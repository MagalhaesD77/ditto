/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.eclipse.ditto.base.service.UriEncoding;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.service.messaging.httppush.RequestSigning;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Signing of HTTP requests and AMQP connection strings with Azure SASL.
 */
public final class AzSaslRequestSigning implements RequestSigning {

    private static final String AUTH_SCHEME = "SharedAccessSignature";

    private static final String AMQP_USERNAME_TRIM_PATTERN = "\\.azure-devices\\.net$";

    private final String sharedKeyName;
    private final ByteString sharedKey;
    private final Duration ttl;
    private final String endpoint;

    private AzSaslRequestSigning(final String sharedKeyName, final ByteString sharedKey,
            final Duration ttl, final String endpoint) {
        this.sharedKeyName = sharedKeyName;
        this.sharedKey = sharedKey;
        this.ttl = ttl;
        this.endpoint = endpoint;
    }

    /**
     * Create the signing algorithm of Azure SASL.
     *
     * @param sharedKeyName name of the shared key.
     * @param sharedKey value of the shared key.
     * @param ttl how long should tokens be valid after creation.
     * @param endpoint value of the {@code sr} field in the signature. Default to the URI.
     * @return The signing algorithm.
     */
    public static AzSaslRequestSigning of(final String sharedKeyName, final String sharedKey,
            final Duration ttl, final String endpoint) {
        try {
            final ByteString sharedKeyBytes = ByteString.fromArray(Base64.getDecoder().decode(sharedKey));
            return new AzSaslRequestSigning(sharedKeyName, sharedKeyBytes, ttl, endpoint);
        } catch (final IllegalArgumentException e) {
            throw MessageSendingFailedException.newBuilder()
                    .message("Failed to initiate Azure SASL signing algorithm.")
                    .description("The shared key is not in Base64 scheme.")
                    .build();
        }
    }

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request, final Instant timestamp) {
        final String token = getSasToken(timestamp);
        final HttpCredentials credentials = HttpCredentials.create(AUTH_SCHEME, token);
        final HttpRequest signedRequest = request.addCredentials(credentials);
        return Source.single(signedRequest);
    }

    /**
     * Create AMQP SASL-plain "username" identifying an SAS token.
     *
     * @return The "username".
     */
    public String getAmqpUsername() {
        return sharedKeyName + "@sas.root." + endpoint.replaceAll(AMQP_USERNAME_TRIM_PATTERN, "");
    }

    /**
     * Create AMQP SASL-plain "password" containing the SAS token.
     *
     * @param timestamp Timestamp at which the token is generated.
     * @return The "password".
     */
    public String getAmqpPassword(final Instant timestamp) {
        return AUTH_SCHEME + " " + getSasToken(timestamp);
    }

    /**
     * Get the SAS token of an Azure resource.
     *
     * @param timestamp Timestamp at which the token is generated.
     * @return The token.
     */
    public String getSasToken(final Instant timestamp) {
        final long expiry = timestamp.plus(ttl).getEpochSecond();
        final String sr = UriEncoding.encodeAllButUnreserved(endpoint);
        final String stringToSign = sr + "\n" + expiry;
        final String signature = UriEncoding.encodeAllButUnreserved(
                Base64.getEncoder().encodeToString(RequestSigning.hmacSha256(sharedKey.toArray(), stringToSign)));
        return assembleToken(sr, signature, expiry, sharedKeyName);
    }

    private static String assembleToken(final String sr, final String signature, final long expiry, final String skn) {
        return String.format("sr=%s&sig=%s&se=%d&skn=%s", sr, signature, expiry, skn);
    }
}

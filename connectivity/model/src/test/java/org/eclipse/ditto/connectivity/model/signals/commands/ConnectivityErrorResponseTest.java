/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.signals.commands;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ConnectivityErrorResponse}.
 */
public final class ConnectivityErrorResponseTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ConnectivityErrorResponse.class)
                .usingGetClass()
                .verify();
    }

}

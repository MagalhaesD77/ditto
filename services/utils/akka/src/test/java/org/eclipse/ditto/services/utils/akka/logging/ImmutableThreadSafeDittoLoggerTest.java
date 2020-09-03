/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.akka.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areEffectivelyImmutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.concurrent.ThreadSafe;

import org.assertj.core.data.MapEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.impl.ObservableMdcAdapter;

/**
 * Unit test for {@link ImmutableThreadSafeDittoLogger}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableThreadSafeDittoLoggerTest {

    private static final String CORRELATION_ID_KEY = CommonMdcEntryKey.CORRELATION_ID.toString();
    private static final String CONNECTION_ID_KEY = "connection-id";

    @Rule
    public final TestName testName = new TestName();

    @Mock
    private Logger plainSlf4jLogger;

    private CapturingMdcAdapterObserver mdcObserver;

    @Before
    public void setUp() {
        mdcObserver = new CapturingMdcAdapterObserver();
        ObservableMdcAdapter.registerObserver(testName.getMethodName(), mdcObserver);
    }

    @After
    public void tearDown() {
        ObservableMdcAdapter.deregisterObserver(testName.getMethodName());
    }

    @Ignore("MutabilityChecker cannot cope with the implementation")
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableThreadSafeDittoLogger.class,
                areEffectivelyImmutable(),
                provided(Logger.class).isAlsoImmutable());
    }

    @Test
    public void getInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableThreadSafeDittoLogger.of(null))
                .withMessage("The logger must not be null!")
                .withNoCause();
    }

    @Test
    public void getNameReturnsExpected() {
        final ImmutableThreadSafeDittoLogger underTest = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);

        assertThat(underTest.getName()).isEqualTo(plainSlf4jLogger.getName());
    }

    @Test
    public void withSameCorrelationIdReturnsSameInstance() {
        final String correlationId = getUsableCorrelationId();
        ImmutableThreadSafeDittoLogger underTest = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        final ImmutableThreadSafeDittoLogger secondLogger = underTest.withCorrelationId(correlationId);

        assertThat(secondLogger).isSameAs(underTest);
    }

    @Test
    public void putCorrelationIdToMdcAndLogInfo() {
        final String correlationId = getUsableCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);
        ImmutableThreadSafeDittoLogger underTest = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger).info(msg);
        assertThat(mdcObserver.allPutEntries)
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId));
        assertThat(mdcObserver.allRemovedKeys)
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY);
    }

    @Test
    public void putNothingToMdcAndDoNotLogAsInfoIsDisabled() {
        final String correlationId = getUsableCorrelationId();
        final String msg = "Foo!";
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(false);
        ImmutableThreadSafeDittoLogger underTest = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger, Mockito.times(0)).info(msg);
        assertThat(mdcObserver.allPutEntries).isEmpty();
        assertThat(mdcObserver.allRemovedKeys).isEmpty();
    }

    @Test
    public void putNothingToMdcButLogInfo() {
        final String correlationId = null;
        final String msg = "Foo!";
        ImmutableThreadSafeDittoLogger underTest = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.info(msg);

        Mockito.verify(plainSlf4jLogger, Mockito.times(0)).isInfoEnabled();
        Mockito.verify(plainSlf4jLogger).info(msg);
        assertThat(mdcObserver.allPutEntries).as("Put MDC entries").isEmpty();
        assertThat(mdcObserver.allRemovedKeys).as("Removed MDC entries").isEmpty();
    }

    @Test
    public void putCorrelationIdToMdcAndLogDebugInfoWarnAndError() {
        final String correlationId = getUsableCorrelationId();
        final String debugMsg = "BugMeNot, {}!";
        final String infoMsg = "Foo!";
        final String warnMsg = "Bar!";
        final String errorMsg = "Baz!";
        final MapEntry<String, String> correlationIdMdcEntry = entry(CORRELATION_ID_KEY, correlationId);
        Mockito.when(plainSlf4jLogger.isDebugEnabled()).thenReturn(true);
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);
        Mockito.when(plainSlf4jLogger.isWarnEnabled()).thenReturn(true);
        Mockito.when(plainSlf4jLogger.isErrorEnabled()).thenReturn(true);

        ImmutableThreadSafeDittoLogger underTest = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        underTest = underTest.withCorrelationId(correlationId);

        underTest.debug(debugMsg, "please");
        underTest.info(infoMsg);
        underTest.warn(warnMsg);
        underTest.error(errorMsg);

        Mockito.verify(plainSlf4jLogger).debug(debugMsg, "please");
        Mockito.verify(plainSlf4jLogger).info(infoMsg);
        Mockito.verify(plainSlf4jLogger).warn(warnMsg);
        Mockito.verify(plainSlf4jLogger).error(errorMsg);
        assertThat(mdcObserver.allPutEntries)
                .as("Put MDC entries")
                .containsExactly(correlationIdMdcEntry, correlationIdMdcEntry, correlationIdMdcEntry,
                        correlationIdMdcEntry);
        assertThat(mdcObserver.allRemovedKeys)
                .as("Removed MDC entries")
                .containsExactly(CORRELATION_ID_KEY, CORRELATION_ID_KEY, CORRELATION_ID_KEY, CORRELATION_ID_KEY);
    }

    @Test
    public void twoThreadsTwoLoggers() {
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);

        final ImmutableThreadSafeDittoLogger initialLogger = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Runnable loggingThread1 = () -> initialLogger.withCorrelationId("logger1-1").info("logger1-1");
        final Runnable loggingThread2 = () -> initialLogger.withCorrelationId("logger2-1").info("logger2-1");
        final CompletableFuture<Void> future1 = CompletableFuture.runAsync(loggingThread2, executorService);
        final CompletableFuture<Void> future2 = CompletableFuture.runAsync(loggingThread1, executorService);
        final CompletableFuture<Void> allLoggingFuture = CompletableFuture.allOf(future1, future2);
        allLoggingFuture.join();

        assertThat(allLoggingFuture).isCompleted();
        assertThat(mdcObserver.allPutEntries)
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, "logger2-1"), entry(CORRELATION_ID_KEY, "logger1-1"));
    }

    @Test
    public void withTwoMdcEntries() {
        Mockito.when(plainSlf4jLogger.isErrorEnabled()).thenReturn(true);
        final ImmutableThreadSafeDittoLogger initialLogger = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        final String correlationId = getUsableCorrelationId();
        final String connectionId = "myConnection";
        final String logMessage = "The connection is closed!";

        final ThreadSafeDittoLogger underTest =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId);

        underTest.error(logMessage);

        Mockito.verify(plainSlf4jLogger).error(logMessage);
        assertThat(mdcObserver.allPutEntries)
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId), entry(CONNECTION_ID_KEY, connectionId));
        assertThat(mdcObserver.allRemovedKeys)
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY, CONNECTION_ID_KEY);
    }

    @Test
    public void removeCorrelationIdViaNullValue() {
        Mockito.when(plainSlf4jLogger.isInfoEnabled()).thenReturn(true);
        final ImmutableThreadSafeDittoLogger initialLogger = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        final String correlationId = getUsableCorrelationId();
        final String connectionId = "myConnection";

        final ThreadSafeDittoLogger withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId);

        withTwoMdcEntries.info("Foo");

        final ThreadSafeDittoLogger withOneMdcEntry =
                withTwoMdcEntries.withMdcEntries(CORRELATION_ID_KEY, null, CONNECTION_ID_KEY, connectionId);

        withOneMdcEntry.info("Bar");

        assertThat(mdcObserver.allPutEntries)
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId),
                        entry(CONNECTION_ID_KEY, connectionId),
                        entry(CONNECTION_ID_KEY, connectionId));
        assertThat(mdcObserver.allRemovedKeys)
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY, CONNECTION_ID_KEY, CORRELATION_ID_KEY, CONNECTION_ID_KEY);
    }

    @Test
    public void removeConnectionIdViaKey() {
        Mockito.when(plainSlf4jLogger.isDebugEnabled()).thenReturn(true);
        final ImmutableThreadSafeDittoLogger initialLogger = ImmutableThreadSafeDittoLogger.of(plainSlf4jLogger);
        final String correlationId = getUsableCorrelationId();
        final String connectionId = "myConnection";

        final ThreadSafeDittoLogger withTwoMdcEntries =
                initialLogger.withMdcEntries(CORRELATION_ID_KEY, correlationId, CONNECTION_ID_KEY, connectionId);

        withTwoMdcEntries.debug("Foo");

        final ThreadSafeDittoLogger withOneMdcEntry = withTwoMdcEntries.removeMdcEntry(CONNECTION_ID_KEY);

        withOneMdcEntry.debug("Bar");

        assertThat(mdcObserver.allPutEntries)
                .as("Put MDC entries")
                .containsOnly(entry(CORRELATION_ID_KEY, correlationId),
                        entry(CONNECTION_ID_KEY, connectionId),
                        entry(CORRELATION_ID_KEY, correlationId));
        assertThat(mdcObserver.allRemovedKeys)
                .as("Removed MDC entries")
                .containsOnly(CORRELATION_ID_KEY, CONNECTION_ID_KEY, CORRELATION_ID_KEY, CONNECTION_ID_KEY);
    }

    private String getUsableCorrelationId() {
        return testName.getMethodName();
    }

    @ThreadSafe
    private static final class CapturingMdcAdapterObserver extends ObservableMdcAdapter.AbstractMdcAdapterObserver {

        private final List<Map.Entry<String, String>> allPutEntries;
        private final List<String> allRemovedKeys;

        private CapturingMdcAdapterObserver() {
            allPutEntries = Collections.synchronizedList(new ArrayList<>());
            allRemovedKeys = Collections.synchronizedList(new ArrayList<>());
        }

        @Override
        public void onPut(final String key, final String value) {
            allPutEntries.add(entry(key, value));
        }

        @Override
        public void onRemove(final String key) {
            allRemovedKeys.add(key);
        }

    }

}
/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;

import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestProbe;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingDeleteModel}.
 */
public final class ThingDeleteModelTest extends AbstractWithActorSystemTest {

    @Test
    public void testHashCodeAndEquals() {
        system = ActorSystem.create();
        final TestProbe probe1 = TestProbe.apply(system);
        final TestProbe probe2 = TestProbe.apply(system);
        EqualsVerifier.forClass(ThingDeleteModel.class)
                .usingGetClass()
                .withPrefabValues(ActorSelection.class, system.actorSelection(probe1.ref().path()),
                        system.actorSelection(probe2.ref().path()))
                .verify();
    }

}

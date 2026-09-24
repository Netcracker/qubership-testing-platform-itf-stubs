/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 *
 */

package org.qubership.automation.itf.trigger.cli.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.UUID;

import org.apache.camel.component.netty.NettyConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.constants.TriggerState;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.trigger.cli.CliConstants;

class CliTriggerTest {

    private CliTrigger trigger;

    @AfterEach
    void tearDown() throws Exception {
        if (trigger != null) {
            trigger.deactivate();
        }
    }

    /**
     * Fails when activating a CLI trigger with no command delimiter cannot resolve its Netty
     * endpoint.
     *
     * <p>Regression test for the Camel 4.18.0 upgrade: the trigger used to request the
     * "textline" codec by appending it as a Netty URI query parameter, and Camel's endpoint
     * resolution rejected it with {@code ResolveEndpointFailedException: ... Unknown
     * parameters=[{textline=true}]}.</p>
     */
    @Test
    void testActivate_DefaultTextlineCodec_Succeeds() throws Exception {
        trigger = new CliTrigger(descriptor("cli-textline"), baseProperties());

        trigger.activate();

        assertEquals(TriggerState.ACTIVE, trigger.getState());
    }

    /**
     * Fails when activating a CLI trigger with a custom command delimiter cannot resolve its
     * Netty endpoint.
     */
    @Test
    void testActivate_CustomCommandDelimiter_Succeeds() throws Exception {
        ConnectionProperties properties = baseProperties();
        properties.put(CliConstants.Inbound.COMMAND_DELIMITER, ";");
        trigger = new CliTrigger(descriptor("cli-delimiter"), properties);

        trigger.activate();

        assertEquals(TriggerState.ACTIVE, trigger.getState());
    }

    @Test
    void testBuildNettyConfiguration_NoDelimiter_UsesTextlineCodec() {
        trigger = new CliTrigger(descriptor("cli-config-textline"), baseProperties());

        NettyConfiguration configuration = trigger.buildNettyConfiguration("TCP", "localhost", "0", null);

        assertEquals("TCP", configuration.getProtocol());
        assertEquals("localhost", configuration.getHost());
        assertEquals(0, configuration.getPort());
        assertTrue(configuration.isTextline());
        assertTrue(configuration.isAllowDefaultCodec());
    }

    @Test
    void testBuildNettyConfiguration_WithDelimiter_UsesCustomCodec() {
        trigger = new CliTrigger(descriptor("cli-config-delimiter"), baseProperties());

        NettyConfiguration configuration = trigger.buildNettyConfiguration("TCP", "localhost", "0", ";");

        assertFalse(configuration.isTextline());
        assertFalse(configuration.isAllowDefaultCodec());
        assertFalse(configuration.isAutoAppendDelimiter());
        assertEquals(1, configuration.getEncodersAsList().size());
        assertEquals(2, configuration.getDecodersAsList().size());
    }

    private static ConnectionProperties baseProperties() {
        ConnectionProperties properties = new ConnectionProperties();
        properties.put(CliConstants.CONNECTION_TYPE, "TCP");
        properties.put(CliConstants.REMOTE_IP, "localhost");
        properties.put(CliConstants.REMOTE_PORT, "0");
        return properties;
    }

    private static StorableDescriptor descriptor(String name) {
        StorableDescriptor descriptor = mock(StorableDescriptor.class);
        when(descriptor.getProjectUuid()).thenReturn(UUID.randomUUID());
        when(descriptor.getProjectId()).thenReturn(BigInteger.ONE);
        when(descriptor.getId()).thenReturn(UUID.randomUUID().toString());
        when(descriptor.getName()).thenReturn(name);
        return descriptor;
    }
}

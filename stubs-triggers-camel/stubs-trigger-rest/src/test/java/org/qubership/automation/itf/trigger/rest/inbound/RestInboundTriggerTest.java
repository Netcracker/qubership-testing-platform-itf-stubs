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

package org.qubership.automation.itf.trigger.rest.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Collections;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;

import jakarta.servlet.http.HttpServletRequest;

class RestInboundTriggerTest {

    /**
     * Fails when addClientAddressInHeader cannot read the inbound HttpServletRequest.
     *
     * <p>Regression test for the Camel upgrade from 2.20.4: the method used to read the request
     * from a {@code "CamelHttpServletRequest"} header that {@link HttpMessage} set on construction.
     * Current Camel versions store the request only as a plain field on {@link HttpMessage},
     * exposed through {@link HttpMessage#getRequest()}, so the header was always {@code null} and
     * the client-address headers ("client", "remoteAddr", "remoteHost", "remotePort", "protocol")
     * were silently never added.</p>
     */
    @Test
    void testAddClientAddressInHeader_PopulatesClientCoordsFromRequest() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("192.168.1.10");
        when(servletRequest.getRemoteHost()).thenReturn("client-host");
        when(servletRequest.getRemotePort()).thenReturn(54321);
        when(servletRequest.getProtocol()).thenReturn("HTTP/1.1");
        when(servletRequest.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        HttpCommonEndpoint endpoint = mock(HttpCommonEndpoint.class);
        when(endpoint.getHttpBinding()).thenReturn(mock(HttpBinding.class));

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setIn(new HttpMessage(exchange, endpoint, servletRequest, null));

        RestInboundTrigger trigger = new RestInboundTrigger(descriptor(), new ConnectionProperties());
        trigger.addClientAddressInHeader(exchange);

        assertEquals("192.168.1.10", exchange.getIn().getHeader("client"));
        assertEquals("192.168.1.10", exchange.getIn().getHeader("remoteAddr"));
        assertEquals("client-host", exchange.getIn().getHeader("remoteHost"));
        assertEquals(54321, exchange.getIn().getHeader("remotePort"));
        assertEquals("HTTP/1.1", exchange.getIn().getHeader("protocol"));
    }

    private static StorableDescriptor descriptor() {
        StorableDescriptor descriptor = mock(StorableDescriptor.class);
        when(descriptor.getProjectUuid()).thenReturn(UUID.randomUUID());
        when(descriptor.getProjectId()).thenReturn(BigInteger.ONE);
        when(descriptor.getId()).thenReturn(UUID.randomUUID().toString());
        return descriptor;
    }
}

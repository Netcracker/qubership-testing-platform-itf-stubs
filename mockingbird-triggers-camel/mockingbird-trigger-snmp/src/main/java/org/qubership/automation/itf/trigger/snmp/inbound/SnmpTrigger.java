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

package org.qubership.automation.itf.trigger.snmp.inbound;

import static org.qubership.automation.itf.trigger.snmp.SnmpTransportConstants.HOST;
import static org.qubership.automation.itf.trigger.snmp.SnmpTransportConstants.PORT;
import static org.qubership.automation.itf.trigger.snmp.SnmpTransportConstants.SNMP_COMPONENT;
import static org.qubership.automation.itf.trigger.snmp.SnmpTransportConstants.SNMP_VERSION;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.snmp.SnmpActionType;
import org.apache.camel.component.snmp.SnmpComponent;
import org.apache.camel.component.snmp.SnmpEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.exception.TriggerException;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.inbound.AbstractCamelTrigger;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnmpTrigger extends AbstractCamelTrigger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnmpTrigger.class);
    private static final String SNMP_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.snmp.inbound.SNMPInboundTransport";
    private CamelContext camelContext;

    public SnmpTrigger(StorableDescriptor triggerConfigurationDescriptor, ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new ItfAbstractRouteBuilder() {
            @Override
            public void configure() throws Exception {
                UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
                SnmpEndpoint endpoint = new SnmpEndpoint(resolveEndpoint(),
                        (SnmpComponent) camelContext.getComponent(SNMP_COMPONENT));
                endpoint.setAddress(resolveEndpoint());
                endpoint.setType(SnmpActionType.TRAP);
                from(endpoint)
                    .process(exchange -> {
                        String sessionId = UUID.randomUUID().toString();
                        MetricsAggregateService.putCommonMetrics(projectUuid,sessionId);
                        LOGGER.info("Project: {}. SessionId: {}. Request is received by endpoint: {}",
                                projectUuid, sessionId, endpoint);
                        startSession(exchange, SNMP_INBOUND_TRANSPORT_CLASS_NAME,
                            getConnectionProperties(), getTriggerConfigurationDescriptor(), sessionId);
                        MetricsAggregateService.incrementIncomingRequestToProject(
                                projectUuid, TransportType.SNMP_INBOUND, true);
                    }).routeId(getId())
                        .routeDescription(projectUuid.toString())
                        .group(TransportType.SNMP_INBOUND.name());
            }

            @Override
            public Map<String, Object> getAdditionalProperties(Exchange exchange) {
                return null;
            }

            @Override
            public List<String> getExcludeHeadersList() {
                return null;
            }
        });
        camelContext.start();
        LOGGER.info("{} is activated successfully", camelContext.toString());
    }

    @Override
    protected void deactivateSpecificTrigger() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
            LOGGER.info("{} is deactivated successfully", camelContext.toString());
        }
    }

    @Override
    protected void applyTriggerProperties(ConnectionProperties connectionProperties) throws TriggerException {
        setConnectionProperties(connectionProperties);
    }

    private String resolveEndpoint() {
        return "snmp:" + getConnectionProperties().get(HOST) + ":" + getConnectionProperties().get(PORT)
                + "?protocol=udp&type=TRAP&snmpVersion=" + getConnectionProperties().get(SNMP_VERSION);
    }

}

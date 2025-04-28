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

package org.qubership.automation.itf.trigger.smpp.inbound;

import java.util.UUID;

import org.qubership.automation.itf.communication.TriggerExecutionMessageSender;
import org.qubership.automation.itf.core.model.communication.message.CommonTriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.transport.service.LockProvider;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.Helper;
import org.qubership.automation.itf.trigger.camel.inbound.AbstractCamelTrigger;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.smpp.SmppServerSimulator;

public class SmppTrigger extends AbstractCamelTrigger {
    private SmppServerSimulator smppServerSimulator;

    private static final String SMPP_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.smpp.inbound.SmppInboundTransport";

    public SmppTrigger(StorableDescriptor triggerConfigurationDescriptor,
                          ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        smppServerSimulator = new SmppServerSimulator(this.getConnectionProperties(),this);
    }

    @Override
    protected void deactivateSpecificTrigger() {
        if (smppServerSimulator != null) {
            smppServerSimulator.stop();
        }
    }

    @Override
    protected void applyTriggerProperties(ConnectionProperties connectionProperties) {
        setConnectionProperties(connectionProperties);
    }

    /**
     * Prepare, send message to itf-executor; wait and get a response. Return prepared response.
     */
    public Message produceMessageToItf(Message requestMessage) throws InterruptedException {
        String sessionId = UUID.randomUUID().toString();
        MetricsAggregateService.putCommonMetrics(getTriggerConfigurationDescriptor().getProjectUuid(), sessionId);
        String brokerMessageSelectorValue = Helper.getBrokerMessageSelectorValue();
        ItfAbstractRouteBuilder.logExtendedInfo(getTriggerConfigurationDescriptor().getProjectUuid(),
                sessionId,
                brokerMessageSelectorValue,
                SMPP_INBOUND_TRANSPORT_CLASS_NAME,
                requestMessage.getText() == null ? 0 : requestMessage.getText().length());
        TriggerExecutionMessageSender.send(new CommonTriggerExecutionMessage(
                SMPP_INBOUND_TRANSPORT_CLASS_NAME, requestMessage,
                getTriggerConfigurationDescriptor(), sessionId,
                brokerMessageSelectorValue), getTriggerConfigurationDescriptor().getProjectUuid());
        LOGGER.debug("Project: {}, SessionId: {}, Broker Message Selector Value: {}, transport: '{}' "
                        + "- message to executor is sent.",
                getTriggerConfigurationDescriptor().getProjectUuid(), sessionId, brokerMessageSelectorValue,
                SMPP_INBOUND_TRANSPORT_CLASS_NAME);
        Message message = LockProvider.INSTANCE.waitResponse(sessionId,
                Helper.getLockProviderCheckInterval(),
                Helper.getLockProviderCheckMaxInterval(),
                Helper.getLockProviderCheckMultiplier());
        LOGGER.info("Project: {}. SessionId: {}. Response is sent",
                getTriggerConfigurationDescriptor().getProjectUuid(), sessionId);
        return (message == null) ? new Message("Null response message") : message;
    }
}

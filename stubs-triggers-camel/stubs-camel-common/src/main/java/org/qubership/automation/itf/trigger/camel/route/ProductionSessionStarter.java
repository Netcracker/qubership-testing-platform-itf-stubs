package org.qubership.automation.itf.trigger.camel.route;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.qubership.automation.itf.communication.TriggerExecutionMessageSender;
import org.qubership.automation.itf.core.model.communication.message.CommonTriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.trigger.camel.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;

public class ProductionSessionStarter implements SessionStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionSessionStarter.class);
    private static final String EXCHANGE_PATTERN_JNDI_PROPERTY = "exchangePattern";

    /**
     * Start session of interaction between stubs and executor via ActiveMq queues.
     *
     * @param exchange - Camel exchange,
     * @param transportClassName - transport class name,
     * @param transportConfig - transport configuration,
     * @param triggerConfig - trigger  configuration,
     * @param sessionId - session id for logging purposes,
     * @param message - incoming message,
     */
    @Override
    public void startSession(@Nonnull Exchange exchange,
                             @Nonnull String transportClassName,
                             @Nonnull ConnectionProperties transportConfig,
                             @Nonnull StorableDescriptor triggerConfig,
                             @Nonnull String sessionId,
                             @Nonnull Message message) {
        if (exchange.getPattern() == ExchangePattern.InOut) {
            Map<String, String> addJndiProps = transportConfig.obtain("addJndiProps");
            if (addJndiProps != null && addJndiProps.containsKey(EXCHANGE_PATTERN_JNDI_PROPERTY)) {
                String exchangePattern = addJndiProps.get(EXCHANGE_PATTERN_JNDI_PROPERTY);
                if (exchangePattern != null && exchangePattern.equalsIgnoreCase(ExchangePattern.InOnly.toString())) {
                    exchange.setPattern(ExchangePattern.InOnly);
                }
            }
        }
        String brokerMessageSelectorValue = Helper.getBrokerMessageSelectorValue();
        TriggerExecutionMessageSender.send(
                new CommonTriggerExecutionMessage(
                        transportClassName, message, triggerConfig, sessionId, brokerMessageSelectorValue
                ), triggerConfig.getProjectUuid()
        );
        LOGGER.debug("Project: {}, SessionId: {}, Broker Message Selector Value: {}, transport: '{}' - message to "
                        + "executor is sent.", triggerConfig.getProjectUuid(), sessionId, brokerMessageSelectorValue,
                transportClassName);
    }

}

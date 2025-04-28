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

package org.qubership.automation.itf.trigger.camel.route;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.StringSource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileBinding;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.communication.TriggerExecutionMessageSender;
import org.qubership.automation.itf.core.model.communication.message.CommonTriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ItfAbstractRouteBuilder extends RouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItfAbstractRouteBuilder.class);
    private static final String EXCHANGE_PATTERN_JNDI_PROPERTY = "exchangePattern";
    private static final int MAX_SIZE = Config.getConfig().getIntOrDefault(
            "logging.incoming.request.message.max.size",5242880);

    /**
     * Create and fill Message (String body and headers) from Camel message received.
     * Generate sessionId as randomUUID().
     * Send event with Message created and sessionId into atp-itf-executor via queue.
     * Return sessionId.
     */
    public void startSession(@Nonnull Exchange exchange,
                               @Nonnull String transportClassName,
                               @Nonnull ConnectionProperties transportConfig,
                               @Nonnull StorableDescriptor triggerConfig,
                               @Nonnull String sessionId) throws Exception {
        Message message = prepareIncomingMessage(exchange, transportClassName, transportConfig, triggerConfig,
                sessionId);
        startSession(exchange, transportClassName, transportConfig, triggerConfig, sessionId, message);
    }

    /**
     * Start session of interaction between stubs and executor via ActiveMq queues.
     *
     * @param exchange - Camel exchange,
     * @param transportClassName - transport class name,
     * @param transportConfig - transport configuration,
     * @param triggerConfig - trigger  configuration,
     * @param sessionId - session id for logging purposes,
     * @param message - incoming message,
     * @throws Exception in case exceptions while sending message to executor queue.
     */
    public void startSession(@Nonnull Exchange exchange,
                             @Nonnull String transportClassName,
                             @Nonnull ConnectionProperties transportConfig,
                             @Nonnull StorableDescriptor triggerConfig,
                             @Nonnull String sessionId,
                             @Nonnull Message message) throws Exception {
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

    /**
     * Prepare Itf message from Camel incoming message according its body type.
     *
     * @param exchange - Camel exchange,
     * @param transportClassName - transport class name,
     * @param transportConfig - transport configuration,
     * @param triggerConfig - trigger  configuration,
     * @param sessionId - session id for logging purposes,
     * @return prepared Itf incoming message for Camel incoming message,
     * @throws Exception in case message body processing exceptions.
     */
    public Message prepareIncomingMessage(@Nonnull Exchange exchange,
                                          @Nonnull String transportClassName,
                                          @Nonnull ConnectionProperties transportConfig,
                                          @Nonnull StorableDescriptor triggerConfig,
                                          @Nonnull String sessionId) throws Exception {
        org.apache.camel.Message input = exchange.getIn();
        Object messageBody = input.getBody();
        Message message;
        long bodyLength = -1L;
        if (messageBody instanceof CxfPayload) {
            message = new Message(((StringSource) ((CxfPayload) messageBody).getBodySources().get(0)).getText());
        } else if (messageBody instanceof InputStream) {
            /*  Should be revised. May be charset replacement could be configured in properties file?
                Algorithm:
                    - If there is no CamelHttpCharacterEncoding header ==> use UTF-8 charset
                    - Otherwise, if CamelHttpCharacterEncoding=Shift_JIS, ==> use MS932 charset
                    - Otherwise ==> use charset from the header
             */
            String charset = JvmSettings.CHARSET_NAME;
            if (input.getHeaders() != null && input.getHeaders().containsKey("CamelHttpCharacterEncoding")) {
                Object encodingHeader = input.getHeader("CamelHttpCharacterEncoding");
                if (encodingHeader.equals("Shift_JIS")) {
                    charset = "MS932";
                } else {
                    charset = encodingHeader.toString();
                }
            }
            message = new Message(IOUtils.toString((InputStream) messageBody, charset));
        } else if (messageBody instanceof RemoteFile) {
            RemoteFile remoteFile = (RemoteFile) messageBody;
            GenericFileBinding gfb = remoteFile.getBinding();
            GenericFile gf = new GenericFile();
            ByteArrayOutputStream ba = (ByteArrayOutputStream) gfb.getBody(gf);
            message = new Message(ba.toString(JvmSettings.CHARSET_NAME));
        } else if (messageBody instanceof GenericFile) {
            message = new Message((File) ((GenericFile) messageBody).getFile());
            bodyLength = ((GenericFile) messageBody).getFileLength();
        } else if (messageBody instanceof byte[]) {
            message = new Message(new String((byte[]) messageBody, StandardCharsets.UTF_8));
        } else {
            message = new Message((String) messageBody);
        }
        message.convertAndSetHeaders(input.getHeaders(), getExcludeHeadersList());
        if (bodyLength == -1L) {
            bodyLength = message.getText() == null ? 0 : message.getText().length();
        }
        MetricsAggregateService.checkIncomingMessageSize(triggerConfig.getProjectUuid(), bodyLength);
        logExtendedInfo(triggerConfig.getProjectUuid(), sessionId, Helper.getBrokerMessageSelectorValue(),
                transportClassName, bodyLength);
        message.getConnectionProperties().putAll(transportConfig);
        message.getConnectionProperties().putAll(getAdditionalProperties(exchange));
        return message;
    }

    /**
     * Log message info (including size, if it's above threshold).
     *
     * @param projectUuid - project Uuid,
     * @param sessionId - session id for logging purposes,
     * @param brokerMessageSelectorValue - message selector value for broker,
     * @param transportClassName - transport class name,
     * @param length - length of incoming message body.
     */
    public static void logExtendedInfo(UUID projectUuid,
                                String sessionId,
                                String brokerMessageSelectorValue,
                                String transportClassName,
                                long length) {
        LOGGER.debug("Project: {}, SessionId: {}, "
                        + (length >= MAX_SIZE ? String.format("Message size: %s (bytes), ", length) : StringUtils.EMPTY)
                        + "Broker Message Selector Value: {}, transport: '{}' - sending message to executor...",
                projectUuid, sessionId, brokerMessageSelectorValue, transportClassName);
    }

    public abstract Map<String, Object> getAdditionalProperties(Exchange exchange);

    public abstract List<String> getExcludeHeadersList();
}

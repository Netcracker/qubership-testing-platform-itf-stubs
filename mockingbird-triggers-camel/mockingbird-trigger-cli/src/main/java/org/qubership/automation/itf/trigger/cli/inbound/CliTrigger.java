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

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.LoggingErrorHandlerBuilder;
import org.apache.camel.component.netty4.ChannelHandlerFactories;
import org.apache.camel.component.netty4.ChannelHandlerFactory;
import org.apache.camel.component.netty4.NettyComponent;
import org.apache.camel.component.netty4.NettyConfiguration;
import org.apache.camel.component.netty4.NettyConsumer;
import org.apache.camel.component.netty4.NettyEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.support.ServiceSupport;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.communication.TriggerExecutionMessageSender;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.communication.message.CommonTriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.transport.service.LockProvider;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.Helper;
import org.qubership.automation.itf.trigger.camel.inbound.AbstractCamelTrigger;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.cli.CliConstants;
import org.qubership.automation.itf.trigger.cli.CliServerInitializerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class CliTrigger extends AbstractCamelTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(CliTrigger.class);
    private static final String CLI_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.cli.inbound.CLIInboundTransport";
    private static final Map<UUID, Map<String, CamelContext>> camelContexts = new ConcurrentHashMap<>();

    public CliTrigger(StorableDescriptor triggerConfigurationDescriptor, ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        CamelContext context = getCamelContext();
        context.addRoutes(new ItfAbstractRouteBuilder() {
            @Override
            public void configure() throws Exception {
                NettyComponent nettyComponent = new NettyComponent(context);
                context.addComponent(getId(), nettyComponent);
                if ("$runningHostname".equals(getConnectionProperties().obtain(CliConstants.REMOTE_IP))) {
                    getConnectionProperties().replace(CliConstants.REMOTE_IP, Config.getConfig().getRunningHostname());
                }

                String cmdDelimiter = getConnectionProperties().obtain(CliConstants.Inbound.COMMAND_DELIMITER);
                String params = "?textline=true";
                if (StringUtils.isNotBlank(cmdDelimiter) && !"\n".equals(cmdDelimiter)) {
                    SimpleRegistry registry = new SimpleRegistry();
                    ((DefaultCamelContext) context).setRegistry(registry);
                    registry.put("tcpStringEncoder", new StringEncoder());
                    registry.put("tcpStringDecoder", new StringDecoder());
                    registry.put("tcpDelimiterFrameDecoder", getDelimiterFrameDecoder(cmdDelimiter));
                    params = "?allowDefaultCodec=false&decoders=#tcpDelimiterFrameDecoder,#tcpStringDecoder"
                            + "&encoders=#tcpStringEncoder&autoAppendDelimiter=false";
                }

                Endpoint endpoint = nettyComponent.createEndpoint(getId()
                    + ':' + getConnectionProperties().obtain(CliConstants.CONNECTION_TYPE)
                    + "://" + getConnectionProperties().obtain(CliConstants.REMOTE_IP)
                    + ':' + getConnectionProperties().obtain(CliConstants.REMOTE_PORT)
                    + params);
                String endpointString = endpoint.toString();

                if (!StringUtils.isBlank(getConnectionProperties().obtain(CliConstants.Inbound.GREETING))) {
                    NettyConfiguration configuration = ((NettyEndpoint) endpoint).getConfiguration();
                    NettyConsumer consumer = (NettyConsumer) endpoint.createConsumer(null);
                    CliServerInitializerFactory serverInitializerFactory = new CliServerInitializerFactory(consumer,
                            getConnectionProperties());
                    configuration.setServerInitializerFactory(serverInitializerFactory);
                }
                boolean isAllowedEmpty = "Yes".equals(getConnectionProperties()
                        .getOrDefault(CliConstants.Inbound.ALLOWED_EMPTY, "No"));
                final CliMessageBuilder messageBuilder = new CliMessageBuilder(
                    cmdDelimiter == null || cmdDelimiter.equals("\n") || cmdDelimiter.equals(".")
                            ? StringUtils.EMPTY : cmdDelimiter, isAllowedEmpty);
                UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
                String brokerMessageSelectorValue = Helper.getBrokerMessageSelectorValue();
                from(endpoint).routeId(getId())
                    .process(exchange -> {
                        String sessionId = UUID.randomUUID().toString();
                        MetricsAggregateService.putCommonMetrics(projectUuid, sessionId);
                        LOGGER.info("Project: {}. SessionId: {}. Request is received by endpoint: {}",
                            projectUuid, sessionId, endpoint);
                        String body = exchange.getIn().getBody(String.class);
                        MetricsAggregateService.checkIncomingMessageSize(projectUuid, body);
                        Message requestMessage = composeRequestMessage(body, messageBuilder);
                        if (requestMessage != null) {
                            addRequestHeaders(exchange.getIn(), requestMessage);
                            ItfAbstractRouteBuilder.logExtendedInfo(projectUuid, sessionId, brokerMessageSelectorValue,
                                    CLI_INBOUND_TRANSPORT_CLASS_NAME, body == null
                                            ? 0 : body.getBytes(JvmSettings.CHARSET).length);
                            TriggerExecutionMessageSender
                                    .send(new CommonTriggerExecutionMessage(CLI_INBOUND_TRANSPORT_CLASS_NAME,
                                                    requestMessage, getTriggerConfigurationDescriptor(),
                                                    sessionId, brokerMessageSelectorValue),
                                            getTriggerConfigurationDescriptor().getProjectUuid());
                            LOGGER.debug("Project: {}, SessionId: {}, transport: '{}'"
                                            + " - message to executor is sent.", projectUuid, sessionId,
                                    CLI_INBOUND_TRANSPORT_CLASS_NAME);
                            setUpOut(exchange, projectUuid, sessionId);
                            LOGGER.info("Project: {}. SessionId: {}. Response is sent from endpoint: {}",
                                    projectUuid, sessionId, endpointString);
                            MetricsAggregateService
                                    .recordIncomingRequestDuration(projectUuid,
                                            TransportType.CLI_INBOUND,
                                            endpointString,
                                            Duration.between(exchange.getCreated().toInstant(), OffsetDateTime.now()));
                        }
                    });
            }

            private Message composeRequestMessage(String body, CliMessageBuilder messageBuilder) {
                if (messageBuilder.isAllowedEmpty() || !StringUtils.isEmpty(body)) {
                    return new Message(body);
                }
                return null;
            }

            private void addRequestHeaders(org.apache.camel.Message inMessage, Message requestMessage) {
                if (inMessage.getHeaders() != null
                        && inMessage.getHeaders().containsKey("CamelNettyRemoteAddress")) {
                    InetSocketAddress address = (InetSocketAddress) inMessage.getHeader("CamelNettyRemoteAddress");
                    if (address != null) {
                        String remoteHost = address.getHostString();
                        int remotePort = address.getPort();
                        requestMessage.getHeaders().put("remoteIp", remoteHost);
                        requestMessage.getHeaders().put("port", remotePort);
                    }
                }
            }

            @Override
            public Map<String, Object> getAdditionalProperties(Exchange exchange) {
                return new HashMap<>(); // There are currently no additional connection properties from the exchange
            }

            @Override
            public List<String> getExcludeHeadersList() {
                return null;
            }
        });
    }

    @Override
    protected void deactivateSpecificTrigger() throws Exception {
        UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
        if (Objects.nonNull(camelContexts.get(projectUuid))) {
            CamelContext context = camelContexts.get(projectUuid).get(getId());
            if (Objects.nonNull(context)) {
                deactivateTrigger(context);
                camelContexts.get(projectUuid).remove(getId());
            } else {
                deactivateTrigger(CAMEL_CONTEXT);
            }
        } else {
            deactivateTrigger(CAMEL_CONTEXT);
        }
    }

    private void deactivateTrigger(CamelContext context) throws Exception {
        if (Objects.nonNull(context.hasComponent(getId()))) {
            context.stopRoute(getId());
            context.removeRoute(getId());
            context.removeComponent(getId());
        }
    }

    @Override
    protected void applyTriggerProperties(ConnectionProperties connectionProperties) {
        setConnectionProperties(connectionProperties);
    }

    private CamelContext getCamelContext() {
        String cmdDelimiter = getConnectionProperties().obtain(CliConstants.Inbound.COMMAND_DELIMITER);
        if (StringUtils.isNotBlank(cmdDelimiter) && !"\n".equals(cmdDelimiter)) {
            CamelContext camelContext = new DefaultCamelContext();
            camelContext.createProducerTemplate();
            UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
            startContext(camelContext);
            Map<String, CamelContext> contextList = camelContexts.getOrDefault(projectUuid, new ConcurrentHashMap<>());
            contextList.put(getId(), camelContext);
            camelContexts.put(projectUuid, contextList);
            return camelContexts.get(projectUuid).get(getId());
        }
        return CAMEL_CONTEXT;
    }

    private void startContext(CamelContext context) {
        if (!((ServiceSupport) context).isStarted()) {
            try {
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (context) {
                    if (!((ServiceSupport) context).isStarted()) {
                        LoggingErrorHandlerBuilder errorHandlerBuilder = new LoggingErrorHandlerBuilder(LOGGER);
                        context.setErrorHandlerBuilder(errorHandlerBuilder);
                        context.start();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed starting of CamelContext", e);
            }
        }
    }

    private ChannelHandlerFactory getDelimiterFrameDecoder(String cmdDelimiter) {
        ByteBuf[] delimiters = new ByteBuf[]{Unpooled.copiedBuffer(cmdDelimiter.getBytes(JvmSettings.CHARSET))};
        return ChannelHandlerFactories.newDelimiterBasedFrameDecoder(1024, delimiters, "tcp");
    }

    /*  We need to add "\r" to the end of response body in order
     *   to place cursor into 1st left position of the next row in the window
     */
    protected void setUpOut(Exchange exchange, UUID projectUuid, String sessionId) throws InterruptedException {
        Message message = LockProvider.INSTANCE.waitResponse(sessionId,
                Helper.getLockProviderCheckInterval(),
                Helper.getLockProviderCheckMaxInterval(),
                Helper.getLockProviderCheckMultiplier());
        LOGGER.debug("Project {}, SessionId {}. Response is got from SessionHandler.", projectUuid, sessionId);
        if (message != null) {
            buildResponse(exchange, message.getText() /*+ "\n\r"*/);
            MetricsAggregateService
                    .incrementIncomingRequestToProject(projectUuid, TransportType.CLI_INBOUND, true);
        } else {
            buildResponse(exchange, "Null response; see logs for errors (sessionId: " + sessionId /*+ ")\n\r"*/);
            MetricsAggregateService
                    .incrementIncomingRequestToProject(projectUuid, TransportType.CLI_INBOUND, false);
        }
        LOGGER.debug("Project {}, SessionId {}. Response is built.", projectUuid, sessionId);
    }

    private void buildResponse(Exchange exchange, String messageText) {
        exchange.getOut().setBody(messageText);
    }
}

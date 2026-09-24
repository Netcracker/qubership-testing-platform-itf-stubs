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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.component.netty.ChannelHandlerFactories;
import org.apache.camel.component.netty.ChannelHandlerFactory;
import org.apache.camel.component.netty.NettyComponent;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.component.netty.NettyEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelLogger;
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

                String connectionType = getConnectionProperties().obtain(CliConstants.CONNECTION_TYPE);
                String remoteIp = getConnectionProperties().obtain(CliConstants.REMOTE_IP);
                int remotePort = toPort(getConnectionProperties().obtain(CliConstants.REMOTE_PORT));
                String cmdDelimiter = getConnectionProperties().obtain(CliConstants.Inbound.COMMAND_DELIMITER);

                NettyConfiguration configuration = buildNettyConfiguration(
                        connectionType, remoteIp, remotePort, cmdDelimiter);

                NettyEndpoint endpoint = new NettyEndpoint(
                        getId() + ':' + connectionType + "://" + remoteIp + ':' + remotePort,
                        nettyComponent, configuration);
                endpoint.setCamelContext(context);
                String endpointString = endpoint.toString();

                if (!StringUtils.isBlank(getConnectionProperties().obtain(CliConstants.Inbound.GREETING))) {
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
                from(endpoint)
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
                                            Duration.between(Instant.ofEpochMilli(exchange.getCreated()), OffsetDateTime.now()));
                        }
                    }).routeId(getId())
                        .routeDescription(projectUuid.toString())
                        .group(TransportType.CLI_INBOUND.name());
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
            context.getRouteController().stopRoute(getId());
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
        if (!context.isStarted()) {
            try {
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (context) {
                    if (!context.isStarted()) {
                        DefaultErrorHandlerBuilder defaultErrorHandlerBuilder = new DefaultErrorHandlerBuilder();
                        CamelLogger camelLogger = new CamelLogger(LOGGER);
                        defaultErrorHandlerBuilder.setLoggerBean(camelLogger);
                        context.getCamelContextExtension().setErrorHandlerFactory(defaultErrorHandlerBuilder);
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

    /**
     * Reads the {@code remote_port} connection property as an {@code int}.
     *
     * <p>{@code CLIInboundTransport} declares this property as an {@link Integer}, but
     * {@link ConnectionProperties#obtain} casts to whatever type the caller assigns it to, so
     * reading it as a {@code String} throws {@code ClassCastException} at runtime instead of
     * failing to compile.</p>
     */
    static int toPort(Object remotePort) {
        return remotePort instanceof Number
                ? ((Number) remotePort).intValue()
                : Integer.parseInt(remotePort.toString());
    }

    /**
     * Builds the Netty endpoint configuration for the given connection settings.
     *
     * <p>Every field is set through {@link NettyConfiguration}'s own setters rather than through
     * a query-string URI Camel would otherwise parse and bind by property name.</p>
     */
    NettyConfiguration buildNettyConfiguration(String connectionType, String remoteIp, int remotePort,
                                                String cmdDelimiter) {
        NettyConfiguration configuration = new NettyConfiguration();
        configuration.setProtocol(connectionType);
        configuration.setHost(remoteIp);
        configuration.setPort(remotePort);
        if (StringUtils.isNotBlank(cmdDelimiter) && !"\n".equals(cmdDelimiter)) {
            configuration.setAllowDefaultCodec(false);
            configuration.setEncodersAsList(List.of(new StringEncoder()));
            configuration.setDecodersAsList(List.of(getDelimiterFrameDecoder(cmdDelimiter), new StringDecoder()));
            configuration.setAutoAppendDelimiter(false);
        } else {
            configuration.setTextline(true);
        }
        configuration.validateConfiguration();
        return configuration;
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

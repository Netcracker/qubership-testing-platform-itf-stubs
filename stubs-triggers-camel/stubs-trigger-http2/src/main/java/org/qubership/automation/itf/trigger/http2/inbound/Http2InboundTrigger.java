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

package org.qubership.automation.itf.trigger.http2.inbound;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.communication.TriggerExecutionMessageSender;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.communication.message.CommonTriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.config.ApplicationConfig;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.transport.service.LockProvider;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.AbstractTriggerImpl;
import org.qubership.automation.itf.trigger.camel.Helper;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.http2.Http2Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

public class Http2InboundTrigger extends AbstractTriggerImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(Http2InboundTrigger.class);
    private static final String HTTP2_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.http2.inbound.HTTP2InboundTransport";
    private CamelContext context;
    private Undertow undertow;

    public Http2InboundTrigger(StorableDescriptor triggerConfigurationDescriptor,
                               ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
            if ("$runningHostname".equals(getConnectionProperties().obtain(Http2Constants.REMOTE_HOST))) {
                getConnectionProperties().replace(Http2Constants.REMOTE_HOST, Config.getConfig().getRunningHostname());
            }
            int port = Integer.parseInt(getConnectionProperties().get(Http2Constants.REMOTE_PORT).toString());
            String host = getConnectionProperties().get(Http2Constants.REMOTE_HOST).toString();
            String configuredEndpoint = getConnectionProperties().get(Http2Constants.ENDPOINT).toString();
            UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
            String brokerMessageSelectorValue = Helper.getBrokerMessageSelectorValue();

            boolean addProjectUuidEndpointPrefix = "Yes".equals(getConnectionProperties()
                    .getOrDefault(Http2Constants.ADD_PROJECTUUID_ENDPOINT_PREFIX, "Yes"));
            String projectUuidPrefix = addProjectUuidEndpointPrefix ? getPrefixWithProjectUuid() : StringUtils.EMPTY;

            int ioThreads = Integer.parseInt(ApplicationConfig.env
                    .getProperty("server.undertow.threads.io.transport.http2","2"));
            int workerThreads = Integer.parseInt(ApplicationConfig.env
                    .getProperty("server.undertow.threads.worker.transport.http2","16"));

            undertow = Undertow.builder()
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .addHttpListener(port, host)
                    .setIoThreads(ioThreads)
                    .setWorkerThreads(workerThreads)
                .setHandler(Handlers.path()
                        .addPrefixPath(projectUuidPrefix + configuredEndpoint,
                            exchange -> exchange.getRequestReceiver().receiveFullBytes((innerExchange, message) -> {
                                String sessionId = UUID.randomUUID().toString();
                                String body = new String(message, StandardCharsets. UTF_8);
                                MetricsAggregateService.putCommonMetrics(projectUuid, sessionId);
                                LOGGER.info("Project: {}. SessionId: {}. Request is received by endpoint: {}",
                                        projectUuid, sessionId, configuredEndpoint);
                                OffsetDateTime started = OffsetDateTime.now();
                                MetricsAggregateService.checkIncomingMessageSize(projectUuid, body);
                                Message msg = new Message(body);
                                HeaderMap headerMap = innerExchange.getRequestHeaders();
                                for (HttpString name : headerMap.getHeaderNames()) {
                                    HeaderValues values = headerMap.get(name);
                                    msg.getHeaders().put(name.toString(), values == null || values.isEmpty()
                                            ? StringUtils.EMPTY : (values.size() == 1)
                                            ? values.get(0) : values.toString());
                                }
                                msg.getHeaders().put("protocol", innerExchange.getProtocol().toString());
                                msg.getHeaders().put("CamelHttpMethod", innerExchange.getRequestMethod().toString());
                                msg.getHeaders().put("CamelHttpUri", innerExchange.getRequestURI());
                                msg.getHeaders().put("CamelHttpQuery", innerExchange.getQueryString());
                                msg.getConnectionProperties().putAll(getConnectionProperties());
                                try {
                                    ItfAbstractRouteBuilder.logExtendedInfo(projectUuid, sessionId,
                                            brokerMessageSelectorValue, HTTP2_INBOUND_TRANSPORT_CLASS_NAME,
                                            body.getBytes(JvmSettings.CHARSET).length);
                                    TriggerExecutionMessageSender.send(
                                        new CommonTriggerExecutionMessage(
                                            HTTP2_INBOUND_TRANSPORT_CLASS_NAME, msg,
                                            getTriggerConfigurationDescriptor(), sessionId,
                                            brokerMessageSelectorValue),
                                            getTriggerConfigurationDescriptor().getProjectUuid()
                                    );
                                    LOGGER.debug("Project: {}, SessionId: {}, Broker Message Selector Value: {}, "
                                                    + "transport: '{}' - message to executor is sent.",
                                            projectUuid, sessionId, brokerMessageSelectorValue,
                                            HTTP2_INBOUND_TRANSPORT_CLASS_NAME);
                                    Message response = setUpOut(innerExchange, projectUuid, sessionId);
                                    if (response != null) {
                                        innerExchange.getResponseSender().send(response.getText());
                                        LOGGER.info("Project: {}. SessionId: {}. Response is sent from endpoint: {}",
                                                projectUuid, sessionId, configuredEndpoint);
                                        MetricsAggregateService.incrementIncomingRequestToProject(projectUuid,
                                                TransportType.HTTP2_INBOUND, true);
                                    } else {
                                        throw new RuntimeException("Response message is NULL for sessionId "
                                                + sessionId + " (as a rule, not received in time from ITF-EXECUTOR)");
                                    }
                                } catch (InterruptedException e) {
                                    processException(innerExchange, projectUuid, sessionId, 524, e);
                                } catch (Exception e) {
                                    processException(innerExchange, projectUuid, sessionId, 500, e);
                                } finally {
                                    MetricsAggregateService.recordIncomingRequestDuration(projectUuid,
                                            TransportType.HTTP2_INBOUND, configuredEndpoint,
                                            Duration.between(started, OffsetDateTime.now()));
                                }
                            })))
                .build();
            undertow.start();
            }
        });
        context.start();
        LOGGER.info("Trigger {} (project {}) is activated successfully",
                getTriggerConfigurationDescriptor().getId(),
                getTriggerConfigurationDescriptor().getProjectUuid());
    }

    @Override
    protected void deactivateSpecificTrigger() throws Exception {
        if (context != null) {
            context.stop();
        }
        if (undertow != null) {
            undertow.stop();
            undertow = null;
        }
        LOGGER.info("Trigger {} (project {}) is deactivated successfully",
                getTriggerConfigurationDescriptor().getId(),
                getTriggerConfigurationDescriptor().getProjectUuid());
    }

    @Override
    protected void applyTriggerProperties(ConnectionProperties connectionProperties) {
        setConnectionProperties(connectionProperties);
    }

    protected Message setUpOut(HttpServerExchange exchange, UUID projectUuid, String sessionId)
            throws InterruptedException {
        Message message = LockProvider.INSTANCE.waitResponse(sessionId,
                Helper.getLockProviderCheckInterval(),
                Helper.getLockProviderCheckMaxInterval(),
                Helper.getLockProviderCheckMultiplier());
        LOGGER.debug("Project {}, SessionId {}. Response is got from SessionHandler.", projectUuid, sessionId);
        if (message != null) {
            Object codeStatus = message.getConnectionProperties().get(Http2Constants.RESPONSE_CODE);
            if (codeStatus == null || StringUtils.EMPTY.equals(codeStatus.toString())) {
                codeStatus = getConnectionProperties().get(Http2Constants.RESPONSE_CODE);
                if (codeStatus == null || StringUtils.EMPTY.equals(codeStatus.toString())) {
                    exchange.setStatusCode(Integer.parseInt("200")); // set status code to default value
                } else {
                    exchange.setStatusCode(Integer.parseInt(codeStatus.toString()));
                }
            } else {
                exchange.setStatusCode(Integer.parseInt(codeStatus.toString()));
            }
            fillHeaders(exchange, message.getHeaders(), getConnectionProperties().get("headers"));
        }
        LOGGER.debug("Project {}, SessionId {}. Response is built.", projectUuid, sessionId);
        return message;
    }

    private void fillHeaders(HttpServerExchange exchange, Map<String, Object> messageHeaders, Object triggerHeaders) {
        // It's discussable: may be, we should skip trigger headers if there is 1+ message header.
        // But, currently, I merge them with message headers priority.
        if (triggerHeaders instanceof Map) {
            ((Map<String, Object>) triggerHeaders).forEach(messageHeaders::putIfAbsent);
        }
        messageHeaders.forEach((key, value) ->
                exchange.getResponseHeaders().add(new HttpString(key), String.valueOf(value)));
    }

    private void processException(HttpServerExchange exchange, UUID projectUuid, String sessionId, int httpStatusCode,
                                  Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(projectUuid).append(". SessionId: ").append(sessionId)
                .append(". Error while request processing:\n").append(ex.getMessage());
        if (ex.getCause() != null) {
            sb.append("\nCaused by: ").append(ex.getCause());
        }
        exchange.setStatusCode(httpStatusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send(sb.toString());
        LOGGER.error("Project: {}. SessionId: {}. Error while request processing", projectUuid, sessionId, ex);
        MetricsAggregateService.incrementIncomingRequestToProject(projectUuid, TransportType.HTTP2_INBOUND, false);
    }
}

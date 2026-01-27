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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletEndpoint;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfigsHolder;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.core.util.constants.ProjectSettingsConstants;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.helper.ProjectSettingsHelper;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.http.Helper;
import org.qubership.automation.itf.trigger.http.HttpConstants;
import org.qubership.automation.itf.trigger.http.inbound.HttpInboundTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingRequestWrapper;

public class RestInboundTrigger extends HttpInboundTrigger {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestInboundTrigger.class);
    private static final String REST_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.rest.inbound.RESTInboundTransport";

    public RestInboundTrigger(StorableDescriptor triggerConfigurationDescriptor,
                              final ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected RouteBuilder createRoute() {
        return new ItfAbstractRouteBuilder() {
            @Override
            public void configure() {
                UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
                BigInteger projectId = getTriggerConfigurationDescriptor().getProjectId();
                String endPoint = Objects.toString(getConnectionProperties().get(HttpConstants.ENDPOINT));
                ArrayList<String> endPointsList = splitEndPoint(endPoint);
                int cnt = 0;
                for (String currentEndPoint : endPointsList) {
                    from("servlet:" + getPrefixWithProjectUuid() + currentEndPoint + "?matchOnUriPrefix=true")
                        .process(exchange -> {
                            String sessionId = UUID.randomUUID().toString();
                            MetricsAggregateService.putCommonMetrics(projectUuid, sessionId);
                            LOGGER.info("Project: {}. SessionId: {}. Request is received by endpoint: {}",
                                    projectUuid, sessionId, currentEndPoint);
                            OffsetDateTime started = OffsetDateTime.now();
                            prepareFilters((ServletEndpoint) exchange.getFromEndpoint());
                            addClientAddressInHeader(exchange);
                            preProcessUrlEncodedMessage(exchange);

                            Message message = prepareIncomingMessage(exchange, REST_INBOUND_TRANSPORT_CLASS_NAME,
                                    getConnectionProperties(), getTriggerConfigurationDescriptor(), sessionId);

                            boolean enableFastStubs = Boolean.parseBoolean(
                                    ProjectSettingsHelper.getProjectSettingsService()
                                            .get(projectId, ProjectSettingsConstants.ENABLE_FAST_STUBS, "true"));
                            boolean prepared = false;
                            if (enableFastStubs) {
                                StubEndpointConfig cfg = FastResponseConfigsHolder.INSTANCE.getConfig(
                                        getTriggerConfigurationDescriptor().getProjectUuid().toString(),
                                        StubEndpointConfig.TransportTypes.REST.name(), currentEndPoint);
                                if (cfg != null) {
                                    prepared = prepareFastResponse(exchange, message, cfg, sessionId,
                                            getTriggerConfigurationDescriptor());
                                }
                            }
                            if (!prepared) {
                                // Standard processing - because fast stubs could not prepare an answer.
                                startSession(exchange, REST_INBOUND_TRANSPORT_CLASS_NAME, getConnectionProperties(),
                                        getTriggerConfigurationDescriptor(), sessionId, message);
                                boolean resultState = false;
                                try {
                                    Message responseMessage = setUpOut(exchange, sessionId);
                                    resultState = responseMessage != null && responseMessage.getFailedMessage() == null;
                                } catch (Throwable ex) {
                                    throw new RuntimeException(String.format("Error while sending REST response, "
                                                    + "Project: %s, sessionId: %s, endpoint: %s",
                                            projectUuid, sessionId, currentEndPoint), ex);
                                } finally {
                                    LOGGER.info("Project: {}. SessionId: {}. Response is sent from endpoint: {}",
                                            projectUuid, sessionId, currentEndPoint);
                                    collectMetrics(projectUuid, TransportType.REST_INBOUND, currentEndPoint,
                                            resultState, started);
                                }
                            } else {
                                LOGGER.info("Project: {}. SessionId: {}. Fast-Stub response is sent from endpoint: {}",
                                        projectUuid, sessionId, currentEndPoint);
                                collectMetrics(projectUuid, TransportType.REST_INBOUND, currentEndPoint,
                                        true, started);
                            }
                        }).routeId((endPointsList.size() == 1) ? getId() : getId() + (++cnt))
                            .routeDescription(projectUuid.toString())
                            .group(TransportType.REST_INBOUND.name());
                }
            }

            @Override
            public Map<String, Object> getAdditionalProperties(Exchange exchange) {
                return RestInboundTrigger.this.getAdditionalProperties(exchange);
            }

            @Override
            public List<String> getExcludeHeadersList() {
                return Arrays.asList("CamelHttpServletRequest", "CamelHttpServletResponse");
            }
        };
    }

    @Override
    protected org.apache.camel.Message composeBody(org.apache.camel.Message camelMessage, Message itfMessage) {
        return Helper.composeBodyForRest(camelMessage, itfMessage);
    }

    private void preProcessUrlEncodedMessage(Exchange exchange) throws UnsupportedEncodingException {
        HttpMessage in = (HttpMessage) exchange.getIn();
        String contentType = in.getHeader("Content-Type", "text/html", String.class);
        String method = in.getHeader("CamelHttpMethod", "GET", String.class);
        if (contentType.contains("application/x-www-form-urlencoded") && method.equals("POST")) {
            HttpServletRequest request = in.getRequest();
            if (request instanceof ContentCachingRequestWrapper) {
                byte[] bytes = ((ContentCachingRequestWrapper)request).getContentAsByteArray();
                if (bytes.length > 0) {
                    String encoding = in.getHeader("CamelHttpCharacterEncoding", JvmSettings.CHARSET_NAME,
                            String.class);
                    String content = new String(bytes, encoding);
                    in.setBody(java.net.URLDecoder.decode(content, encoding));
                } else {
                    in.setBody(StringUtils.EMPTY);
                }
            }
        }
    }

    private void prepareFilters(ServletEndpoint endpoint) {
        DefaultHeaderFilterStrategy st = (DefaultHeaderFilterStrategy) endpoint.getHeaderFilterStrategy();
        st.setOutFilter(null);
        st.setInFilter(null);
        st.getOutFilter().add("content-type");
        st.setCaseInsensitive(true);
    }

    private void addClientAddressInHeader(Exchange exchange) {
        ServletRequest servletRequest = (ServletRequest) exchange.getIn().getHeader("CamelHttpServletRequest");
        Helper.addClientCoordsToHeaders(exchange.getIn().getHeaders(), servletRequest);
        Helper.fixCoNamedHeaders(exchange.getIn().getHeaders(), servletRequest);
    }

    private ArrayList<String> splitEndPoint(String s) {
        if (s.contains("(") && s.contains(")") && s.contains("|")) {
            return unpackEndPoint(s);
        } else {
            ArrayList<String> result = new ArrayList<>();
            result.add(s);
            return result;
        }
    }

    private ArrayList<String> unpackEndPoint(String s) {
        ArrayList<String> list = new ArrayList<>();
        list.add(StringUtils.EMPTY);
        int level = 0;
        String block = StringUtils.EMPTY;
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == '(' && level++ == 0) {
                for (String w : list) {
                    list.set(list.indexOf(w), w + block);
                }
                block = StringUtils.EMPTY;
            } else if (s.charAt(i) == ')' && --level == 0) {
                ArrayList<String> parts = splitBlockOnParts(block);
                for (String w : list) {
                    for (String p : parts) {
                        parts.set(parts.indexOf(p), w + p);
                    }
                    list = parts;
                }
                block = StringUtils.EMPTY;
            } else {
                block += s.charAt(i);
            }
        }
        for (String w : list) {
            list.set(list.indexOf(w), w + block);
        }
        return list;
    }

    private ArrayList<String> splitBlockOnParts(String s) {
        ArrayList<String> list = new ArrayList<>();
        int level = 0;
        String block = StringUtils.EMPTY;
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == '(') {
                ++level;
            } else if (s.charAt(i) == ')') {
                --level;
            }
            if (s.charAt(i) == '|' && level == 0) {
                list.addAll(splitEndPoint(block));
                block = StringUtils.EMPTY;
            } else {
                block += s.charAt(i);
            }
        }
        list.addAll(splitEndPoint(block));
        return list;
    }

}

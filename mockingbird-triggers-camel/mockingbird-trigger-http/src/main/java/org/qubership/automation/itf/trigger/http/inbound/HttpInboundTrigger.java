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

package org.qubership.automation.itf.trigger.http.inbound;

import static org.qubership.automation.itf.trigger.camel.Helper.isTrue;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.core.message.parser.Parser;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.jpa.context.JsonContext;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.stub.fast.ResponseDescription;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.core.stub.parser.SimpleParsingRule;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.engine.TemplateEngineFactory;
import org.qubership.automation.itf.core.util.transport.service.LockProvider;
import org.qubership.automation.itf.core.util.transport.service.SessionHandler;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.FastStubsHelper;
import org.qubership.automation.itf.trigger.camel.Helper;
import org.qubership.automation.itf.trigger.camel.inbound.AbstractCamelTrigger;
import org.qubership.automation.itf.trigger.http.HttpConstants;
import org.qubership.automation.itf.trigger.template.velocity.VelocityTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpInboundTrigger extends AbstractCamelTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpInboundTrigger.class);

    protected HttpInboundTrigger(StorableDescriptor triggerConfigurationDescriptor,
                                 ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    protected Message setUpOut(Exchange exchange, String sessionId) throws Exception {
        Message message = LockProvider.INSTANCE.waitResponse(sessionId,
                Helper.getLockProviderCheckInterval(),
                Helper.getLockProviderCheckMaxInterval(),
                Helper.getLockProviderCheckMultiplier());
        LOGGER.debug("Project {}, SessionId {}. Response is got from SessionHandler",
                getTriggerConfigurationDescriptor().getProjectUuid(), sessionId);
        if (message != null) {
            if (this.getConnectionProperties() != null) {
                message.fillConnectionProperties(this.getConnectionProperties());
            }
            message.fillHeaders(message.getConnectionProperties(), "headers");
            if (message.getFailedMessage() == null) {
                buildResponse(exchange, message);
            } else {
                buildErrorResponse(exchange, message);
            }
        } else {
            buildUnknownErrorResponse(exchange, sessionId);
        }
        LOGGER.debug("Project {}, SessionId {}. Response is built",
                getTriggerConfigurationDescriptor().getProjectUuid(), sessionId);
        return message;
    }

    protected void collectMetrics(UUID projectUuid, TransportType transportType, String endPoint, boolean resultState,
                                  OffsetDateTime started) {
        MetricsAggregateService.incrementIncomingRequestToProject(projectUuid, transportType, resultState);
        MetricsAggregateService.recordIncomingRequestDuration(projectUuid, transportType, endPoint,
                        Duration.between(started, OffsetDateTime.now()));
    }

    /**
     * Prepare fast stub response from incoming message, using StubEndpointConfig cfg.
     *
     * @param exchange - Camel exchange,
     * @param message - incoming message,
     * @param cfg - Fast stub configuration,
     * @param sessionId - session id for logging purposes,
     * @param triggerDescriptor - trigger configuration descriptor,
     * @return result of message processing, true/false,
     * @throws Exception - mostly in case exceptions while sending message to reporting.
     */
    public boolean prepareFastResponse(Exchange exchange,
                                       Message message,
                                       StubEndpointConfig cfg,
                                       String sessionId,
                                       StorableDescriptor triggerDescriptor) throws Exception {
        if (cfg == null || isTrue(cfg.getDisabled())) {
            return false;
        }
        Date started = new Date();
        Parser parser = new Parser();
        JsonContext parsedContext = parser.parseToJsonContext(message, cfg.getParsingRules(),
                triggerDescriptor.getProjectId());
        duplicateContextToTcAndSp(parsedContext);
        FastStubsHelper.recalculateOperationDefinitionKey(cfg, parsedContext);
        List<SimpleParsingRule> operationParsingRules =
                cfg.getOperationParsingRules().get(cfg.getOperationDefinitionKey());
        if (operationParsingRules != null) {
            JsonContext parsedOperationsContext = parser.parseToJsonContext(message, operationParsingRules,
                    triggerDescriptor.getProjectId());
            duplicateContextToTcAndSp(parsedOperationsContext);
            parsedContext.merge(parsedOperationsContext);
        }
        ResponseDescription responseDescription = FastStubsHelper
                .checkConditions(parsedContext, cfg);
        if (responseDescription == null
                && !(cfg.getDefaultResponse() == null || isTrue(cfg.getDefaultResponse().getDisabled()))) {
            responseDescription = cfg.getDefaultResponse();
        }
        if (responseDescription == null) {
            return false;
        }
        Message outgoing = setUpFastOut(exchange, sessionId, responseDescription, parsedContext);
        if (!FastStubsHelper.isReportingSkipped(responseDescription.getSkipReporting(), cfg.getSkipReporting())) {
            try {
                FastStubsHelper.sendMessageToReport(message, outgoing, triggerDescriptor, started, new Date(),
                        parsedContext, cfg.getConfiguredEndpoint(), responseDescription);
            } catch (Exception ex) {
                LOGGER.error("Error while reporting from Fast Stub, endpoint {}", cfg.getConfiguredEndpoint(), ex);
            }
        }
        return true;
    }

    private void duplicateContextToTcAndSp(JsonContext ctx) {
        JsonContext sp = new JsonContext();
        sp.merge(ctx);
        JsonContext tc = new JsonContext();
        tc.put("saved", new JsonContext());
        ((JsonContext)tc.get("saved")).merge(ctx);
        ctx.put("sp", sp);
        ctx.put("tc", tc);
    }

    protected Message setUpFastOut(Exchange exchange, String sessionId, ResponseDescription responseDescription,
                                   JsonContext context) throws Exception {
        Message message = new Message(replaceVariables(responseDescription.getBody(), context));
        if (this.getConnectionProperties() != null) {
            message.fillConnectionProperties(this.getConnectionProperties());
        }
        message.fillHeaders(message.getConnectionProperties(), "headers");
        processHeaders(responseDescription.getHeaders(), context);
        message.getHeaders().putAll(responseDescription.getHeaders());

        String responseCodeFromTemplate = replaceVariables(responseDescription.getResponseCode(), context);
        if (StringUtils.isNotEmpty(responseCodeFromTemplate)) {
            message.getHeaders().put(Exchange.HTTP_RESPONSE_CODE, responseCodeFromTemplate);
        }
        buildResponse(exchange, message);
        LOGGER.debug("Project {}, SessionId {}. Response is built",
                getTriggerConfigurationDescriptor().getProjectUuid(), sessionId);
        return message;
    }

    protected String replaceVariables(String input, JsonContext context) {
        return ((VelocityTemplateEngine) TemplateEngineFactory.get()).process(input, context);
    }

    protected void processHeaders(Map<String, Object> headersMap, JsonContext context) {
        for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
            if (entry.getValue() instanceof List) {
                List<String> oldlist = (List<String>) entry.getValue();
                List<String> newlist = new ArrayList<>();
                for (String elem : oldlist) {
                    newlist.add(replaceVariables(java.util.Objects.toString(elem,
                            StringUtils.EMPTY), context));
                }
                entry.setValue(newlist);
            } else {
                entry.setValue(replaceVariables(java.util.Objects.toString(entry.getValue(),
                        StringUtils.EMPTY), context));
            }
        }
    }

    private void buildErrorResponse(Exchange exchange, Message message) {
        org.apache.camel.Message out = exchange.getOut();
        out.setHeader(Exchange.HTTP_RESPONSE_CODE, "500");
        out.setHeader(Exchange.CONTENT_TYPE, "text/plain");
        Map<String, Object> headers = message.getHeaders();
        if (headers != null) {
            out.getHeaders().putAll(headers);
        }
        out.setBody(message.getFailedMessage());
        out.setFault(true); // Let's notify SOAP/REST trigger that there was fault while processing
    }

    private void buildUnknownErrorResponse(Exchange exchange, String sessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div>").append("<p>Response message is NULL for sessionId ").append(sessionId);

        Exception ex = exchange.getException();
        if (ex == null) {
            sb.append(" and there are no exchange exceptions.</p>")
                    .append("<p>Recommendations:</p>");
        } else {
            sb.append("; exchange exception(s) happen, please check: ")
                    .append(ex.getMessage()).append("<br>Stacktrace: ").append(ExceptionUtils.getStackTrace(ex))
                    .append("</p>")
                    .append("<p>Additionally:</p>");
        }

        sb.append("<p> 1) Please check ITF logs in the ITF root folder and 'itf_logs' sub-folder,</p>")
                .append("<p> 2) Check if you haven't properly configured response template on the situation.</p>")
                .append("<p>Please contact ITF Support in case of investigation difficulties.</p>")
                .append("</div>");

        org.apache.camel.Message out = exchange.getOut();
        out.setBody(sb.toString());
        out.setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        out.setHeader(Exchange.CONTENT_TYPE, "text/html");
        out.setFault(true); // Let's notify SOAP/REST trigger that there was fault while processing
    }

    private void buildResponse(Exchange exchange, Message message) throws Exception {
        org.apache.camel.Message out = exchange.getOut();
        out.setHeader(Exchange.HTTP_RESPONSE_CODE, getOrDefault(message.getConnectionProperties(),
                HttpConstants.RESPONSE_CODE, "200"));
        out.setHeader(Exchange.CONTENT_TYPE, getOrDefault(message.getConnectionProperties(),
                HttpConstants.CONTENT_TYPE, "text/html"));
        Map<String, Object> headers = message.getHeaders();
        if (headers != null) {
            out.getHeaders().putAll(headers);
        }
        message.convertAndSetHeaders(out.getHeaders());
        composeBody(out, message);
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        LockProvider.INSTANCE.init();
        SessionHandler.INSTANCE.init();
        createAndConfigureContext();
    }

    protected abstract RoutesBuilder createRoute();

    protected abstract org.apache.camel.Message composeBody(org.apache.camel.Message camelMessage, Message itfMessage)
            throws Exception;

    @Override
    protected void deactivateSpecificTrigger() throws Exception {
        String id = getId();
        for (int cnt = 1; ; cnt++) {
            String curId = id + cnt;
            if (CAMEL_CONTEXT.getRoute(curId) == null) {
                break;
            }
            CAMEL_CONTEXT.stopRoute(curId);
            CAMEL_CONTEXT.removeRoute(curId);
        }
        CAMEL_CONTEXT.stopRoute(id);
        CAMEL_CONTEXT.removeRoute(id);
        CAMEL_CONTEXT.removeComponent(id);
        LOGGER.info("CAMEL_CONTEXT [{}] is deactivated successfully", id);
    }

    protected void applyTriggerProperties(ConnectionProperties connectionProperties) {
        setConnectionProperties(connectionProperties);
    }

    public void validateRequest(Exchange exchange) {
    }

    public void validateResponse(Exchange exchange) {
    }

    private Object getOrDefault(Map<String, Object> properties, String key, String defaultValue) {
        Object value = properties.get(key);
        return (value == null) || !properties.containsKey(key) || (StringUtils.EMPTY.equals(value.toString()))
                ? defaultValue : value;
    }

    protected Map<String, Object> getAdditionalProperties(Exchange exchange) {
        Map<String, Object> addProperties = new HashMap<>();
        try {
            String uri = exchange.getIn().getHeader("CamelHttpUri", String.class);
            String query = exchange.getIn().getHeader("CamelHttpQuery", String.class); // Camel doesn't decode query
            // string
            String uriParams = StringUtils.isBlank(uri) ? StringUtils.EMPTY : uri;
            if (!StringUtils.isBlank(query)) {
                uriParams = uriParams + "?" + query;
            }
            addProperties.put("uriParams", java.net.URLDecoder.decode(uriParams, JvmSettings.CHARSET_NAME));
            addProperties.put("method", exchange.getIn().getHeader("CamelHttpMethod").toString());
            // DF also puts all headers to connectionProperties. I don't think we (camel) should do the same...
            // Commented by Alexander Kapustin
            //addProperties.put("headers", exchange.getIn().getHeaders());
        } catch (UnsupportedEncodingException ex) {
            // Silently go away now. May be we should throw an exception or log it - it should be discussed
        }
        return addProperties;
    }

    private void createAndConfigureContext() throws Exception {
        CAMEL_CONTEXT.addRoutes(createRoute());

        //Replace component with newer one
        if (CAMEL_CONTEXT.getComponentNames().contains(getId())) {
            CAMEL_CONTEXT.removeComponent(getId());
        }
        HttpComponent http4Component = new HttpComponent();
        CAMEL_CONTEXT.addComponent(getId(), http4Component);

        //Allow editing headers forbidden by default. Like 'Date','Host', etc.
        /* Commented; It's non-working for inbound processing because:
            - we set the headerFilterStrategy for http-component,
            - but when a response is sent, headerFilterStrategy from the endpoint (not component) is used!!!
            - Unfortunately, we can not set the headerFilterStrategy for the endpoint here (when a trigger is
            activated),
                because the context doesn't contain endpoints (for REST).
                    - For SOAP, endpoints are available here, via smth like: context.getRouteDefinitions().get(0)
                    .getInputs().get(0).getEndpoint()
            - So, we will set the headerFilterStrategy later, when a message is processed. It works fine both for
            REST and SOAP
         */
        /*
        DefaultHeaderFilterStrategy ITFHeaderFilterStrategy = new DefaultHeaderFilterStrategy();
        ITFHeaderFilterStrategy.setOutFilter(new HashSet<>());
        context.getComponent("http", org.apache.camel.component.http4.HttpComponent.class)
        .setHeaderFilterStrategy(ITFHeaderFilterStrategy);
        */
    }

    public CamelContext getCamelContext() {
        return CAMEL_CONTEXT;
    }
}

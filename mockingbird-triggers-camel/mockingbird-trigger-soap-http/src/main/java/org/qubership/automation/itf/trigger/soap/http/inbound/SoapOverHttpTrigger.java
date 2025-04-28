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

package org.qubership.automation.itf.trigger.soap.http.inbound;

import static org.qubership.automation.itf.trigger.camel.Helper.getBrokerMessageSelectorValue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StringSource;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.component.cxf.DataFormat;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.model.RouteDefinition;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.MustUnderstandInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapOutInterceptor;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.ws.policy.PolicyException;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.communication.TriggerExecutionMessageSender;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.communication.message.CommonTriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfigsHolder;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.core.util.constants.ProjectSettingsConstants;
import org.qubership.automation.itf.core.util.constants.PropertyConstants;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.helper.ProjectSettingsHelper;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.http.Helper;
import org.qubership.automation.itf.trigger.http.HttpConstants;
import org.qubership.automation.itf.trigger.http.inbound.HttpInboundTrigger;
import org.qubership.automation.itf.trigger.soap.http.SoapOverHttpHelper;
import org.qubership.automation.itf.xsd.XsdValidationResult;
import org.qubership.automation.itf.xsd.XsdValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class SoapOverHttpTrigger extends HttpInboundTrigger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoapOverHttpTrigger.class);
    private static final String SOAP_OVER_HTTP_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.soap.http.inbound.SOAPOverHTTPInboundTransport";

    public SoapOverHttpTrigger(StorableDescriptor triggerConfigurationDescriptor,
                               ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected RoutesBuilder createRoute() {
        SoapOverHttpHelper.prepareBusContext(this);
        return new ItfAbstractRouteBuilder() {
            public void configure() throws Exception {
                UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
                BigInteger projectId = getTriggerConfigurationDescriptor().getProjectId();
                String currentEndPoint = Objects.toString(getConnectionProperties().get(HttpConstants.ENDPOINT));
                String wsdlPath = getWsdlPath();
                CxfEndpoint cxfEndpoint = createCxfEndpoint(wsdlPath);
                RouteDefinition routeFrom = from(cxfEndpoint);
                routeFrom.onException(Throwable.class).continued(false);
                routeFrom.process(exchange -> {
                    String sessionId = UUID.randomUUID().toString();
                    MetricsAggregateService.putCommonMetrics(projectUuid, sessionId);
                    LOGGER.info("Project: {}. SessionId: {}. Request is received by endpoint: {}",
                            projectUuid, sessionId, currentEndPoint);
                    OffsetDateTime started = OffsetDateTime.now();
                    clearOutFilter((CxfEndpoint) exchange.getFromEndpoint());
                    addClientAddressInHeader(exchange);
                    String stringBody = getStringBody(exchange.getIn());
                    validate(exchange.getIn(), stringBody, PropertyConstants.Soap.REQUEST_XSD_PATH);
                    exchange.getIn().setBody(stringBody);

                    /*
                        Validation by XSD (if turned on) is completed. Now is the time for fast stub.
                     */

                    Message message = prepareIncomingMessage(exchange, SOAP_OVER_HTTP_INBOUND_TRANSPORT_CLASS_NAME,
                            getConnectionProperties(), getTriggerConfigurationDescriptor(), sessionId);

                    boolean enableFastStubs = Boolean.parseBoolean(
                            ProjectSettingsHelper.getProjectSettingsService()
                                    .get(projectId, ProjectSettingsConstants.ENABLE_FAST_STUBS, "true"));
                    boolean prepared = false;
                    if (enableFastStubs) {
                        StubEndpointConfig cfg = FastResponseConfigsHolder.INSTANCE.getConfig(
                                getTriggerConfigurationDescriptor().getProjectUuid().toString(),
                                StubEndpointConfig.TransportTypes.SOAP.name(), currentEndPoint);
                        if (cfg != null) {
                            prepared = prepareFastResponse(exchange, message, cfg, sessionId,
                                    getTriggerConfigurationDescriptor());
                        }
                    }
                    if (!prepared) {
                        // Standard processing - because fast stubs could not prepare an answer.
                        startSession(exchange, SOAP_OVER_HTTP_INBOUND_TRANSPORT_CLASS_NAME, getConnectionProperties(),
                                getTriggerConfigurationDescriptor(), sessionId, message);
                        Message responseMessage = setUpOut(exchange, sessionId);
                        boolean resultState = responseMessage != null && responseMessage.getFailedMessage() == null;
                        if (!exchange.getOut().isFault()) {
                            try {
                                validate(exchange.getOut(), PropertyConstants.Soap.RESPONSE_XSD_PATH);
                            } catch (IllegalArgumentException | RuntimeCamelException ex) {
                                if (responseMessage != null) {
                                    responseMessage.setFailedMessage(ex.getMessage());
                                    responseMessage.getHeaders().put("CamelHttpResponseCode", "500");
                                }
                                throw ex;
                            } finally {
                                LOGGER.info("Project: {}. SessionId: {}. Response is sent from endpoint: {}",
                                        projectUuid, sessionId, currentEndPoint);
                                collectMetrics(projectUuid, TransportType.SOAP_OVER_HTTP_INBOUND,
                                        currentEndPoint, resultState, started);
                            }
                        } else {
                            LOGGER.info("Project: {}. SessionId: {}. Response (fault) is sent from endpoint: {}",
                                    projectUuid, sessionId, currentEndPoint);
                            collectMetrics(projectUuid, TransportType.SOAP_OVER_HTTP_INBOUND,
                                    currentEndPoint, resultState, started);
                        }
                    } else {
                        LOGGER.info("Project: {}. SessionId: {}. Fast-Stub response is sent from endpoint: {}",
                                projectUuid, sessionId, currentEndPoint);
                        collectMetrics(projectUuid, TransportType.SOAP_OVER_HTTP_INBOUND,
                                currentEndPoint, true, started);
                    }
                })
                .routeId(getId());
                cxfEndpoint.start();
            }

            @Override
            public Map<String, Object> getAdditionalProperties(Exchange exchange) {
                return SoapOverHttpTrigger.this.getAdditionalProperties(exchange);
            }

            @Override
            public List<String> getExcludeHeadersList() {
                return Arrays.asList("CamelCxfMessage", "org.apache.cxf.headers.Header.list");
            }
        };
    }

    @Override
    protected org.apache.camel.Message composeBody(org.apache.camel.Message camelMessage, Message itfMessage) {
        return Helper.composeBodyForSoapInbound(
                camelMessage,
                itfMessage,
                ((CxfEndpoint) camelMessage.getExchange().getFromEndpoint()).getDataFormat().equals(DataFormat.RAW));
    }

    @Override
    public void validateRequest(Exchange exchange) {
        validate(exchange.getIn(), PropertyConstants.Soap.REQUEST_XSD_PATH);
    }

    @Override
    public void validateResponse(Exchange exchange) {
        validate(exchange.getOut(), PropertyConstants.Soap.RESPONSE_XSD_PATH);
    }

    private String getStringBody(org.apache.camel.Message camelMessage) {
        Object messageBody = camelMessage.getBody();
        if (messageBody instanceof CxfPayload) {
            return serializeCxfPayloadBody((CxfPayload)messageBody);
        } else if (messageBody instanceof ByteArrayInputStream) {
            return readBais((ByteArrayInputStream)messageBody);
        } else {
            return (String) messageBody;
        }
    }

    private void clearOutFilter(CxfEndpoint endpoint) {
        DefaultHeaderFilterStrategy st = (DefaultHeaderFilterStrategy) endpoint.getHeaderFilterStrategy();
        st.setOutFilter(null);
    }

    private void addClientAddressInHeader(Exchange exchange) {
        try {
            ServletRequest servletRequest = (ServletRequest) ((SoapMessage) exchange.getIn().getHeader(
                    "CamelCxfMessage")).get("HTTP.REQUEST");
            Helper.addClientCoordsToHeaders(exchange.getIn().getHeaders(), servletRequest);
            Helper.fixCoNamedHeaders(exchange.getIn().getHeaders(), servletRequest);
        } catch (Exception ex) {
            LOGGER.warn("addClientAddressInHeader: exceptions while headers processing", ex);
        }
    }

    /*
     *   If no exception was thrown, validation was successful
     */
    private void validate(org.apache.camel.Message camelMessage, String xsdPathParameter) {
        String xsdPath = getConnectionProperties().obtain(xsdPathParameter);
        if (StringUtils.isBlank(xsdPath)) {
            LOGGER.debug("Message validation is skipped due to empty parameter {}", xsdPathParameter);
            return;
        }
        String stringBody = getStringBody(camelMessage);
        if (StringUtils.isBlank(stringBody)) {
            LOGGER.warn("Message validation is skipped due to message is empty");
            return;
        }
        performValidation(stringBody, camelMessage.getBody(), xsdPath);
    }

    private void validate(org.apache.camel.Message camelMessage, String stringBody, String xsdPathParameter) {
        if (StringUtils.isBlank(stringBody)) {
            LOGGER.warn("Message validation is skipped due to message is empty");
            return;
        }
        String xsdPath = getConnectionProperties().obtain(xsdPathParameter);
        if (StringUtils.isBlank(xsdPath)) {
            LOGGER.debug("Message validation is skipped due to empty parameter {}", xsdPathParameter);
            return;
        }
        performValidation(stringBody, camelMessage.getBody(), xsdPath);
    }

    private XsdValidationResult performValidation(String stringBody, Object messageBody, String xsdPath) {
        XsdValidationResult validationResult;
        if (messageBody instanceof CxfPayload) {
            validationResult = new XsdValidator()
                    .validate(stringBody, collectNamespaces((CxfPayload) messageBody, xsdPath));
        } else {
            validationResult = new XsdValidator().validate(stringBody, xsdPath);
        }
        if (validationResult.isFailed()) {
            throw new IllegalArgumentException(
                    validationResult.getException() instanceof SAXException
                            ? "Invalid message. " : "Unexpected exception. " + validationResult
            );
        }
        return validationResult;
    }

    private String serializeCxfPayloadBody(CxfPayload cxfPayloadBody) {
        List<Source> srcList = cxfPayloadBody.getBodySources();
        if (srcList == null || srcList.isEmpty()) {
            return StringUtils.EMPTY;
        }
        Source src = srcList.get(0);
        if (src instanceof StringSource) {
            return ((StringSource) src).getText();
        }
        List<Element> elementList = cxfPayloadBody.getBody();
        Document document = elementList.get(0).getOwnerDocument();
        DOMImplementationLS domImplLs = (DOMImplementationLS) document.getImplementation();
        LSSerializer serializer = domImplLs.createLSSerializer();
        return serializer.writeToString(elementList.get(0));
    }

    private String readBais(ByteArrayInputStream messageBody) {
        int n = messageBody.available();
        byte[] bytes = new byte[n];
        messageBody.read(bytes, 0, n);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private StreamSource[] collectNamespaces(CxfPayload cxfPayloadBody, String xsdPath) {
        ArrayList<StreamSource> result = new ArrayList<>();
        result.add(new StreamSource(xsdPath));

        List<Element> list = cxfPayloadBody.getBody();
        if (list != null && !list.isEmpty()) {
            result.add(new StreamSource(list.get(0).getNamespaceURI()));
        }

        return result.toArray(new StreamSource[0]);
    }

    private String getWsdlPath() {
        String pathToWsdlFile = getConnectionProperties().obtain(PropertyConstants.Soap.WSDL_PATH);
        if (StringUtils.isBlank(pathToWsdlFile)) {
            throw new IllegalArgumentException("Path/URL to WSDL file is not specified");
        }
        try {
            return SoapOverHttpHelper.getAndCheckPath(pathToWsdlFile, true, "WSDL file");
        } catch (MalformedURLException | FileNotFoundException e) {
            throw new IllegalArgumentException("Path/URL to WSDL file is invalid (" + pathToWsdlFile + ")", e);
        }
    }

    /*  The method is to set endpoint properties.
     *   Properties examples are:
     *       props.put("allow-multiplex-endpoint", Boolean.TRUE);
     *       props.put("schema-validation-enabled", Boolean.FALSE);
     *   Another example:
     *       cxfEndpoint.setPortName("ACSWSSoap"); // TASUP-7092, NITP-5169
     * */
    protected void setExtraProperties(CxfEndpoint cxfEndpoint, Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        // This is the 1st implementation (TASUP-7092, NITP-5169), so it's a subject to revise later
        Map<String, Object> props = new HashMap<>();
        for (Map.Entry<String, Object> item : properties.entrySet()) {
            if (StringUtils.isBlank(item.getKey()) || item.getValue() == null) {
                continue;
            }
            String key = item.getKey().trim();
            switch (key) {
                case "dataFormat":
                    try {
                        DataFormat dtf = DataFormat.valueOf((String) item.getValue());
                        cxfEndpoint.setDataFormat(dtf);
                    } catch (Exception ignore) {
                        LOGGER.debug("Incorrect dataFormat property: {}", item.getValue());
                    }
                    break;
                case "namespace":
                    break;
                case "endpoint":
                    if (properties.containsKey("namespace")) {
                        cxfEndpoint.setPortName(new QName((String) properties.get("namespace"),
                                (String) item.getValue()));
                    } else {
                        cxfEndpoint.setPortName((String) item.getValue());
                    }
                    break;
                case "servicename":
                    if (properties.containsKey("namespace")) {
                        cxfEndpoint.setServiceName(new QName((String) properties.get("namespace"),
                                (String) item.getValue()));
                    }
                    break;
                default:
                    props.put(key, item.getValue());
            }
        }
        if (!props.isEmpty()) {
            cxfEndpoint.setProperties(props);
        }
    }

    private CxfEndpoint createCxfEndpoint(String wsdlPath) {
        CxfEndpoint cxfEndpoint = new CxfEndpoint();
        cxfEndpoint.setCamelContext(CAMEL_CONTEXT);
        cxfEndpoint.setWsdlURL(wsdlPath);
        cxfEndpoint.setPublishedEndpointUrl(getPrefixWithProjectUuid()
                + getConnectionProperties().obtain(HttpConstants.ENDPOINT));
        cxfEndpoint.setAddress(getPrefixWithProjectUuid() + getConnectionProperties().obtain(HttpConstants.ENDPOINT));
        cxfEndpoint.setDataFormat(DataFormat.PAYLOAD);

        setExtraProperties(cxfEndpoint,
                getConnectionProperties().obtain(PropertyConstants.Commons.ENDPOINT_PROPERTIES));

        cxfEndpoint.setBus(BusFactory.getThreadDefaultBus());
        final PlainSoapMessage plainSoapMessage = new PlainSoapMessage();

        cxfEndpoint.getOutFaultInterceptors().add(new AbstractPhaseInterceptor<SoapMessage>(Phase.POST_PROTOCOL) {
            @Override
            public void handleMessage(SoapMessage message) throws Fault {
                SoapOverHttpTrigger.this.handleMessage(message, plainSoapMessage);
            }
        });

        cxfEndpoint.getInInterceptors().add(new ItfMustUnderstandInterceptor());

        cxfEndpoint.getInInterceptors().add(new ItfSoapMessageAbstractPhaseInterceptor(plainSoapMessage));

        cxfEndpoint.getInInterceptors().add(new AbstractPhaseInterceptor<SoapMessage>(Phase.PRE_LOGICAL) {
            @Override
            public void handleMessage(SoapMessage message) throws Fault {
                SoapOverHttpTrigger.this.revertToPlainMessage(message, plainSoapMessage);
            }
        });

        cxfEndpoint.getOutInterceptors().add(new AbstractPhaseInterceptor(Phase.SETUP) {
            @Override
            public void handleMessage(org.apache.cxf.message.Message message) throws Fault {
                Interceptor<? extends org.apache.cxf.message.Message> interceptorToRemove = null;
                Interceptor<? extends org.apache.cxf.message.Message> currentInterceptor;
                getResponseCode(message);
                ListIterator<Interceptor<? extends org.apache.cxf.message.Message>> iterator =
                        message.getInterceptorChain().getIterator();
                while (iterator.hasNext()) {
                    currentInterceptor = iterator.next();
                    if (currentInterceptor instanceof SoapOutInterceptor) {
                        interceptorToRemove = currentInterceptor;
                        break;
                    }
                }
                if (interceptorToRemove != null) {
                    message.getInterceptorChain().remove(interceptorToRemove);
                }
            }
        });

        return cxfEndpoint;
    }

    /* To avoid ClassCastException (cxf 3.1.5 - 3.1.18 on the some WSDLs and messages) in the
          \org\apache\cxf\cxf-rt-transports-http\3.1.5\cxf-rt-transports-http-3.1.5-sources
          .jar!\org\apache\cxf\transport\http\AbstractHTTPDestination.java
          Method: getReponseCodeFromMessage()
          Cast: String to Integer
       This exception isn't thrown to the client and/or ITF console and/or ITF log files.
       It entails empty response message returned to the client (but response code is 200)
    */
    private void getResponseCode(org.apache.cxf.message.Message message) {
        String key = org.apache.cxf.message.Message.class.getName() + ".RESPONSE_CODE";
        if (message.containsKey(key)) {
            Object obj = message.get(key);
            if (obj instanceof String) {
                try {
                    int respCode = Integer.parseInt((String) obj);
                    message.put(key, respCode);
                } catch (NumberFormatException ex) {
                    // do nothing
                }
            }
        }
    }

    private void handleMessage(SoapMessage soapMessage, PlainSoapMessage body) throws Fault {
        final Fault fault = (Fault) soapMessage.getContent(Exception.class);
        if (fault == null) {
            return;
        }

        if (fault.getCause() instanceof PolicyException) {
            handlePolicyError(soapMessage, fault);
            return;
        }

        Exchange exchange = (Exchange) soapMessage.getExchange().get("org.apache.camel.Exchange");
        if (Objects.nonNull(exchange) && exchange.hasOut()) {
            // It's response processing in case of ITF errors
            //  No processing currently. Please note: ITF errors must be enclosed into html or xml to avoid CXF errors
        } else {
            // It's request processing in case of CXF- or XSD-validation errors
            Message message = new Message();
            message.setText(body.getText());
            message.setFailedMessage(fault.getMessage()
                    + "\nStacktrace: " + ExceptionUtils.getStackTrace(fault)
                    + "\nInbound message was: " + body.getText());
            message.getConnectionProperties().putAll(getConnectionProperties());
            message.getHeaders().putAll(body.getHeaders());
            if (fault.getStatusCode() > 0) {
                message.getConnectionProperties().put(HttpConstants.RESPONSE_CODE, fault.getStatusCode());
            }
            String sessionId = UUID.randomUUID().toString();
            try {
                TriggerExecutionMessageSender.send(
                        new CommonTriggerExecutionMessage(
                                StringUtils.EMPTY,
                                message,
                                getTriggerConfigurationDescriptor(),
                                sessionId,
                                getBrokerMessageSelectorValue()
                        ), getTriggerConfigurationDescriptor().getProjectUuid()
                );
            } catch (Exception e) {
                LOGGER.error("Failed to send message to broker", e);
            }
        }
    }

    private void handlePolicyError(SoapMessage soapMessage, Fault fault) throws Fault {
        prepareAndSendErrorResponse(soapMessage, fault);
        LOGGER.error("An error occurred while processing the SOAP message - a PolicyException was encountered. "
                + "Please check the policy description and resource availability (URL) in the WSDL file. "
                + "ITF did not process this SOAP message. Endpoint: " + getConnectionProperties().get("endpoint")
                + " , WSDL path: " + getWsdlPath(), fault.getCause());
    }

    /*
     Send a response to the client side with a status code and an error in the message body.
     ITF will not process the message and the result or error will not be added to the context.
     We just send the error to the client
    */
    private void prepareAndSendErrorResponse(SoapMessage soapMessage, Fault fault) {
        HttpServletResponse resp = (HttpServletResponse) soapMessage.getExchange()
                .getInMessage().get(AbstractHTTPDestination.HTTP_RESPONSE);
        resp.setStatus(fault.getStatusCode());
        try {
            resp.getOutputStream().write(fault.getMessage().getBytes(JvmSettings.CHARSET));
            resp.getOutputStream().flush();
        } catch (Exception e) {
            LOGGER.error("Error while sending SOAP Error response ", e);
        }
        soapMessage.getInterceptorChain().setFaultObserver(null); //avoid return soap fault
        soapMessage.getInterceptorChain().abort();
    }

    private void revertToPlainMessage(SoapMessage soapMessage, PlainSoapMessage body) {
        List<Object> listObj = soapMessage.getContent(List.class);
        if (listObj != null) {
            if (!listObj.isEmpty()) {
                listObj.clear();
            }
            listObj.add(new StringSource(body.getText()));
        }
    }

    private static class ItfMustUnderstandInterceptor extends MustUnderstandInterceptor {
        public ItfMustUnderstandInterceptor() {
            super(Phase.PRE_PROTOCOL);
        }

        @Override
        public void handleMessage(SoapMessage soapMessage) throws Fault {
            //Ignore all incoming mustUnderstand parameters on soap-headers
            soapMessage.getHeaders().forEach(header -> ((SoapHeader) header).setMustUnderstand(false));
        }
    }

    @SuppressFBWarnings(value = "SIC_INNER_SHOULD_BE_STATIC", justification = "Checked; it can't be static")
    private class ItfSoapMessageAbstractPhaseInterceptor extends AbstractPhaseInterceptor<SoapMessage> {
        private final PlainSoapMessage plainSoapMessage;

        public ItfSoapMessageAbstractPhaseInterceptor(PlainSoapMessage plainSoapMessage) {
            super(Phase.RECEIVE);
            this.plainSoapMessage = plainSoapMessage;
        }

        @Override
        public void handleMessage(SoapMessage message) throws Fault {
            try (InputStream content = message.getContent(InputStream.class)) {
                try {
                    String sourceMessage = IOUtils.toString(content);
                    plainSoapMessage.setText(sourceMessage);
                    message.setContent(InputStream.class,
                            new ByteArrayInputStream(sourceMessage.getBytes(JvmSettings.CHARSET)));
                } catch (IOException e) {
                    LOGGER.error("Unable to get message from soap request", e);
                }
            } catch (IOException e) {
                LOGGER.error("Unable to close input stream", e);
            }
            try {
                Map<String, List<String>> headers
                        = CastUtils.cast((Map) message.get(org.apache.cxf.message.Message.PROTOCOL_HEADERS));
                if (headers != null && !headers.isEmpty()) {
                    plainSoapMessage.setHeaders(headers.entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, e -> e.getValue().isEmpty()
                                    ? "" : e.getValue().size() == 1 ? e.getValue().get(0) : e.getValue())
                    ));
                }
            } catch (Exception ex) {
                LOGGER.error("Unable to get message headers from soap request", ex);
            }
        }
    }

    @SuppressFBWarnings(value = "SIC_INNER_SHOULD_BE_STATIC", justification = "Checked; it can't be static")
    private class PlainSoapMessage {

        private String text;
        private Map<String, Object> headers = new HashMap<>();

        PlainSoapMessage() {
        }

        String getText() {
            return text;
        }

        void setText(String text) {
            this.text = text;
        }

        Map<String, Object> getHeaders() {
            return headers;
        }

        void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }
    }
}

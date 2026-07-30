package org.qubership.automation.itf.trigger.soap.http.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.cxf.BusFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.constants.PropertyConstants;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.http.HttpConstants;
import org.qubership.automation.itf.trigger.soap.http.SoapOverHttpHelper;

/**
 * Tests of requests processing in SoapOverHttpTrigger.
 * Should be executed in non-parallel mode (ExecutionMode.SAME_THREAD is set explicitly).
 */
@Execution(ExecutionMode.SAME_THREAD)
class SoapOverHttpTriggerIntegrationTest {
    @TempDir
    Path tempDir;

    private SoapOverHttpTrigger trigger;
    private CamelContext camelContext;
    private ConnectionProperties connectionProperties;
    private StorableDescriptor descriptor;
    private Path xsdPath;

    private static final UUID projectUuid = UUID.randomUUID();

    // Test WSDL (simplified)
    private static final String TEST_WSDL =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                                      xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                                      xmlns:tns="http://test.example.com/"
                                      targetNamespace="http://test.example.com/">
                        <wsdl:types>
                            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                                        targetNamespace="http://test.example.com/">
                                <xsd:element name="GetUserRequest">
                                    <xsd:complexType>
                                        <xsd:sequence>
                                            <xsd:element name="userId" type="xsd:int"/>
                                        </xsd:sequence>
                                    </xsd:complexType>
                                </xsd:element>
                                <xsd:element name="GetUserResponse">
                                    <xsd:complexType>
                                        <xsd:sequence>
                                            <xsd:element name="userName" type="xsd:string"/>
                                        </xsd:sequence>
                                    </xsd:complexType>
                                </xsd:element>
                            </xsd:schema>
                        </wsdl:types>
                        <wsdl:message name="GetUserRequest">
                            <wsdl:part name="parameters" element="tns:GetUserRequest"/>
                        </wsdl:message>
                        <wsdl:message name="GetUserResponse">
                            <wsdl:part name="parameters" element="tns:GetUserResponse"/>
                        </wsdl:message>
                        <wsdl:portType name="TestService">
                            <wsdl:operation name="GetUser">
                                <wsdl:input message="tns:GetUserRequest"/>
                                <wsdl:output message="tns:GetUserResponse"/>
                            </wsdl:operation>
                        </wsdl:portType>
                        <wsdl:binding name="TestServiceSoap" type="tns:TestService">
                            <soap:binding transport="http://schemas.xmlsoap.org/soap/http"/>
                            <wsdl:operation name="GetUser">
                                <soap:operation soapAction=""/>
                                <wsdl:input><soap:body use="literal"/></wsdl:input>
                                <wsdl:output><soap:body use="literal"/></wsdl:output>
                            </wsdl:operation>
                        </wsdl:binding>
                        <wsdl:service name="TestService">
                            <wsdl:port name="TestServiceSoap" binding="tns:TestServiceSoap">
                                <soap:address location="http://localhost:8080/test"/>
                            </wsdl:port>
                        </wsdl:service>
                    </wsdl:definitions>""";

    // Valid SOAP request
    private static final String VALID_SOAP_REQUEST =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:tns="http://test.example.com/">
                        <soap:Body>
                            <tns:GetUserRequest>
                                <tns:userId>123</tns:userId>
                            </tns:GetUserRequest>
                        </soap:Body>
                    </soap:Envelope>""";

    // Invalid SOAP request
    private static final String INVALID_SOAP_REQUEST =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:tns="http://test.example.com/">
                        <soap:Body>
                            <tns:GetUserRequest>
                                <tns:userId>not_an_integer</tns:userId>
                            </tns:GetUserRequest>
                        </soap:Body>
                    </soap:Envelope>""";

    @BeforeEach
    void setUp() throws Exception {
        // 1. Create WSDL file
        Path wsdlPath = tempDir.resolve("test.wsdl");
        Files.writeString(wsdlPath, TEST_WSDL);

        // 2. Create XSD file (separately from WSDL)
        xsdPath = tempDir.resolve("test.xsd");
        String xsdContent =
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                   targetNamespace="http://test.example.com/"
                                   xmlns:tns="http://test.example.com/"
                                   elementFormDefault="qualified">
                            <xs:element name="GetUserRequest">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="userId" type="xs:int"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="GetUserResponse">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="userName" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:schema>""";
        Files.writeString(xsdPath, xsdContent);

        // 3. Configure ConnectionProperties (REQUEST_XSD_PATH and RESPONSE_XSD_PATH will be parametrized later)
        connectionProperties = new ConnectionProperties();
        connectionProperties.put(HttpConstants.ENDPOINT, "/test");
        connectionProperties.put(HttpConstants.RESPONSE_CODE, "200");
        connectionProperties.put(PropertyConstants.Soap.WSDL_PATH, wsdlPath.toString());
        connectionProperties.put(PropertyConstants.Soap.WSDL_CONTAINS_XSD, "No");
        connectionProperties.put(PropertyConstants.Soap.REQUEST_XSD_PATH, xsdPath.toString());
        connectionProperties.put(PropertyConstants.Soap.RESPONSE_XSD_PATH, xsdPath.toString());

        // 4. Create StorableDescriptor
        descriptor = mock(StorableDescriptor.class);
        when(descriptor.getProjectUuid()).thenReturn(projectUuid);
        when(descriptor.getProjectId()).thenReturn(BigInteger.ONE);
        when(descriptor.getId()).thenReturn(UUID.randomUUID().toString());

        // 5. Create trigger (will be re-created in parametrized tests)
        trigger = new SoapOverHttpTrigger(descriptor, connectionProperties);

        // 6. Create CamelContext
        camelContext = new DefaultCamelContext();

        // 7. Initialize CXF Bus
        BusFactory.getDefaultBus();
    }

    @AfterEach
    void TearDown() {
        if (camelContext != null && camelContext.isStarted()) {
            camelContext.stop();
        }
    }

    /**
     * Parametrized test to check SOAP requests processing
     * with XSD validation and without it
     */
    @ParameterizedTest(name = "XSD validation: {0}, valid: {1}")
    @MethodSource("provideTestCases")
    void testProcessSoapRequest(boolean xsdValidationEnabled, boolean isValidMessage, String expectedResult)
            throws Exception {
        // Given - create ConnectionProperties with specific XSD configuration
        ConnectionProperties testProps = new ConnectionProperties(connectionProperties);
        if (xsdValidationEnabled) {
            testProps.put(PropertyConstants.Soap.REQUEST_XSD_PATH, xsdPath.toString());
            testProps.put(PropertyConstants.Soap.RESPONSE_XSD_PATH, xsdPath.toString());
        } else {
            testProps.remove(PropertyConstants.Soap.REQUEST_XSD_PATH);
            testProps.remove(PropertyConstants.Soap.RESPONSE_XSD_PATH);
        }

        // Create trigger with test configuration
        trigger = new SoapOverHttpTrigger(descriptor, testProps);

        // Create and start route
        var routeBuilder = createTestRoute(testProps);
        camelContext.addRoutes(routeBuilder);
        camelContext.start();

        String message = isValidMessage ? VALID_SOAP_REQUEST : INVALID_SOAP_REQUEST;

        // When
        DefaultExchange exchange = sendAndProcessMessage(message);

        // Then - check result depending on expectations
        if ("SUCCESS".equals(expectedResult)) {
            assertNull(exchange.getException());
            String body = exchange.getIn().getBody(String.class);
            assertNotNull(body);
            assertTrue(body.contains("GetUserRequest"));
        } else if ("FAILURE".equals(expectedResult)) {
            assertNotNull(exchange.getException());
            assertInstanceOf(IllegalArgumentException.class, exchange.getException());
            String error = exchange.getException().toString();
            assertTrue(error.contains("SAXParseException")
                    || error.contains("'not_an_integer' is not a valid value for 'integer'"));
        } else if ("SKIPPED".equals(expectedResult)) {
            // Once XSD validation is turned off, even invalid message passes through
            assertNull(exchange.getException());
        }
    }

    /**
     * Data Source for parametrized test
     */
    private static Stream<Arguments> provideTestCases() {
        return Stream.of(
                // XSD ON, Valid message -> SUCCESS
                Arguments.of(true, true, "SUCCESS"),
                // XSD ON, Invalid message -> FAILURE
                Arguments.of(true, false, "FAILURE"),
                // XSD OFF, Invalid message -> SKIPPED (validation skipped)
                Arguments.of(false, false, "SKIPPED"),
                // XSD OFF, Valid message -> SKIPPED (validation skipped)
                Arguments.of(false, true, "SKIPPED")
        );
    }

    private RoutesBuilder createTestRoute(ConnectionProperties props) {
        SoapOverHttpHelper.prepareBusContext(this);
        return new ItfAbstractRouteBuilder() {
            public void configure() {
                UUID projectUuid = trigger.getTriggerConfigurationDescriptor().getProjectUuid();
                BigInteger projectId = trigger.getTriggerConfigurationDescriptor().getProjectId();
                String currentEndPoint = Objects.toString(props.get(HttpConstants.ENDPOINT));
                String wsdlPath = trigger.getWsdlPath();
                CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);
                RouteDefinition routeFrom = from(cxfEndpoint);
                routeFrom.onException(Throwable.class).continued(false);
                routeFrom.process(exchange -> {
                    String sessionId = UUID.randomUUID().toString();
                    OffsetDateTime started = OffsetDateTime.now();
                    trigger.clearOutFilter((CxfEndpoint) exchange.getFromEndpoint());
                    trigger.addClientAddressInHeader(exchange);
                    String stringBody = trigger.getStringBody(exchange.getIn());
                    trigger.validate(exchange.getIn(), stringBody, PropertyConstants.Soap.REQUEST_XSD_PATH);
                    exchange.getIn().setBody(stringBody);

                    /*
                        Validation by XSD (if turned on) is completed.
                        Now is the time for fast stub (fast stub logic is removed from the test
                     */

                    org.qubership.automation.itf.core.model.jpa.message.Message message = prepareIncomingMessage(
                            exchange,
                            "org.qubership.automation.itf.transport.soap.http.inbound.SOAPOverHTTPInboundTransport",
                            props,
                            trigger.getTriggerConfigurationDescriptor(),
                            sessionId);

                        org.qubership.automation.itf.core.model.jpa.message.Message responseMessage = setResponseMessage(exchange, sessionId);
                        if (exchange.getException() == null) {
                            try {
                                // Commented because response is always set to empty currently
                                //trigger.validate(exchange.getOut(), PropertyConstants.Soap.RESPONSE_XSD_PATH);
                            } catch (IllegalArgumentException | RuntimeCamelException ex) {
                                if (responseMessage != null) {
                                    responseMessage.setFailedMessage(ex.getMessage());
                                    responseMessage.getHeaders().put("CamelHttpResponseCode", "500");
                                }
                                throw ex;
                            }
                        }
                }).routeId(trigger.getId())
                .routeDescription(projectUuid.toString())
                .group(TransportType.SOAP_OVER_HTTP_INBOUND.name());
                cxfEndpoint.start();
            }

            @Override
            public Map<String, Object> getAdditionalProperties(Exchange exchange) {
                return new HashMap<>();
            }

            @Override
            public List<String> getExcludeHeadersList() {
                return Arrays.asList("CamelCxfMessage", "org.apache.cxf.headers.Header.list");
            }
        };
    }

    private org.qubership.automation.itf.core.model.jpa.message.Message setResponseMessage(
            Exchange exchange, String sessionId) {
        return new org.qubership.automation.itf.core.model.jpa.message.Message("");
    }

    DefaultExchange sendAndProcessMessage(String message) throws Exception {
        ProducerTemplate localProducerTemplate = camelContext.createProducerTemplate();

        // Create Exchange with the message and call processor directly
        // Get processor from the route
        var routeDefinition = camelContext.getRoute(trigger.getId());
        var processor = routeDefinition.getProcessor(); // this is our process(exchange -> {...})

        // Create Exchange
        // And get from-endpoint (to perform validation)
        String wsdlPath = connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH);
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        DefaultExchange exchange = DefaultExchange.newFromEndpoint(cxfEndpoint);
        exchange.getIn().setBody(message);

        // addClientAddressInHeader requires CamelCxfMessage header.
        // Create it manually, because we don't go via real CXF
        org.apache.cxf.binding.soap.SoapMessage soapMessage = mock(org.apache.cxf.binding.soap.SoapMessage.class);
        jakarta.servlet.http.HttpServletRequest servletRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getRemoteHost()).thenReturn("localhost");
        when(servletRequest.getRemotePort()).thenReturn(8080);
        when(servletRequest.getLocalAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getLocalName()).thenReturn("localhost");
        when(servletRequest.getLocalPort()).thenReturn(8080);
        when(soapMessage.get("HTTP.REQUEST")).thenReturn(servletRequest);
        exchange.getIn().setHeader("CamelCxfMessage", soapMessage);

        // When - call processor directly
        processor.process(exchange);
        return exchange;
    }

    /**
     * Test: Validate message w/o routing.
     * Test only validateRequest method.
     */
    @Test
    void testValidateRequest_Directly() {
        // Given
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(
                connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH));

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(VALID_SOAP_REQUEST);

        // When - call validateRequest directly
        trigger.validateRequest(exchange);

        // Then - validation is successful
        assertNull(exchange.getException());
        String body = exchange.getIn().getBody(String.class);
        assertEquals(VALID_SOAP_REQUEST, body);
    }

    /**
     * Test: Check getStringBody with different types
     */
    @Test
    void testGetStringBody() {
        Message inMessage = new DefaultMessage(camelContext);

        // String
        inMessage.setBody(VALID_SOAP_REQUEST);
        String result = trigger.getStringBody(inMessage);
        assertEquals(VALID_SOAP_REQUEST, result);

        // ByteArrayInputStream
        ByteArrayInputStream bais = new ByteArrayInputStream(
                VALID_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));
        inMessage.setBody(bais);
        result = trigger.getStringBody(inMessage);
        assertEquals(VALID_SOAP_REQUEST, result);
    }

    /**
     * Test: Validation is skipped in case XSD isn't configured
     */
    @Test
    void testValidateRequest_NoXsd_SkipsValidation() {
        // Given - remove XSD property
        connectionProperties.remove(PropertyConstants.Soap.REQUEST_XSD_PATH);
        trigger = new SoapOverHttpTrigger(descriptor, connectionProperties);

        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(
                connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH));

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(INVALID_SOAP_REQUEST); // invalid message

        // When
        trigger.validateRequest(exchange);

        // Then - Validation is skipped, no exceptions
        assertNull(exchange.getException());
    }

}

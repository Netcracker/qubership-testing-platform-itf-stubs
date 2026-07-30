package org.qubership.automation.itf.trigger.soap.http.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.constants.PropertyConstants;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.trigger.http.HttpConstants;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tests to check full CXF-processing of SOAP requests.
 * They check:
 * 1. Parsing of WSDL
 * 2. Registering and applying of interceptors
 * 3. XSD validation of messages
 * 4. Valid and invalid messages processing
 */
class SoapOverHttpTriggerCxfProcessingTest {

    @TempDir
    Path tempDir;

    private SoapOverHttpTrigger trigger;
    private CamelContext camelContext;
    private ConnectionProperties connectionProperties;
    private StorableDescriptor descriptor;

    private static final UUID projectUuid = UUID.randomUUID();

    // Test WSDL with embedded XSD
    private static final String TEST_WSDL =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                                      xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                                      xmlns:tns="http://test.example.com/"
                                      xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                                      targetNamespace="http://test.example.com/">
                        <wsdl:types>
                            <xsd:schema targetNamespace="http://test.example.com/"
                                        elementFormDefault="qualified">
                                <xsd:element name="GetUserRequest">
                                    <xsd:complexType>
                                        <xsd:sequence>
                                            <xsd:element name="userId" type="xsd:int"/>
                                            <xsd:element name="userName" type="xsd:string" minOccurs="0"/>
                                        </xsd:sequence>
                                    </xsd:complexType>
                                </xsd:element>
                                <xsd:element name="GetUserResponse">
                                    <xsd:complexType>
                                        <xsd:sequence>
                                            <xsd:element name="userName" type="xsd:string"/>
                                            <xsd:element name="userId" type="xsd:int"/>
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

    // Valid SOAP message (it corresponds XSD)
    private static final String VALID_SOAP_REQUEST =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:tns="http://test.example.com/">
                        <soap:Body>
                            <tns:GetUserRequest>
                                <tns:userId>123</tns:userId>
                                <tns:userName>John Doe</tns:userName>
                            </tns:GetUserRequest>
                        </soap:Body>
                    </soap:Envelope>""";

    // Invalid SOAP message (userId should be int, but it's String here)
    private static final String INVALID_SOAP_REQUEST =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:tns="http://test.example.com/">
                        <soap:Body>
                            <tns:GetUserRequest>
                                <tns:userId>not_an_integer</tns:userId>
                                <tns:userName>John Doe</tns:userName>
                            </tns:GetUserRequest>
                        </soap:Body>
                    </soap:Envelope>""";

    // Invalid SOAP message (mandatory userId element is missed)
    private static final String INVALID_SOAP_REQUEST_MISSING_ELEMENT =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                                   xmlns:tns="http://test.example.com/">
                        <soap:Body>
                            <tns:GetUserRequest>
                                <tns:userName>John Doe</tns:userName>
                            </tns:GetUserRequest>
                        </soap:Body>
                    </soap:Envelope>""";

    @BeforeEach
    void setUp() throws Exception {
        // 1. Create WSDL file
        Path wsdlPath = tempDir.resolve("test.wsdl");
        Files.writeString(wsdlPath, TEST_WSDL);

        // 2. Create XSD file
        Path xsdPath = tempDir.resolve("test.xsd");
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
                                        <xs:element name="userName" type="xs:string" minOccurs="0"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="GetUserResponse">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="userName" type="xs:string"/>
                                        <xs:element name="userId" type="xs:int"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:schema>""";
        Files.writeString(xsdPath, xsdContent);

        // 3. Configure ConnectionProperties
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

        // 5. Create trigger
        trigger = new SoapOverHttpTrigger(descriptor, connectionProperties);

        // 6. Create CamelContext
        camelContext = new DefaultCamelContext();

        // 7. Initialize CXF Bus
        BusFactory.getDefaultBus();
    }

    /**
     * Test: Valid SOAP message is processed via CXF successfully
     * Actually, CXF parsing isn't tested (because it's not invoked)
     */
    @Test
    void testValidSoapMessage_SuccessfullyProcessed() {
        // Given
        String wsdlPath = connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH);
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        DefaultExchange exchange = DefaultExchange.newFromEndpoint(cxfEndpoint);
        exchange.getIn().setBody(VALID_SOAP_REQUEST);

        // Configure headers for addClientAddressInHeader
        SoapMessage soapMessage = mock(SoapMessage.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getRemoteHost()).thenReturn("localhost");
        when(servletRequest.getRemotePort()).thenReturn(8080);
        when(servletRequest.getLocalAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getLocalName()).thenReturn("localhost");
        when(servletRequest.getLocalPort()).thenReturn(8080);
        when(soapMessage.get("HTTP.REQUEST")).thenReturn(servletRequest);
        exchange.getIn().setHeader("CamelCxfMessage", soapMessage);

        // When - XSD validation
        trigger.validateRequest(exchange);

        // Then - validation is successful
        assertNull(exchange.getException());
        String body = exchange.getIn().getBody(String.class);
        assertNotNull(body);
        assertTrue(body.contains("GetUserRequest"));
        assertTrue(body.contains("123"));
        assertTrue(body.contains("John Doe"));
    }

    /**
     * Test: Invalid SOAP message (incorrect field type) - validation with error
     */
    @Test
    void testInvalidSoapMessage_WrongType_ThrowsValidationError() {
        // Given
        String wsdlPath = connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH);
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        DefaultExchange exchange = DefaultExchange.newFromEndpoint(cxfEndpoint);
        exchange.getIn().setBody(INVALID_SOAP_REQUEST);

        SoapMessage soapMessage = mock(SoapMessage.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getRemoteHost()).thenReturn("localhost");
        when(servletRequest.getRemotePort()).thenReturn(8080);
        when(servletRequest.getLocalAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getLocalName()).thenReturn("localhost");
        when(servletRequest.getLocalPort()).thenReturn(8080);
        when(soapMessage.get("HTTP.REQUEST")).thenReturn(servletRequest);
        exchange.getIn().setHeader("CamelCxfMessage", soapMessage);

        // When & Then - validation should throw exception
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            trigger.validateRequest(exchange);
        });

        // Check error
        String error = illegalArgumentException.toString();
        assertTrue(error.contains("SAXParseException")
                || error.contains("'not_an_integer' is not a valid value for 'integer'"));
    }

    /**
     * Test: Invalid SOAP message (mandatory field is missed) - validation with error
     */
    @Test
    void testInvalidSoapMessage_MissingRequiredElement_ThrowsValidationError() {
        // Given
        String wsdlPath = connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH);
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        DefaultExchange exchange = DefaultExchange.newFromEndpoint(cxfEndpoint);
        exchange.getIn().setBody(INVALID_SOAP_REQUEST_MISSING_ELEMENT);

        SoapMessage soapMessage = mock(SoapMessage.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getRemoteHost()).thenReturn("localhost");
        when(servletRequest.getRemotePort()).thenReturn(8080);
        when(servletRequest.getLocalAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getLocalName()).thenReturn("localhost");
        when(servletRequest.getLocalPort()).thenReturn(8080);
        when(soapMessage.get("HTTP.REQUEST")).thenReturn(servletRequest);
        exchange.getIn().setHeader("CamelCxfMessage", soapMessage);

        // When & Then - validation should throw exception
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            trigger.validateRequest(exchange);
        });

        String error = illegalArgumentException.toString();
        assertTrue(error.contains("SAXParseException")
                || error.contains("Invalid content was found starting with element '{\"http://test.example.com/\":userName}'. One of '{\"http://test.example.com/\":userId}' is expected."));    }

    /**
     * Test: Check that ItfSoapMessageAbstractPhaseInterceptor
     * extracts message correctly.
     */
    @Disabled("It doesn't test CXF interceptors chain execution. To be rewritten or removed.")
    @Test
    void testFullProcessing_WithInterceptors() {
        // Given
        String wsdlPath = connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH);
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        // Create PlainSoapMessage
        SoapOverHttpTrigger.PlainSoapMessage plainSoapMessage = trigger.new PlainSoapMessage();

        // Get interceptor from CxfEndpoint and cast it to the correct type
        SoapOverHttpTrigger.ItfSoapMessageAbstractPhaseInterceptor interceptor =
                (SoapOverHttpTrigger.ItfSoapMessageAbstractPhaseInterceptor) cxfEndpoint
                        .getInInterceptors()
                        .stream()
                        .filter(
                                i -> i instanceof SoapOverHttpTrigger.ItfSoapMessageAbstractPhaseInterceptor)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("ItfSoapMessageAbstractPhaseInterceptor not found"));

        // Create SoapMessage with valid request
        SoapMessage soapMessage = mock(SoapMessage.class);
        ByteArrayInputStream bais = new ByteArrayInputStream(
                VALID_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));
        when(soapMessage.getContent(java.io.InputStream.class)).thenReturn(bais);

        Map<String, List<String>> protocolHeaders = new HashMap<>();
        protocolHeaders.put("Content-Type", List.of("text/xml; charset=UTF-8"));
        when(soapMessage.get(Message.PROTOCOL_HEADERS)).thenReturn(protocolHeaders);

        // When - invoke interceptor
        interceptor.handleMessage(soapMessage);

        // Then - check that message is extracted correctly
        assertEquals(VALID_SOAP_REQUEST, plainSoapMessage.getText());
        assertNotNull(plainSoapMessage.getHeaders());
        assertEquals("text/xml; charset=UTF-8", plainSoapMessage.getHeaders().get("Content-Type"));
    }

    /**
     * Test: Check that ItfMustUnderstandInterceptor ignores mustUnderstand parameters.
     */
    @Test
    void testMustUnderstandInterceptor_WithRealSoapMessage() {
        // Given
        String wsdlPath = connectionProperties.obtain(PropertyConstants.Soap.WSDL_PATH);
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        // Get interceptor from CxfEndpoint and cast it to the correct type
        SoapOverHttpTrigger.ItfMustUnderstandInterceptor interceptor =
                (SoapOverHttpTrigger.ItfMustUnderstandInterceptor) cxfEndpoint
                        .getInInterceptors()
                        .stream()
                        .filter(
                                i -> i instanceof SoapOverHttpTrigger.ItfMustUnderstandInterceptor)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("ItfMustUnderstandInterceptor not found"));

        // Create SoapMessage with headers
        SoapMessage soapMessage = mock(SoapMessage.class);

        List<Header> headers = new ArrayList<>();
        SoapHeader header1 = mock(SoapHeader.class);
        SoapHeader header2 = mock(SoapHeader.class);
        headers.add(header1);
        headers.add(header2);
        when(soapMessage.getHeaders()).thenReturn(headers);

        // When
        interceptor.handleMessage(soapMessage);

        // Then
        verify(header1).setMustUnderstand(false);
        verify(header2).setMustUnderstand(false);
    }

    /**
     * Test: Check that, in case XSD validation is turned off, invalid message is processed w/o exception
     */
    @Test
    void testInvalidSoapMessage_WithXsdDisabled_SkipsValidation() {
        // Given - create configuration w/o XSD
        ConnectionProperties noXsdProps = new ConnectionProperties(connectionProperties);
        noXsdProps.remove(PropertyConstants.Soap.REQUEST_XSD_PATH);
        noXsdProps.remove(PropertyConstants.Soap.RESPONSE_XSD_PATH);

        SoapOverHttpTrigger triggerNoXsd = new SoapOverHttpTrigger(descriptor, noXsdProps);

        String wsdlPath = noXsdProps.obtain(PropertyConstants.Soap.WSDL_PATH);
        CxfEndpoint cxfEndpoint = triggerNoXsd.createCxfEndpoint(wsdlPath);

        DefaultExchange exchange = DefaultExchange.newFromEndpoint(cxfEndpoint);
        exchange.getIn().setBody(INVALID_SOAP_REQUEST);

        // When - perform validation
        triggerNoXsd.validateRequest(exchange);

        // Then - validation is skipped, no exceptions
        assertNull(exchange.getException());
        String body = exchange.getIn().getBody(String.class);
        assertEquals(INVALID_SOAP_REQUEST, body);
    }
}
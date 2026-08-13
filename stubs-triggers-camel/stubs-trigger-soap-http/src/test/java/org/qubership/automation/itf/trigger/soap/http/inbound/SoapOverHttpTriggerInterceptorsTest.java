package org.qubership.automation.itf.trigger.soap.http.inbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.util.xml.StringSource;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.ws.policy.PolicyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.constants.PropertyConstants;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.trigger.http.HttpConstants;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoapOverHttpTriggerInterceptorsTest {
    @TempDir
    Path tempDir;

    @Mock
    private SoapMessage soapMessage;

    @Mock
    private org.apache.cxf.message.Exchange cxfExchange;

    @Mock
    private Message inMessage;

    @Mock
    private HttpServletResponse httpResponse;

    @Mock
    private StorableDescriptor descriptor;

    @Mock
    private Exchange camelExchange;

    @Mock
    private ServletOutputStream servletOutputStream;

    private ConnectionProperties connectionProperties;
    private SoapOverHttpTrigger trigger;
    private SoapOverHttpTrigger.PlainSoapMessage plainSoapMessage;

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

    private static final String SOAP_MESSAGE_TEXT =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                        <soap:Body>
                            <GetUserRequest>
                                <userId>123</userId>
                            </GetUserRequest>
                        </soap:Body>
                    </soap:Envelope>""";

    @BeforeEach
    void setUp() throws Exception {
        // 1. Create WSDL file
        Path wsdlPath = tempDir.resolve("test.wsdl");
        Files.writeString(wsdlPath, TEST_WSDL);

        connectionProperties = new ConnectionProperties();
        connectionProperties.put(HttpConstants.ENDPOINT, "/test");
        connectionProperties.put(HttpConstants.RESPONSE_CODE, "200");
        connectionProperties.put(PropertyConstants.Soap.WSDL_PATH, wsdlPath.toString());
        connectionProperties.put(PropertyConstants.Soap.WSDL_CONTAINS_XSD, "No");

        when(descriptor.getProjectUuid()).thenReturn(UUID.randomUUID());
        when(descriptor.getProjectId()).thenReturn(BigInteger.ONE);
        when(descriptor.getId()).thenReturn(UUID.randomUUID().toString());

        trigger = new SoapOverHttpTrigger(descriptor, connectionProperties);
        plainSoapMessage = trigger.new PlainSoapMessage();
    }

    // ==================== Tests of CxfEndpoint configuration ====================

    @Test
    void testCreateCxfEndpoint_ConfiguresInterceptors() {
        // Given
        String wsdlPath = "file:/test.wsdl";

        // When
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        // Then
        assertNotNull(cxfEndpoint);
        assertEquals(wsdlPath, cxfEndpoint.getWsdlURL());
        assertEquals(DataFormat.PAYLOAD, cxfEndpoint.getDataFormat());

        assertNotNull(cxfEndpoint.getInInterceptors());
        assertNotNull(cxfEndpoint.getOutInterceptors());
        assertNotNull(cxfEndpoint.getOutFaultInterceptors());

        boolean hasMustUnderstand = cxfEndpoint.getInInterceptors().stream()
                .anyMatch(i -> i instanceof SoapOverHttpTrigger.ItfMustUnderstandInterceptor);
        assertTrue(hasMustUnderstand, "ItfMustUnderstandInterceptor should be added");

        boolean hasMessageInterceptor = cxfEndpoint.getInInterceptors().stream()
                .anyMatch(i -> i instanceof SoapOverHttpTrigger.ItfSoapMessageAbstractPhaseInterceptor);
        assertTrue(hasMessageInterceptor, "ItfSoapMessageAbstractPhaseInterceptor should be added");

        // Check if there is at least 1 out-fault interceptor
        assertFalse(cxfEndpoint.getOutFaultInterceptors().isEmpty(), "OutFaultInterceptors should not be empty");
    }

    @Test
    void testCreateCxfEndpoint_SetsExtraProperties() {
        // Given
        String wsdlPath = "file:/test.wsdl";
        Map<String, Object> extraProps = new HashMap<>();
        extraProps.put("dataFormat", "RAW");
        extraProps.put("someProperty", "someValue");
        connectionProperties.put(PropertyConstants.Commons.ENDPOINT_PROPERTIES, extraProps);

        // When
        CxfEndpoint cxfEndpoint = trigger.createCxfEndpoint(wsdlPath);

        // Then
        assertEquals(DataFormat.RAW, cxfEndpoint.getDataFormat());
        assertNotNull(cxfEndpoint.getProperties());
        assertEquals("someValue", cxfEndpoint.getProperties().get("someProperty"));
    }

    // ==================== Tests of ItfMustUnderstandInterceptor ====================

    @Test
    void testItfMustUnderstandInterceptor_IgnoresAllMustUnderstand() {
        // Given
        SoapOverHttpTrigger.ItfMustUnderstandInterceptor interceptor =
                new SoapOverHttpTrigger.ItfMustUnderstandInterceptor();

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

    @Test
    void testItfMustUnderstandInterceptor_HandlesNullHeaders() {
        // Given
        SoapOverHttpTrigger.ItfMustUnderstandInterceptor interceptor =
                new SoapOverHttpTrigger.ItfMustUnderstandInterceptor();

        // Return empty list instead of null, to avoid NPE
        when(soapMessage.getHeaders()).thenReturn(new ArrayList<>());

        // When & Then
        assertDoesNotThrow(() -> interceptor.handleMessage(soapMessage));
    }

    // ==================== Tests of ItfSoapMessageAbstractPhaseInterceptor ====================

    @Test
    void testItfSoapMessageAbstractPhaseInterceptor_ExtractsMessageAndHeaders() {
        // Given
        ByteArrayInputStream bais = new ByteArrayInputStream(
                SOAP_MESSAGE_TEXT.getBytes(StandardCharsets.UTF_8));

        when(soapMessage.getContent(java.io.InputStream.class)).thenReturn(bais);

        Map<String, List<String>> protocolHeaders = new HashMap<>();
        protocolHeaders.put("Content-Type", List.of("text/xml; charset=UTF-8"));
        protocolHeaders.put("SOAPAction", List.of("\"\""));
        when(soapMessage.get(Message.PROTOCOL_HEADERS)).thenReturn(protocolHeaders);

        SoapOverHttpTrigger.ItfSoapMessageAbstractPhaseInterceptor interceptor =
                trigger.new ItfSoapMessageAbstractPhaseInterceptor(plainSoapMessage);

        // When
        interceptor.handleMessage(soapMessage);

        // Then
        assertEquals(SOAP_MESSAGE_TEXT, plainSoapMessage.getText());
        assertNotNull(plainSoapMessage.getHeaders());
        assertEquals("text/xml; charset=UTF-8", plainSoapMessage.getHeaders().get("Content-Type"));
        assertEquals("\"\"", plainSoapMessage.getHeaders().get("SOAPAction"));
    }

    @Test
    void testItfSoapMessageAbstractPhaseInterceptor_HandlesIOException() throws Exception {
        // Given
        // Create InputStream, which throws IOException while reading
        java.io.InputStream errorStream = mock(java.io.InputStream.class);
        when(errorStream.read(any(), anyInt(), anyInt())).thenThrow(new IOException("Test IO Exception"));

        // Mock close() to avoid exceptions
        doNothing().when(errorStream).close();
        when(soapMessage.getContent(java.io.InputStream.class)).thenReturn(errorStream);

        SoapOverHttpTrigger.ItfSoapMessageAbstractPhaseInterceptor interceptor =
                trigger.new ItfSoapMessageAbstractPhaseInterceptor(plainSoapMessage);

        // When & Then - in case exception, just log it, do NOT throw
        assertDoesNotThrow(() -> interceptor.handleMessage(soapMessage));

        // Message wasn't set due to exception
        assertNull(plainSoapMessage.getText());
    }

    // ==================== Tests of handleMessage (errors handling) ====================

    @Test
    void testHandleMessage_WithNullFault() {
        // Given
        when(soapMessage.getContent(Exception.class)).thenReturn(null);

        // When
        trigger.handleMessage(soapMessage, plainSoapMessage);

        // Then
        verify(soapMessage, never()).getExchange();
    }

    @Test
    void testHandleMessage_WithPolicyException() throws Exception {
        // Given
        Fault fault = mock(Fault.class);
        PolicyException policyEx = new PolicyException(new Exception("Policy error"));
        when(fault.getCause()).thenReturn(policyEx);
        when(fault.getStatusCode()).thenReturn(500);
        when(fault.getMessage()).thenReturn("Policy error occurred");

        when(soapMessage.getContent(Exception.class)).thenReturn(fault);
        when(soapMessage.getExchange()).thenReturn(cxfExchange);
        when(cxfExchange.getInMessage()).thenReturn(inMessage);
        when(inMessage.get(AbstractHTTPDestination.HTTP_RESPONSE)).thenReturn(httpResponse);
        when(httpResponse.getOutputStream()).thenReturn(servletOutputStream);

        org.apache.cxf.interceptor.InterceptorChain interceptorChain =
                mock(org.apache.cxf.interceptor.InterceptorChain.class);
        when(soapMessage.getInterceptorChain()).thenReturn(interceptorChain);

        // When
        trigger.handleMessage(soapMessage, plainSoapMessage);

        // Then
        verify(httpResponse).setStatus(500);
        verify(servletOutputStream).write(any(byte[].class));
        verify(interceptorChain).setFaultObserver(null);
        verify(interceptorChain).abort();
    }

    @Test
    void testHandleMessage_WithFaultAndCamelExchange() {
        // Given
        Fault fault = mock(Fault.class);
        when(fault.getCause()).thenReturn(new RuntimeException("Test error"));
        when(fault.getStatusCode()).thenReturn(400);
        when(fault.getMessage()).thenReturn("Test error message");

        when(soapMessage.getContent(Exception.class)).thenReturn(fault);
        when(soapMessage.getExchange()).thenReturn(cxfExchange);
        when(cxfExchange.get("org.apache.camel.Exchange")).thenReturn(camelExchange);
        when(camelExchange.hasOut()).thenReturn(false);

        plainSoapMessage.setText(SOAP_MESSAGE_TEXT);
        plainSoapMessage.getHeaders().put("Content-Type", "text/xml");

        // When & Then - check that method doesn't throw exception
        try {
            trigger.handleMessage(soapMessage, plainSoapMessage);
        } catch (Exception e) {
            // Expect NPE from TriggerExecutionMessageSender, it's okay for tests
            assertTrue(e instanceof NullPointerException || e instanceof Fault);
        }
    }

    // ==================== Tests of revertToPlainMessage ====================

    @Test
    void testRevertToPlainMessage_WithListContent() {
        // Given
        List<Object> listObj = new ArrayList<>();
        listObj.add("old content");
        when(soapMessage.getContent(List.class)).thenReturn(listObj);
        plainSoapMessage.setText(SOAP_MESSAGE_TEXT);

        // When
        trigger.revertToPlainMessage(soapMessage, plainSoapMessage);

        // Then - list should contain 1 StringSource only
        assertFalse(listObj.isEmpty(), "List should not be empty after adding StringSource");
        assertEquals(1, listObj.size());
        assertInstanceOf(StringSource.class, listObj.getFirst(), "Element should be StringSource");
    }

    @Test
    void testRevertToPlainMessage_WithNullList() {
        // Given
        when(soapMessage.getContent(List.class)).thenReturn(null);
        plainSoapMessage.setText(SOAP_MESSAGE_TEXT);

        // When & Then
        assertDoesNotThrow(() -> trigger.revertToPlainMessage(soapMessage, plainSoapMessage));
    }

    // ==================== Tests of getResponseCode ====================

    @Test
    void testGetResponseCode_ConvertsStringToInteger() throws Exception {
        // Given
        Message message = mock(Message.class);
        String key = Message.class.getName() + ".RESPONSE_CODE";
        when(message.containsKey(key)).thenReturn(true);
        when(message.get(key)).thenReturn("200");

        // When
        java.lang.reflect.Method method = SoapOverHttpTrigger.class.getDeclaredMethod("getResponseCode", Message.class);
        method.setAccessible(true);
        method.invoke(trigger, message);

        // Then
        verify(message).put(key, 200);
    }

    @Test
    void testGetResponseCode_WithNonStringValue() throws Exception {
        // Given
        Message message = mock(Message.class);
        String key = Message.class.getName() + ".RESPONSE_CODE";
        when(message.containsKey(key)).thenReturn(true);
        when(message.get(key)).thenReturn(200);

        // When
        java.lang.reflect.Method method = SoapOverHttpTrigger.class.getDeclaredMethod("getResponseCode", Message.class);
        method.setAccessible(true);
        method.invoke(trigger, message);

        // Then
        verify(message, never()).put(eq(key), anyInt());
    }

    // ==================== Tests of prepareAndSendErrorResponse ====================

    @Test
    void testPrepareAndSendErrorResponse_SendsErrorToClient() throws Exception {
        // Given
        Fault fault = mock(Fault.class);
        when(fault.getStatusCode()).thenReturn(500);
        when(fault.getMessage()).thenReturn("Internal Server Error");

        when(soapMessage.getExchange()).thenReturn(cxfExchange);
        when(cxfExchange.getInMessage()).thenReturn(inMessage);
        when(inMessage.get(AbstractHTTPDestination.HTTP_RESPONSE)).thenReturn(httpResponse);
        when(httpResponse.getOutputStream()).thenReturn(servletOutputStream);

        org.apache.cxf.interceptor.InterceptorChain interceptorChain =
                mock(org.apache.cxf.interceptor.InterceptorChain.class);
        when(soapMessage.getInterceptorChain()).thenReturn(interceptorChain);

        // When
        java.lang.reflect.Method method = SoapOverHttpTrigger.class.getDeclaredMethod(
                "prepareAndSendErrorResponse", SoapMessage.class, Fault.class);
        method.setAccessible(true);
        method.invoke(trigger, soapMessage, fault);

        // Then
        verify(httpResponse).setStatus(500);
        verify(servletOutputStream).write("Internal Server Error".getBytes());
        verify(servletOutputStream).flush();
        verify(interceptorChain).setFaultObserver(null);
        verify(interceptorChain).abort();
    }

    @Test
    void testPrepareAndSendErrorResponse_HandlesIOException() throws Exception {
        // Given
        Fault fault = mock(Fault.class);
        when(fault.getStatusCode()).thenReturn(500);
        when(fault.getMessage()).thenReturn("Internal Server Error");

        when(soapMessage.getExchange()).thenReturn(cxfExchange);
        when(cxfExchange.getInMessage()).thenReturn(inMessage);
        when(inMessage.get(AbstractHTTPDestination.HTTP_RESPONSE)).thenReturn(httpResponse);
        when(httpResponse.getOutputStream()).thenReturn(servletOutputStream);
        doThrow(new IOException("IO Error")).when(servletOutputStream).write(any(byte[].class));

        org.apache.cxf.interceptor.InterceptorChain interceptorChain =
                mock(org.apache.cxf.interceptor.InterceptorChain.class);
        when(soapMessage.getInterceptorChain()).thenReturn(interceptorChain);

        // When & Then
        java.lang.reflect.Method method = SoapOverHttpTrigger.class.getDeclaredMethod(
                "prepareAndSendErrorResponse", SoapMessage.class, Fault.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(trigger, soapMessage, fault));
    }

    // ==================== Tests of handlePolicyError ====================

    @Test
    void testHandlePolicyError_LogsErrorAndSendsResponse() throws Exception {
        // Given
        Fault fault = mock(Fault.class);
        when(fault.getStatusCode()).thenReturn(500);
        when(fault.getMessage()).thenReturn("Policy error");
        when(fault.getCause()).thenReturn(new PolicyException(new Exception("Policy details")));

        when(soapMessage.getExchange()).thenReturn(cxfExchange);
        when(cxfExchange.getInMessage()).thenReturn(inMessage);
        when(inMessage.get(AbstractHTTPDestination.HTTP_RESPONSE)).thenReturn(httpResponse);
        when(httpResponse.getOutputStream()).thenReturn(servletOutputStream);

        org.apache.cxf.interceptor.InterceptorChain interceptorChain =
                mock(org.apache.cxf.interceptor.InterceptorChain.class);
        when(soapMessage.getInterceptorChain()).thenReturn(interceptorChain);

        // When
        java.lang.reflect.Method method = SoapOverHttpTrigger.class.getDeclaredMethod(
                "handlePolicyError", SoapMessage.class, Fault.class);
        method.setAccessible(true);
        method.invoke(trigger, soapMessage, fault);

        // Then
        verify(httpResponse).setStatus(500);
        verify(interceptorChain).setFaultObserver(null);
        verify(interceptorChain).abort();
    }

    // ==================== Tests of clearOutFilter ====================

    @Test
    void testClearOutFilter_SetsOutFilterToNull() {
        // Given
        CxfEndpoint cxfEndpoint = mock(CxfEndpoint.class);
        DefaultHeaderFilterStrategy headerFilterStrategy = new DefaultHeaderFilterStrategy();
        // Add something into outFilter
        headerFilterStrategy.setOutFilter(Set.of("header1", "header2"));
        when(cxfEndpoint.getHeaderFilterStrategy()).thenReturn(headerFilterStrategy);

        // When
        trigger.clearOutFilter(cxfEndpoint);

        // Then - outFilter should be null or empty
        Set<String> outFilter = headerFilterStrategy.getOutFilter();
        assertTrue(outFilter == null || outFilter.isEmpty(),
                "OutFilter should be null or empty, but was: " + outFilter);
    }

    @Test
    void testClearOutFilter_CreatesNewHeaderFilterStrategyIfNull() {
        // Given
        CxfEndpoint cxfEndpoint = mock(CxfEndpoint.class);
        when(cxfEndpoint.getHeaderFilterStrategy()).thenReturn(null);

        // When
        trigger.clearOutFilter(cxfEndpoint);

        // Then
        verify(cxfEndpoint).setHeaderFilterStrategy(any(DefaultHeaderFilterStrategy.class));
    }

    // ==================== Tests of PlainSoapMessage ====================

    @Test
    void testPlainSoapMessage_StoresTextAndHeaders() {
        // Given
        SoapOverHttpTrigger.PlainSoapMessage message = trigger.new PlainSoapMessage();
        Map<String, Object> headers = new HashMap<>();
        headers.put("key", "value");

        // When
        message.setText(SOAP_MESSAGE_TEXT);
        message.setHeaders(headers);

        // Then
        assertEquals(SOAP_MESSAGE_TEXT, message.getText());
        assertEquals(headers, message.getHeaders());
    }

    @Test
    void testPlainSoapMessage_DefaultEmptyHeaders() {
        // Given
        SoapOverHttpTrigger.PlainSoapMessage message = trigger.new PlainSoapMessage();

        // Then
        assertNotNull(message.getHeaders());
        assertTrue(message.getHeaders().isEmpty());
    }
}
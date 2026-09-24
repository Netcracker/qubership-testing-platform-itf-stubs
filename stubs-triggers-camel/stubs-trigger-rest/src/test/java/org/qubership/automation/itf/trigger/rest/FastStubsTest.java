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

package org.qubership.automation.itf.trigger.rest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.http.common.HttpMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfig;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfigsHolder;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.core.stub.fast.TransportConfig;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.services.CoreServices;
import org.qubership.automation.itf.core.util.services.projectsettings.IProjectSettingsService;
import org.qubership.automation.itf.integration.config.jms.connection.StubsIntegrationConfig;
import org.qubership.automation.itf.integration.config.jms.template.ExecutorJmsTemplateConfiguration;
import org.qubership.automation.itf.integration.config.jms.template.IntegrationJmsTemplateConfiguration;
import org.qubership.automation.itf.integration.config.jms.template.instance.MultiTenantJmsTemplateInstancesConfiguration;
import org.qubership.automation.itf.trigger.FastStubsConfiguration;
import org.qubership.automation.itf.trigger.rest.inbound.RestInboundTrigger;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        FastStubsConfiguration.class,
        MultiTenantJmsTemplateInstancesConfiguration.class,
        StubsIntegrationConfig.class,
        IntegrationJmsTemplateConfiguration.class,
        ExecutorJmsTemplateConfiguration.class,
        FastStubsTest.NoopProjectSettingsServiceConfig.class,
        CoreServices.class})
@TestPropertySource(locations = "classpath:application.properties")
class FastStubsTest {

    /**
     * Provides the {@code projectSettingsService} bean {@link CoreServices} requires.
     *
     * <p>atp-itf-core's own default {@code ProjectSettingsService} bean throws on every call,
     * expecting the consuming application to supply a real one; this repository's real one lives
     * in stubs-aggregator, which stubs-trigger-rest cannot depend on without a cycle. This bean
     * always returns the caller-supplied default instead.</p>
     */
    @Configuration
    static class NoopProjectSettingsServiceConfig {
        @Bean(name = "projectSettingsService")
        IProjectSettingsService projectSettingsService() {
            return new IProjectSettingsService() {
                @Override
                public String get(Object projectId, String key, String defaultValue) {
                    return defaultValue;
                }

                @Override
                public Integer getInt(Object projectId, String key, int defaultValue) {
                    return defaultValue;
                }

                @Override
                public Map<String, String> getByPrefix(Object projectId, String prefix, boolean stripPrefix) {
                    return Collections.emptyMap();
                }

                @Override
                public Map<String, String> getAll(Object projectId) {
                    return Collections.emptyMap();
                }
            };
        }
    }

    static RestInboundTrigger restInboundTrigger;
    static final UUID projectUuid = UUID.fromString("a71d2db4-d8e4-412c-ad47-d021ba2d9c6c");
    static final BigInteger projectId = BigInteger.valueOf(9876543210L);
    static final String configurationFileName = "fast_stub_config_test.json";
    static final String transportType = StubEndpointConfig.TransportTypes.REST.name();

    @BeforeAll
    static void prepareConfigHolder() throws Exception {
        File file = new File(FastStubsTest.class.getClassLoader().getResource(configurationFileName).getFile());
        StorableDescriptor triggerConfigurationDescriptor =
                new StorableDescriptor(1234567890, "Test REST Trigger", projectUuid, projectId);
        restInboundTrigger = spy(new RestInboundTrigger(triggerConfigurationDescriptor, null));
        initTestConfigHolder(file);
    }

    @Test
    void checkFastResponsePreparingWithParsingRulesAndVelocity() throws Exception {
        Exchange exchange = mock(Exchange.class);
        Message exchangeIn = mock(Message.class);
        String incomingBody = "text_to_past=SUCCESS header1=header1 header1=header11 header2=header2 response_code=202";

        Map<String, Object> headers = new HashMap<>();
        headers.put("header1", "header_value1");
        when(exchange.getIn()).thenReturn(exchangeIn);
        when(exchangeIn.getHeaders()).thenReturn(headers);

        Message exchangeOut = mock(HttpMessage.class);
        doReturn("text/plain").when(exchangeOut).getHeader(Exchange.CONTENT_TYPE);
        doCallRealMethod().when(exchangeOut).setBody(any(Object.class));
        doCallRealMethod().when(exchangeOut).getBody();
        doCallRealMethod().when(exchangeOut).getHeaders();

        when(exchange.getOut()).thenReturn(exchangeOut);

        org.qubership.automation.itf.core.model.jpa.message.Message message =
                new org.qubership.automation.itf.core.model.jpa.message.Message(incomingBody);
        message.convertAndSetHeaders(exchangeIn.getHeaders(), null);

        String testEndPoint = "/fast/rest/inbound";
        StubEndpointConfig cfg = FastResponseConfigsHolder.INSTANCE.getConfig(
                String.valueOf(projectUuid),
                transportType,
                testEndPoint);
        assertTrue(cfg != null,
                String.format(
                        "StubEndpointConfig not found.\n"
                                + "Configuration file: %s\n"
                                + "Transport type: %s\n"
                                + "Endpoint: %s",
                        configurationFileName, transportType, testEndPoint
                )
        );
        assertTrue(cfg.getParsingRules() != null,
                String.format(
                        "StubEndpointConfig doesn't have parsing rules.\n"
                                + "Configuration file: %s\n"
                                + "Transport type: %s\n"
                                + "Endpoint: %s",
                        configurationFileName, transportType, testEndPoint
                )
        );
        StorableDescriptor triggerDescriptor = new StorableDescriptor();
        restInboundTrigger.prepareFastResponse(exchange, message, cfg, "test_session_id", triggerDescriptor);
        String expectedMessage = "Test OK! SUCCESS dGV4dA==";
        assertTrue(expectedMessage.equals(exchange.getOut().getBody()),
                String.format(
                    "Expected and actual messages are different.\n"
                        + "Expected:\n%s\n"
                        + "Actual:\n%s", expectedMessage, exchange.getOut().getBody())
        );

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("header2", "header2");
        expectedHeaders.put("CamelHttpResponseCode", "202");
        expectedHeaders.put("header1_0", "header1");
        expectedHeaders.put("header1_multiple", "[header1, header11]");
        expectedHeaders.put("header1_1", "header11");
        expectedHeaders.put("Content-Type", "text/plain");
        expectedHeaders.put("header_date","2023-01-06T05:03:00");

        assertTrue(checkHeaders(expectedHeaders, exchange.getOut().getHeaders()),
                String.format(
                        "Actual headers does not contain expected ones.\n"
                                + "Expected:\n%s\n"
                                + "Actual:\n%s", expectedHeaders, exchange.getOut().getHeaders())
        );

        incomingBody = "text_to_past=SUCCESS header1=conditional header1=header11 header2=header2 response_code=202";
        message.setText(incomingBody);
        expectedMessage = "Conditional Response 1";
        restInboundTrigger.prepareFastResponse(exchange, message, cfg, "test_session_id", triggerDescriptor);
        assertTrue(expectedMessage.equals(exchange.getOut().getBody()),
                String.format(
                        "Expected and actual messages are different.\n"
                                + "Expected:\n%s\n"
                                + "Actual:\n%s", expectedMessage, exchange.getOut().getBody())
        );
    }

    boolean checkHeaders(Map<String, String> expected, Map<String, Object> actual) {
        return expected.entrySet().stream()
                .allMatch(a -> actual.get(a.getKey()) != null && actual.get(a.getKey()).equals(a.getValue()));
    }

    private static void initTestConfigHolder(File savedFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        FastResponseConfig fastResponseConfig = objectMapper.readValue(savedFile, FastResponseConfig.class);
        for (TransportConfig transportConfig : fastResponseConfig.getTransportConfigs()) {
            for (StubEndpointConfig config : transportConfig.getEndpoints()) {
                FastResponseConfigsHolder.INSTANCE.putConfig(
                        fastResponseConfig.getProjectUuid(),
                        transportConfig.getTransportType().name().toUpperCase(Locale.getDefault()),
                        config);
            }
        }
    }
}

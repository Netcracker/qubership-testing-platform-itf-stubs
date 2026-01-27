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

package org.qubership.automation.itf;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import au.com.dius.pact.consumer.dsl.PactDslResponse;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.PactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.google.gson.Gson;
import org.qubership.atp.auth.springbootstarter.config.FeignConfiguration;
import org.qubership.automation.itf.conf.OkHttpFeignConfiguration;
import org.qubership.automation.itf.integration.executor.ExecutorFeignClient;
import org.qubership.automation.itf.openapi.executor.dto.EnvironmentSampleDto;
import org.qubership.automation.itf.openapi.executor.dto.ResultDto;
import org.qubership.automation.itf.openapi.executor.dto.TriggerSampleDto;
import org.qubership.automation.itf.openapi.executor.dto.UIUpdateTriggerStatusDto;
import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@EnableFeignClients(clients = {ExecutorFeignClient.class})
@ContextConfiguration(classes = {ExecutorFeignClientPactUnitTest.TestApp.class})
@Import({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, FeignConfiguration.class,
        FeignAutoConfiguration.class, OkHttpFeignConfiguration.class})
@TestPropertySource(properties = {"feign.atp.executor.name=atp-itf-executor", "feign.atp.executor.route=",
                "feign.atp.executor.url=http://localhost:8888", "feign.httpclient.enabled=false"})
@Slf4j
public class ExecutorFeignClientPactUnitTest {

    @Configuration
    public static class TestApp {
    }

    @Rule
    public PactProviderRule mockProvider
            = new PactProviderRule("atp-itf-executor", "localhost", 8888, this);
    @Autowired
    ExecutorFeignClient executorFeignClient;

    private BigInteger triggerId = new BigInteger("9167234930111872000");
    private BigInteger environmentId = new BigInteger("9167234930111872001");

    @Test
    @PactVerification()
    public void allPass() {
        testGetAllActiveTriggers();
        testGetTriggerById();
        testGetTriggersByEnvironment();
        testUpdateTriggerStatus();
    }

    public void testGetAllActiveTriggers() {
        ResponseEntity<List<TriggerSampleDto>> result1 = executorFeignClient.getAllActiveTriggers();
        Assert.assertEquals(200, result1.getStatusCode().value());
        Assert.assertTrue(result1.getHeaders().get("Content-Type").contains("application/json"));
        Assert.assertEquals(getResponseBody1(), result1.getBody());
    }

    public void testGetTriggerById() {
        ResponseEntity<TriggerSampleDto> result2 = executorFeignClient.getTriggerById(triggerId);
        Assert.assertEquals(200, result2.getStatusCode().value());
        Assert.assertTrue(result2.getHeaders().get("Content-Type").contains("application/json"));
        Assert.assertEquals(getResponseBody2(), result2.getBody());
    }

    public void testGetTriggersByEnvironment() {
        ResponseEntity<EnvironmentSampleDto> result3 = executorFeignClient.getTriggersByEnvironment(environmentId);
        Assert.assertEquals(200, result3.getStatusCode().value());
        Assert.assertTrue(result3.getHeaders().get("Content-Type").contains("application/json"));
        Assert.assertEquals(getResponseBody3(), result3.getBody());
    }

    public void testUpdateTriggerStatus() {
        ResponseEntity<ResultDto> result4 = executorFeignClient.updateTriggerStatus(getRequestBody4());
        Assert.assertEquals(200, result4.getStatusCode().value());
        Assert.assertTrue(result4.getHeaders().get("Content-Type").contains("application/json"));
        Assert.assertEquals(getResponseBody4(), result4.getBody());
    }

    @Pact(consumer = "atp-itf-stubs")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        PactDslResponse response = builder
                .given("all ok")
                .uponReceiving("GET /trigger/all/active OK")
                .path("/trigger/all/active")
                .method("GET")
                .willRespondWith()
                .headers(headers)
                .body(objectToJson(getResponseBody1()))
                .status(200)

                .given("all ok")
                .uponReceiving("GET /trigger/{id} OK")
                .path("/trigger/" + triggerId)
                .method("GET")
                .willRespondWith()
                .headers(headers)
                .body(objectToJson(getResponseBody2()))
                .status(200)

                .given("all ok")
                .uponReceiving("GET /trigger/environmentId/{id} OK")
                .path("/trigger/environmentId/" + environmentId)
                .method("GET")
                .willRespondWith()
                .headers(headers)
                .body(objectToJson(getResponseBody3()))
                .status(200)

                .given("all ok")
                .uponReceiving("PATCH /trigger OK")
                .path("/trigger")
                .method("PATCH")
                .headers(headers)
                .body(objectToJson(getRequestBody4()))
                .willRespondWith()
                .headers(headers)
                .body(objectToJson(getResponseBody4()))
                .status(200);

        return response.toPact();
    }

    private List<TriggerSampleDto> getResponseBody1() {
        return Arrays.asList(getTriggerSample());
    }

    private TriggerSampleDto getResponseBody2() {
        return getTriggerSample();
    }

    private EnvironmentSampleDto getResponseBody3() {
        EnvironmentSampleDto environmentSampleDto = new EnvironmentSampleDto();
        environmentSampleDto.setEnvId(environmentId);
        environmentSampleDto.setTurnedOn(true);
        environmentSampleDto.setTriggerSamples(Arrays.asList(getTriggerSample()));
        return environmentSampleDto;
    }

    private UIUpdateTriggerStatusDto getRequestBody4() {
        UIUpdateTriggerStatusDto uiUpdateTriggerStatusDto = new UIUpdateTriggerStatusDto();
        uiUpdateTriggerStatusDto.setStatus("ACTIVE");
        uiUpdateTriggerStatusDto.setDescription("description");
        uiUpdateTriggerStatusDto.setId(triggerId);
        return uiUpdateTriggerStatusDto;
    }

    private ResultDto getResponseBody4() {
        ResultDto resultDto = new ResultDto();
        Map<String, String> data = new HashMap<>();
        data.put("key", "value");
        resultDto.setData(data);
        resultDto.setMessage("message");
        resultDto.setSuccess(true);
        return resultDto;
    }

    private TriggerSampleDto getTriggerSample() {
        TriggerSampleDto triggerSampleDto = new TriggerSampleDto();
        triggerSampleDto.setTriggerId(triggerId.toString());
        triggerSampleDto.setTriggerName("trig_name");
        triggerSampleDto.setProjectUuid(UUID.fromString("3d6a138d-057b-4e35-8348-17aee2f2b0f8"));
        Map<String, Object> triggerProperties = new HashMap<>();
        triggerProperties.put("responseCode", "200");
        triggerProperties.put("isStub", "Yes");
        triggerProperties.put("endpoint", "/test");
        triggerProperties.put("contentType", "application/json; charset=utf-8");
        triggerSampleDto.setTriggerProperties(triggerProperties);
        triggerSampleDto.setServerName("server_test");
        triggerSampleDto.setTransportName("Outbound REST Synchronous");
        triggerSampleDto.setTriggerTypeName("org.qubership.automation.itf.transport.rest.outbound.RESTOutboundTransport");
        triggerSampleDto.setTransportType(TriggerSampleDto.TransportTypeEnum.REST_INBOUND);
        return triggerSampleDto;
    }

    private String objectToJson(Object object) {
        return new Gson().toJson(object);
    }
}

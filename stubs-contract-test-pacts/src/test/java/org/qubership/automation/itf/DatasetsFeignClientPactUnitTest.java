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

package org.qubership.automation.itf;import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import au.com.dius.pact.consumer.dsl.PactDslResponse;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.PactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.qubership.atp.auth.springbootstarter.config.FeignConfiguration;
import org.qubership.automation.itf.core.util.feign.impl.DatasetsAttachmentFeignClient;

@RunWith(SpringRunner.class)
@EnableFeignClients(clients = {DatasetsAttachmentFeignClient.class})
@ContextConfiguration(classes = {DatasetsFeignClientPactUnitTest.TestApp.class})
@Import({JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        FeignConfiguration.class,
        FeignAutoConfiguration.class})
@TestPropertySource(properties = {"feign.atp.datasets.name=atp-datasets", "feign.atp.datasets.route=",
        "feign.atp.datasets.url=http://localhost:8888", "feign.httpclient.enabled=false"})
public class DatasetsFeignClientPactUnitTest {

    @Configuration
    public static class TestApp {
    }

    @Rule
    public PactProviderRule mockProvider
            = new PactProviderRule("atp-datasets", "localhost", 8888, this);

    @Autowired
    private DatasetsAttachmentFeignClient dsAttachmentFeignClient;
    private final UUID attachmentUuid = UUID.fromString("7c9dafe9-2cd1-4ffc-ae54-45867f2b9701");

    @Test
    @PactVerification()
    public void allPass() {
        ResponseEntity<Resource> result = dsAttachmentFeignClient.getAttachmentByParameterId(attachmentUuid);
        Assert.assertEquals(200, result.getStatusCode().value());
        Assert.assertTrue(result.getHeaders().get("Content-Disposition").contains("attachment; filename=\"name\""));
    }

    @Pact(consumer = "atp-itf-stubs")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Disposition", "attachment; filename=\"name\"");

        PactDslResponse response = builder
                .given("all ok")
                .uponReceiving("GET /attachment/{parameterUuid} OK")
                .path("/attachment/" + attachmentUuid)
                .method("GET")
                .willRespondWith()
                .headers(headers)
                .status(200);

        return response.toPact();
    }
}

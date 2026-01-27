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

package org.qubership.automation.itf.ui.controls;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.qubership.automation.itf.communication.FastStubsInformation;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfig;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfigsHolder;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.core.stub.fast.TransportConfig;
import org.qubership.automation.itf.stubs.service.FastStubsService;

public class FastStubsServiceTest {

    private FastStubsService fastStubsService;

    private final UUID projectUuid = UUID.fromString("24d45984-6563-438d-be37-aeca6cc849bc");
    private List<FastStubsInformation> fastStubsInformations;
    private List<FastStubsInformation> erSuccessFindFastStubsConfigs;

    @Before
    public void setUp() throws Exception {
        fastStubsService = new FastStubsService(null, null, null);
        fastStubsInformations = new ArrayList<>();
        FastStubsInformation fastStubsInfo = new FastStubsInformation(
                StubEndpointConfig.TransportTypes.REST.name(),
                "/v1.0/users/",
                "9173212125911751501_[Microsoft Outlook Server] Get MIME | Success");
        fastStubsInformations.add(fastStubsInfo);

        erSuccessFindFastStubsConfigs = new ArrayList<>();
        FastStubsInformation erFastStubsInfo = new FastStubsInformation(
                StubEndpointConfig.TransportTypes.REST.name(),
                "/v1.0/users/",
                "9173212125911751501_[Microsoft Outlook Server] Get MIME | Success");
        erFastStubsInfo.setExist(true);
        erSuccessFindFastStubsConfigs.add(erFastStubsInfo);

        initTestConfigHolder();
    }

    @Test
    public void testSuccessFindFastStubsConfigs() {
        List<FastStubsInformation> arFindFastStubsConfigs = fastStubsService
                .findFastStubsConfigs(fastStubsInformations, projectUuid);

        Assert.assertEquals(erSuccessFindFastStubsConfigs.size(), arFindFastStubsConfigs.size());
        Assert.assertEquals(erSuccessFindFastStubsConfigs.get(0), arFindFastStubsConfigs.get(0));
    }

    @Test
    public void testNoFindFastStubsConfigs() {
        fastStubsInformations = new ArrayList<>();
        FastStubsInformation fastStubsInfo = new FastStubsInformation(
                StubEndpointConfig.TransportTypes.REST.name(),
                "/v1.0/users/",
                "9173212125911751501_[Microsoft Outlook Server]");
        fastStubsInformations.add(fastStubsInfo);

        List<FastStubsInformation> arFindFastStubsConfigs = fastStubsService
                .findFastStubsConfigs(fastStubsInformations, projectUuid);

        Assert.assertEquals(1, arFindFastStubsConfigs.size());
        Assert.assertEquals(fastStubsInformations.get(0), arFindFastStubsConfigs.get(0));
    }

    private static void initTestConfigHolder() throws IOException {
        File file = new File(FastStubsServiceTest.class
                .getClassLoader()
                .getResource("24d45984-6563-438d-be37-aeca6cc849bc_REST_v1.0_users.json").getFile());
        ObjectMapper objectMapper = new ObjectMapper();
        FastResponseConfig fastResponseConfig = objectMapper.readValue(file, FastResponseConfig.class);
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

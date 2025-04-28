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

import java.util.List;
import java.util.UUID;

import org.qubership.automation.itf.communication.FastStubsInformation;
import org.qubership.automation.itf.core.stub.fast.FastStubsTreeView;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.stubs.service.FastStubsService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FastStubsController {

    private final FastStubsService fastStubsService;

    @PreAuthorize("@entityAccess.checkAccess(#projectUuid, \"READ\")")
    @PostMapping(value = "/fast-stubs/findConfigs", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<FastStubsInformation> findFastStubsConfigs(@RequestBody List<FastStubsInformation> fastStubsCandidates,
                                                           @RequestParam UUID projectUuid) {
        return fastStubsService.findFastStubsConfigs(fastStubsCandidates, projectUuid);
    }

    @PreAuthorize("@entityAccess.checkAccess(#projectUuid, \"READ\")")
    @GetMapping(value = "/fast-stubs/endpoints")
    public List<FastStubsTreeView> endpoints(@RequestParam UUID projectUuid) {
        return fastStubsService.endpoints(projectUuid);
    }

    @PreAuthorize("@entityAccess.checkAccess(#projectUuid, \"READ\")")
    @GetMapping(value = "/fast-stubs/configuration/get")
    public StubEndpointConfig getConfiguration(@RequestParam String configuredEndpoint,
                                               @RequestParam StubEndpointConfig.TransportTypes transportTypes,
                                               @RequestParam UUID projectUuid) {
        return fastStubsService.getConfiguration(configuredEndpoint, transportTypes, projectUuid);
    }

    @PreAuthorize("@entityAccess.checkAccess(#projectUuid, \"UPDATE\")")
    @PutMapping(value = "/fast-stubs/configuration/update")
    public void updateConfiguration(@RequestBody StubEndpointConfig stubEndpointConfig,
                                    @RequestParam StubEndpointConfig.TransportTypes transportTypes,
                                    @RequestParam UUID projectUuid) {
        fastStubsService.updateConfiguration(transportTypes, stubEndpointConfig, projectUuid);
    }

    @PreAuthorize("@entityAccess.checkAccess(#projectUuid, \"DELETE\")")
    @DeleteMapping(value = "/fast-stubs/configuration/delete")
    public void deleteConfiguration(@RequestBody List<FastStubsTreeView> fastStubsTreeViews,
                                    @RequestParam UUID projectUuid) {
        fastStubsService.deleteConfiguration(fastStubsTreeViews, projectUuid);
    }
}

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

import java.util.UUID;

import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.util.mdc.MdcField;
import org.qubership.automation.itf.ui.model.RouteInfoResponse;
import org.qubership.automation.itf.ui.service.TriggerRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class AtpItfStubsController {

    private final TriggerRouteService triggerRouteService;

    @Autowired
    public AtpItfStubsController(TriggerRouteService triggerRouteService) {
        this.triggerRouteService = triggerRouteService;
    }

    @RequestMapping(value = "/ping", method = RequestMethod.GET)
    public boolean ping() {
        return triggerRouteService.ping();
    }

    /**
     * Returns list of active routes by transport type.
     * @param projectUuid - list of routes will be filtered by projectUuid.
     * @param podCount - count active service pods.
     */
    @PreAuthorize("@entityAccess.checkAccess(#projectUuid, \"READ\")")
    @GetMapping(value = "/routes")
    public RouteInfoResponse collectRoutes(@RequestParam UUID projectUuid,
                                           @RequestParam TransportType transportType,
                                           @RequestParam int podCount) {
        MdcUtils.put(MdcField.PROJECT_ID.toString(), projectUuid);
        return triggerRouteService.collectRoutes(projectUuid, transportType, podCount);
    }

    /**
     * Stops route by routeId.
     */
    @PreAuthorize("@entityAccess.isSupport() || @entityAccess.isAdmin()")
    @RequestMapping(value = "/routes", method = RequestMethod.DELETE)
    public String stopRoute(@RequestParam UUID projectUuid,
                            @RequestParam String routeId,
                            @RequestParam String podName) {
        MdcUtils.put(MdcField.PROJECT_ID.toString(), projectUuid);
        return triggerRouteService.stopRoute(projectUuid, routeId, podName);
    }
}

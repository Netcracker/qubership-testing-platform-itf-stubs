/*
 * # Copyright 2024-2026 NetCracker Technology Corporation
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
import java.util.concurrent.ConcurrentHashMap;

import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.automation.itf.activation.ActivationServiceConstants;
import org.qubership.automation.itf.activation.impl.SystemServerTriggerActivationService;
import org.qubership.automation.itf.core.model.communication.StubUser;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.model.communication.message.ServerTriggerStateResponse;
import org.qubership.automation.itf.core.util.mdc.MdcField;
import org.qubership.automation.itf.ui.model.RouteInfoResponse;
import org.qubership.automation.itf.ui.service.TriggerRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class AtpItfStubsController {

    @Value("${stubs.testing.mode.enabled:false}")
    private boolean isTestingMode;

    private final TriggerRouteService triggerRouteService;
    private final SystemServerTriggerActivationService triggerActivationService;

    @Autowired
    public AtpItfStubsController(TriggerRouteService triggerRouteService,
                                 SystemServerTriggerActivationService triggerActivationService) {
        this.triggerRouteService = triggerRouteService;
        this.triggerActivationService = triggerActivationService;
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

    /**
     * Load and activate triggers according received list of objects.
     * It's used in case stubs.testing.mode.enabled=true only,
     * to provide testability of the service alone (without itf-executor service).
     * Primarily, for local development and GitHub CI.
     *
     * @param triggers List of trigger objects to activate
     * @return Activation result.
     */
    @PostMapping("/routes/load")
    public ServerTriggerStateResponse loadRoutes(@RequestBody List<TriggerSample> triggers) {
        if (!isTestingMode) {
            throw new IllegalStateException("Loading of routes is allowed in testing mode only! Please check 'stubs.testing.mode.enabled' setting.");
        }
        if (triggers == null || triggers.isEmpty()) {
            throw new IllegalArgumentException("Trigger List is empty!");
        }
        StubUser user = new StubUser();
        user.setId("0");
        user.setName("itf");
        return triggerActivationService.performBulkAction(triggers,
                new ConcurrentHashMap<>(),
                ActivationServiceConstants.ACTIVATE,
                user,
                "load-and-activate-via-rest");
    }
}

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

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.automation.itf.activation.impl.OnStartupTriggersActivationService;
import org.qubership.automation.itf.activation.impl.TriggerMaintainer;
import org.qubership.automation.itf.communication.RoutesInformationResponse;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.util.exception.TriggerException;
import org.qubership.automation.itf.core.util.mdc.MdcField;
import org.qubership.automation.itf.trigger.http.inbound.HttpInboundTrigger;
import org.qubership.automation.itf.trigger.rest.inbound.RestInboundTrigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class AtpItfStubsController {

    private final OnStartupTriggersActivationService onStartupTriggersActivationService;
    private final TriggerMaintainer triggerMaintainer;

    @Autowired
    public AtpItfStubsController(OnStartupTriggersActivationService onStartupTriggersActivationService,
                                  TriggerMaintainer triggerMaintainer) {
        this.onStartupTriggersActivationService = onStartupTriggersActivationService;
        this.triggerMaintainer = triggerMaintainer;
    }

    @RequestMapping(value = "/ping", method = RequestMethod.GET)
    public boolean ping() {
        return onStartupTriggersActivationService.isInitialActivationCompleted();
    }

    /**
     * Returns list of active routes (REST and SOAP).
     * @param routeId - returns only information for specific route, all routes if routeId not defined.
     * @param projectUuid - list of routes will be filtered by projectUuid.
     */
    @PreAuthorize("@entityAccess.checkAccess(#projectUuid, \"READ\")")
    @RequestMapping(value = "/routes", method = RequestMethod.GET)
    public List<RoutesInformationResponse> getRoutes(@RequestParam(required = false) String routeId,
                                                     @RequestParam(value = "projectUuid") UUID projectUuid)
            throws TriggerException {
        MdcUtils.put(MdcField.PROJECT_ID.toString(), projectUuid);
        RestInboundTrigger trigger = (RestInboundTrigger)triggerMaintainer
                .createNewTrigger(createServiceTriggerSample(TransportType.REST_INBOUND));
        return getRoutesInformationResponse(trigger, routeId, projectUuid);
    }

    /**
     * Stops route by routeId.
     */
    @PreAuthorize("@entityAccess.isSupport() || @entityAccess.isAdmin()")
    @RequestMapping(value = "/routes", method = RequestMethod.DELETE)
    public String stopRoute(@RequestParam String routeId) {
        try {
            RestInboundTrigger trigger = (RestInboundTrigger)triggerMaintainer
                    .createNewTrigger(createServiceTriggerSample(TransportType.REST_INBOUND));
            trigger.getCamelContext().stopRoute(routeId);
            trigger.getCamelContext().removeRoute(routeId);
            trigger.getCamelContext().removeComponent(routeId);
        } catch (Exception e) {
            return e.getMessage();
        }
        return "Route deactivated " + routeId;
    }

    /**
     * Returns TriggerSample of specific transportType for service purposes.
     */
    private TriggerSample createServiceTriggerSample(TransportType transportType) {
        TriggerSample triggerSample = new TriggerSample();
        triggerSample.setTransportType(transportType);
        triggerSample.setTriggerId(new BigInteger("00001"));
        triggerSample.setTriggerName("ServiceTrigger");
        return triggerSample;
    }

    /**
     * Get routes information.
     *
     * @param trigger - service trigger required to take CamelContext.
     * @param routeId - routeId to filter by.
     * @param projectUuid - projectUuid to filter by.
     * @return list of all active routes triggers filtered by routeId and projectUuid.
     */
    private List<RoutesInformationResponse> getRoutesInformationResponse(HttpInboundTrigger trigger,
                                                                         String routeId,
                                                                         UUID projectUuid) {
        return trigger.getCamelContext().getRoutes().stream()
                .map(route -> new RoutesInformationResponse((EventDrivenConsumerRoute)route))
                .filter(route -> Objects.isNull(routeId) || routeId.isEmpty() || routeId.equals(route.getRouteId()))
                .filter(route -> route.getEndpoint().contains(projectUuid.toString()))
                .collect(Collectors.toList());

    }

}

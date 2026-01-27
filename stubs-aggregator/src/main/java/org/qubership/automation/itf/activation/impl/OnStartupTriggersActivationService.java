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

package org.qubership.automation.itf.activation.impl;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.util.eds.ExternalDataManagementService;
import org.qubership.automation.itf.integration.executor.ExecutorService;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnStartupTriggersActivationService extends AbstractService {

    private final ExternalDataManagementService externalDataManagementService;
    private final ExecutorService executorService;

    private static final List<TransportType> VIP1_TYPES = Arrays.asList(TransportType.SMPP_INBOUND,
            TransportType.HTTP2_INBOUND, TransportType.CLI_INBOUND);
    private static final List<TransportType> VIP2_TYPES = Arrays.asList(TransportType.REST_INBOUND,
            TransportType.SOAP_OVER_HTTP_INBOUND);
    private static final List<TransportType> VIP3_TYPES = Arrays.asList(TransportType.KAFKA_INBOUND,
            TransportType.JMS_INBOUND, TransportType.FILE_INBOUND, TransportType.SNMP_INBOUND);

    @Getter
    private boolean listForActivationReceived = false;
    private boolean initialTriggersActivationCompleted = false;

    /**
     * Perform triggers activation at service startup, separately by types, based on priority.
     */
    public void perform(Set<String> triggerTypes) {
        final long startTimeAllTriggers = System.nanoTime();
        List<TriggerSample> allTriggerSamples = getTriggersForActivation();
        if (!allTriggerSamples.isEmpty()) {
            Map<TransportType, List<TriggerSample>> triggersByType =
                    allTriggerSamples.stream()
                            .filter(item -> Objects.nonNull(item) && Objects.nonNull(item.getTransportType()))
                            .collect(Collectors.groupingBy(TriggerSample::getTransportType));

            processTriggersFiltered(triggerTypes, triggersByType, VIP1_TYPES);
            processTriggersFiltered(triggerTypes, triggersByType, VIP2_TYPES);
            processTriggersFiltered(triggerTypes, triggersByType, VIP3_TYPES);

            long elapsedTimeAllTriggers = System.nanoTime() - startTimeAllTriggers;
            log.info("Triggers activation completed in {} (s)",
                    String.format("%.3f", (double) elapsedTimeAllTriggers / 1000000000.0));
        }
        if (!isListForActivationReceived()) {
            log.warn("Triggers for activation were not received.");
        } else {
            initialTriggersActivationCompleted = true;
        }
    }

    private void processTriggersFiltered(Set<String> triggerTypes,
                                         Map<TransportType, List<TriggerSample>> triggersByType,
                                         List<TransportType> processOnlyTypes) {
        triggersByType.entrySet().parallelStream().forEach(transportTypeListEntry -> {
            TransportType transportType = transportTypeListEntry.getKey();
            // Rood brief attempt to speed up initial activation of "the most valuable" transportTypes
            if (processOnlyTypes.contains(transportType)) {
                List<TriggerSample> triggerSamples = transportTypeListEntry.getValue();
                String triggerClassName = TransportTypeToTriggerClassMapping.getTriggerClassName(transportType);
                if (!triggerTypes.contains(triggerClassName)) {
                    log.error("Library for processing triggers of type '{}' not found", transportType);
                    return;
                }
                ConcurrentMap<String, Boolean> availableServers = new ConcurrentHashMap<>();
                log.info("Triggers activation for '{}' is started", triggerClassName);
                activateListOfTriggers(triggerSamples, availableServers, "activation-at-service-startup");
                log.info("Triggers activation for '{}' is finished, total count {}", triggerClassName,
                        triggerSamples.size());
            }
        });
    }

    private List<TriggerSample> getTriggersForActivation() {
        long startTime = System.nanoTime();
        try {
            List<TriggerSample> triggerSamples = executorService.getAllActiveTriggers();
            listForActivationReceived = true;
            if (triggerSamples.isEmpty()) {
                log.info("List of triggers for activation received but it is empty");
            }
            return triggerSamples;
        } catch (FeignException fe) {
            log.error("Error while trying to get triggers for activation. Message: {}", fe.getMessage());
        } finally {
            long elapsedTime = System.nanoTime() - startTime;
            log.info("Request for triggers list for activation processed in {} (s)",
                    String.format("%.3f", (double) elapsedTime / 1000000000.0));
        }
        return new LinkedList<>();
    }

    public boolean isInitialActivationCompleted() {
        return initialTriggersActivationCompleted;
    }

}



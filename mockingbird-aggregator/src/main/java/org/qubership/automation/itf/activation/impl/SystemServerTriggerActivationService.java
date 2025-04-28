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

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.qubership.automation.itf.activation.ActivationServiceConstants;
import org.qubership.automation.itf.core.model.communication.message.ServerTriggerStateResponse;
import org.qubership.automation.itf.core.model.communication.message.ServerTriggerSyncRequest;
import org.qubership.automation.itf.core.util.constants.TriggerState;
import org.springframework.stereotype.Service;

@Service
public class SystemServerTriggerActivationService extends AbstractService {

    /**
     * Process trigger deactivation/reactivation request.
     */
    public void perform(ServerTriggerSyncRequest syncRequest, String tenantId) {
        ServerTriggerStateResponse deactivationResult =
                performBulkAction(syncRequest.getTriggerIdToDeactivate(), new ConcurrentHashMap<>(),
                        ActivationServiceConstants.DEACTIVATE, syncRequest.getUser(), syncRequest.getSessionId());
        ServerTriggerStateResponse reactivationResult =
                performBulkAction(
                        syncRequest.getTriggerIdToReactivate().stream().filter(
                                triggerSample -> TriggerState.ACTIVE.equals(triggerSample.getTriggerState()))
                                .collect(Collectors.toList()),
                        new ConcurrentHashMap<>(),
                        ActivationServiceConstants.SYNC, syncRequest.getUser(), syncRequest.getSessionId());
        deactivationResult.merge(reactivationResult);
        getSender().send(deactivationResult, tenantId);
    }
}

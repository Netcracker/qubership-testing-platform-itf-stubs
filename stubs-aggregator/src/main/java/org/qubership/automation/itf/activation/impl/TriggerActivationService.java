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

import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.activation.ActivationServiceConstants;
import org.qubership.automation.itf.core.model.communication.Result;
import org.qubership.automation.itf.core.model.communication.StubUser;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.model.communication.message.TriggerStatusMessage;
import org.qubership.automation.itf.core.util.constants.TriggerState;
import org.qubership.automation.itf.integration.executor.ExecutorService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerActivationService extends CommonTriggerActivationService {

    private final ExecutorService executorService;

    public TriggerStatusMessage.ObjectType getServiceType() {
        return TriggerStatusMessage.ObjectType.TRIGGER;
    }

    @Override
    public void perform(BigInteger id, String action, StubUser user, String sessionId, String tenantId) {
        log.info("SessionId {}, user {}, action '{}' for Trigger [{}] is started", sessionId, user, action, id);
        ActivationServiceConstants actionByValue = ActivationServiceConstants.getByValue(action);
        if (actionByValue != null) {
            try {
                TriggerSample triggerSample = getTriggerConfiguration(id);
                if (triggerSample != null) {
                    Result result = performActionForTrigger(
                            triggerSample, actionByValue, user, new ConcurrentHashMap<>());
                    if (result.isSuccess()) {
                        String triggerStateAsString = ActivationServiceConstants.ACTIVATE.getValue().equals(action)
                                ? TriggerState.ACTIVE.toString() : TriggerState.INACTIVE.toString();
                        sendSuccessMessageToConfigurator(this.getServiceType(),
                                id, triggerStateAsString, user, sessionId, tenantId);
                        log.info("SessionId {}, user {}, action '{}' for Trigger [{}] is succeeded", sessionId, user,
                                action, id);
                    } else {
                        sendFailMessageToConfigurator(this.getServiceType(),
                                id, TriggerState.ERROR.toString(), result.getMessage(), user, sessionId, tenantId);
                        log.error("SessionId {}, user {}, action '{}' for Trigger [{}] is failed: {}", sessionId, user,
                                action, id, result.getMessage());
                    }
                } else {
                    sendFailMessageToConfigurator(this.getServiceType(), id, null,
                            ActivationServiceConstants.TRIGGER_NOT_FOUND.getValueWithArgs(id), user, sessionId,
                            tenantId);
                    log.error("Trigger isn't found by id {}. Status isn't changed.", id);
                }
            } catch (Exception e) {
                sendFailMessageToConfigurator(this.getServiceType(), id, null,
                        ActivationServiceConstants.TRIGGER_NOT_FOUND.getValueWithArgs(id), user, sessionId, tenantId);
                log.error("Error while getting trigger by id {}. Message: {}", id,
                        e.getMessage() + (e.getMessage().contains("Caused by:") || Objects.isNull(e.getCause())
                                ? StringUtils.EMPTY : "Caused by: " + e.getCause().getMessage()));
            }
        } else {
            sendFailMessageToConfigurator(this.getServiceType(), id, null,
                    ActivationServiceConstants.ACTION_NOT_VALID.getValueWithArgs(action), user, sessionId, tenantId);
            log.error("Action '{}' isn't valid. Status isn't changed.", action);
        }
    }

    private TriggerSample getTriggerConfiguration(BigInteger triggerId) {
        TriggerSample triggerSample = executorService.getTriggerById(triggerId);
        if (triggerSample == null) {
            log.warn("Trigger isn't found by id {}. Null is returned", triggerId);
            return null;
        } else {
            return triggerSample;
        }
    }
}

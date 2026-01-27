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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.activation.ActivationServiceConstants;
import org.qubership.automation.itf.core.model.communication.EnvironmentSample;
import org.qubership.automation.itf.core.model.communication.StubUser;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.model.communication.message.ServerTriggerStateResponse;
import org.qubership.automation.itf.core.model.communication.message.TriggerBulkPerformRequest;
import org.qubership.automation.itf.core.model.communication.message.TriggerStatusMessage;
import org.qubership.automation.itf.core.util.constants.TriggerState;
import org.qubership.automation.itf.core.util.exception.TriggerException;
import org.qubership.automation.itf.integration.executor.ExecutorService;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentActivationService extends CommonTriggerActivationService {

    private final ExecutorService executorService;
    public static final String ENVIRONMENT_ACTIVATION_ERROR_MESSAGE = "Some errors occurred during environment "
            + "activation, see Stubs logs for details";
    private static final String ENVFOLDER_CLASS =
            "org.qubership.automation.itf.core.model.jpa.folder.EnvFolder";
    private static final String ENVIRONMENT_CLASS =
            "org.qubership.automation.itf.core.model.jpa.environment.Environment";

    public TriggerStatusMessage.ObjectType getServiceType() {
        return TriggerStatusMessage.ObjectType.ENVIRONMENT;
    }

    /**
     * Perform triggers processing for not-'sync' and not {Activate/Deactivate/Reactivate of 'environment'} messages.
     *
     * @param id - Environment id,
     * @param action - should be SWITCH action always,
     * @param user - user performing action,
     * @param sessionId - session id UUID for logging purposes,
     * @param tenantId - projectUuid.
     */
    @Override
    public void perform(BigInteger id, String action, StubUser user, String sessionId, String tenantId) {
        try {
            log.info("SessionId {}, user {}, action '{}' for Env [{}] is started", sessionId, user, action, id);
            AtomicBoolean isSuccess = new AtomicBoolean(true);
            EnvironmentSample environmentSample = getTriggersUnderEnvironment(id);
            ActivationServiceConstants actionValue = environmentSample.isTurnedOn()
                    ? ActivationServiceConstants.DEACTIVATE : ActivationServiceConstants.ACTIVATE;
            ConcurrentMap<String, Boolean> availableServers = new ConcurrentHashMap<>();
            processTriggers(environmentSample.getTriggerSamples(), actionValue, user, isSuccess, availableServers,
                    action, sessionId, tenantId);
            if (isSuccess.get()) {
                String triggerStateAsString = ActivationServiceConstants.ACTIVATE.equals(actionValue)
                        ? TriggerState.ACTIVE.toString()
                        : TriggerState.INACTIVE.toString();
                sendSuccessMessageToConfigurator(this.getServiceType(), id, triggerStateAsString, user, sessionId,
                        tenantId);
                log.info("SessionId {}, user {}, action '{}' for Env [{}] is completed", sessionId, user, action, id);
            } else {
                sendFailMessageToConfigurator(this.getServiceType(), id, TriggerState.ERROR.toString(),
                        ENVIRONMENT_ACTIVATION_ERROR_MESSAGE, user, sessionId, tenantId);
                log.warn("SessionId {}, user {}, action '{}' for Env [{}] is failed", sessionId, user, action, id);
            }
        } catch (TriggerException e) {
            log.error("Error while getting trigger by id {}. Message: {}", id,
                    e.getMessage() + (e.getMessage().contains("Caused by:") || Objects.isNull(e.getCause())
                            ? StringUtils.EMPTY : "Caused by: " + e.getCause().getMessage()));
            sendFailMessageToConfigurator(this.getServiceType(), id, TriggerState.ERROR.toString(), e.getMessage(),
                    user, sessionId, tenantId);
        }
    }

    /**
     * Perform bulk action for triggers in the request.
     *
     * @param triggerBulkPerformRequest - request to perform bulk trigger operations.
     */
    public void perform(TriggerBulkPerformRequest triggerBulkPerformRequest) {
        UUID projectUuid = triggerBulkPerformRequest.getProjectUuid();
        String action = triggerBulkPerformRequest.getAction();
        StubUser user = triggerBulkPerformRequest.getUser();
        String sessionId = triggerBulkPerformRequest.getSessionId();
        log.info("Project UUID {}, SessionId {}, user {}, bulk action '{}' is started...", projectUuid, sessionId, user,
                action);
        try {
            List<TriggerSample> triggerSamples = getTriggersForAction(triggerBulkPerformRequest);
            ActivationServiceConstants actionConstant
                    = ActivationServiceConstants.getByValue(triggerBulkPerformRequest.getAction());
            AtomicBoolean isSuccess = new AtomicBoolean(true);
            ConcurrentMap<String, Boolean> availableServers = new ConcurrentHashMap<>();

            processTriggers(triggerSamples, actionConstant, user, isSuccess, availableServers,
                    action, sessionId, projectUuid.toString());

            Map<BigInteger, TriggerState> triggerStates = triggerSamples.stream()
                    .collect(Collectors.toMap(TriggerSample::getTriggerId, TriggerSample::getTriggerState));

            ServerTriggerStateResponse response = new ServerTriggerStateResponse(triggerStates, StringUtils.EMPTY, user,
                    sessionId);
            if (isSuccess.get()) {
                getSender().send(response, projectUuid.toString());
                log.info("Project UUID {}, SessionId {}, user {}, bulk action '{}' is completed.", projectUuid,
                        sessionId, user, action);
            } else {
                response.setErrorMessage(ENVIRONMENT_ACTIVATION_ERROR_MESSAGE);
                getSender().send(response, projectUuid.toString());
                log.warn("Project UUID {}, SessionId {}, user {}, bulk action '{}' is failed.", projectUuid, sessionId,
                        user, action);
            }
        } catch (TriggerException e) {
            String errorMessage = ENVIRONMENT_ACTIVATION_ERROR_MESSAGE;
            ServerTriggerStateResponse response
                    = new ServerTriggerStateResponse(new HashMap<>(), errorMessage, user, sessionId);
            getSender().send(response, projectUuid.toString());
            log.error(errorMessage + ": " + e.getMessage()
                    + (e.getMessage().contains("Caused by:") || Objects.isNull(e.getCause())
                            ? StringUtils.EMPTY : "Caused by: " + e.getCause().getMessage()));
        }
    }

    private List<TriggerSample> getTriggersForAction(TriggerBulkPerformRequest triggerBulkPerformRequest)
            throws TriggerException {
        if (triggerBulkPerformRequest.isSelectedAll()) {
            return ActivationServiceConstants.RE_ACTIVATE.getValue().equals(triggerBulkPerformRequest.getAction())
                    ? getAllTriggersByProjectToReActivate(triggerBulkPerformRequest.getProjectUuid())
                    : getAllTriggersByProject(triggerBulkPerformRequest.getProjectUuid());
        } else {
            List<BigInteger> envFolderIds = getObjectIdsWithClass(ENVFOLDER_CLASS, triggerBulkPerformRequest);
            List<BigInteger> environmentIds = getObjectIdsWithClass(ENVIRONMENT_CLASS, triggerBulkPerformRequest);
            List<TriggerSample> triggerSamples = new ArrayList<>();
            try {
                List<EnvironmentSample> environmentSamples = new ArrayList<>();
                envFolderIds.forEach(id -> environmentSamples.addAll(getTriggersByEnvFolder(id)));
                for (BigInteger id : environmentIds) {
                    environmentSamples.add(getTriggersUnderEnvironment(id));
                }
                environmentSamples.forEach(environmentSample ->
                        triggerSamples.addAll(environmentSample.getTriggerSamples()));

                if (ActivationServiceConstants.RE_ACTIVATE.getValue().equals(triggerBulkPerformRequest.getAction())) {
                    return triggerSamples.stream()
                            .filter(trigger -> TriggerState.ACTIVE.equals(trigger.getTriggerState())
                            || TriggerState.ERROR.equals(trigger.getTriggerState()))
                            .collect(Collectors.toList());
                }
            } catch (TriggerException e) {
                log.error(e.getMessage(), e);
                throw e;
            }
            return triggerSamples;
        }
    }

    private EnvironmentSample getTriggersUnderEnvironment(BigInteger environmentId) throws TriggerException {
        EnvironmentSample environmentSample = executorService.getTriggersByEnvironment(environmentId);
        if (environmentSample == null) {
            throw new TriggerException("Triggers aren't found under Environment with id [" + environmentId
                    + "]. Null is returned.");
        } else {
            return environmentSample;
        }
    }

    private void logError(FeignException fe) {
        log.error("Error while getting triggers for activation. Message: {}", fe.getMessage());
    }

    private void logDuration(long startTime) {
        log.info("Request for triggers list for activation is processed in {} (s)",
                String.format("%.3f", (double) (System.nanoTime() - startTime) / 1000000000.0));
    }

    private void logNothingToDo(boolean isEmpty) {
        if (isEmpty) {
            log.warn("List of triggers for activation received but it's empty");
        }
    }

    private List<EnvironmentSample> getTriggersByEnvFolder(BigInteger envFolderId) {
        long startTime = System.nanoTime();
        try {
            List<EnvironmentSample> environmentSamples = executorService.getTriggersByEnvFolder(envFolderId);
            logNothingToDo(environmentSamples.isEmpty());
            return environmentSamples;
        } catch (FeignException fe) {
            logError(fe);
        } finally {
            logDuration(startTime);
        }
        return Collections.emptyList();
    }

    private List<TriggerSample> getAllTriggersByProject(UUID projectUuid) {
        long startTime = System.nanoTime();
        try {
            List<TriggerSample> triggerSamples = executorService.getAllTriggersByProject(projectUuid);
            logNothingToDo(triggerSamples.isEmpty());
            return triggerSamples;
        } catch (FeignException fe) {
            logError(fe);
        } finally {
            logDuration(startTime);
        }
        return Collections.emptyList();
    }

    private List<TriggerSample> getAllTriggersByProjectToReActivate(UUID projectUuid) {
        long startTime = System.nanoTime();
        try {
            List<TriggerSample> triggerSamples = executorService.getAllTriggersByProjectToReActivate(projectUuid);
            logNothingToDo(triggerSamples.isEmpty());
            return triggerSamples;
        } catch (FeignException fe) {
            logError(fe);
        } finally {
            logDuration(startTime);
        }
        return Collections.emptyList();
    }

    private List<BigInteger> getObjectIdsWithClass(String className, TriggerBulkPerformRequest request) {
        List<BigInteger> ids = new ArrayList<>();
        if (Objects.nonNull(request.getObjects())) {
            request.getObjects().forEach(object -> {
                if (className.equals(object.getClassName())) {
                    ids.add(object.getId());
                }
            });
        }
        return ids;
    }
}

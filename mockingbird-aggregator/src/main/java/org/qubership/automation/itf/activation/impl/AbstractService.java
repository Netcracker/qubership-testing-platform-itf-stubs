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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.activation.ActivationServiceConstants;
import org.qubership.automation.itf.activation.ThreadPoolProvider;
import org.qubership.automation.itf.communication.StubsIntegrationMessageSender;
import org.qubership.automation.itf.core.model.communication.Result;
import org.qubership.automation.itf.core.model.communication.StubUser;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.model.communication.UpdateTriggerStatusRequest;
import org.qubership.automation.itf.core.model.communication.message.ServerTriggerStateResponse;
import org.qubership.automation.itf.core.model.communication.message.TriggerStatusMessage;
import org.qubership.automation.itf.core.util.constants.TriggerState;
import org.qubership.automation.itf.integration.executor.ExecutorService;
import org.qubership.automation.itf.monitoring.metrics.Metric;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public abstract class AbstractService implements ActivationService {

    @Getter
    private StubsIntegrationMessageSender sender;
    private ExecutorService executorService;
    private TriggerMaintainer triggerMaintainer;
    private MetricsAggregateService metricsAggregateService;
    private ThreadPoolProvider threadPoolProvider;
    private int bulkProcessingMaxTime;

    @Autowired
    public void setSender(StubsIntegrationMessageSender sender) {
        this.sender = sender;
    }

    @Autowired
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Autowired
    public void setTriggerMaintainer(TriggerMaintainer triggerMaintainer) {
        this.triggerMaintainer = triggerMaintainer;
    }

    @Autowired
    public void setMetricsAggregateService(MetricsAggregateService metricsAggregateService) {
        this.metricsAggregateService = metricsAggregateService;
    }

    @Autowired
    public void setThreadPoolProvider(ThreadPoolProvider threadPoolProvider) {
        this.threadPoolProvider = threadPoolProvider;
    }

    @Autowired
    public void setBulkProcessingMaxTime(@Value("${bulk.processing.max.time}") int bulkProcessingMaxTime) {
        this.bulkProcessingMaxTime = bulkProcessingMaxTime;
    }

    protected void sendSuccessMessageToConfigurator(TriggerStatusMessage.ObjectType objectType,
                                                    BigInteger id,
                                                    String status,
                                                    StubUser user,
                                                    String sessionId,
                                                    String tenantId) {
        TriggerStatusMessage message =
                new TriggerStatusMessage(objectType, id, status, StringUtils.EMPTY, user, sessionId);
        message.setSuccess(true);
        sender.send(message, tenantId);
    }

    protected void sendFailMessageToConfigurator(TriggerStatusMessage.ObjectType objectType,
                                                 BigInteger id,
                                                 String status,
                                                 String description,
                                                 StubUser user,
                                                 String sessionId,
                                                 String tenantId) {
        TriggerStatusMessage message =
                new TriggerStatusMessage(objectType, id, status, description, user, sessionId);
        sender.send(message, tenantId);
    }

    protected Result updateTriggerStatus(BigInteger id, String status, String description) {
        UpdateTriggerStatusRequest request = new UpdateTriggerStatusRequest(id, status, description);
        return executorService.updateTriggerStatus(request);
    }

    protected void processTriggers(List<TriggerSample> triggers,
                                   ActivationServiceConstants actionValue,
                                   StubUser user,
                                   AtomicBoolean isSuccess,
                                   ConcurrentMap<String, Boolean> availableServers,
                                   String action,
                                   String sessionId,
                                   String projectUuid) {
        Map<BigInteger, TriggerState> triggerStates = new ConcurrentHashMap<>();
        threadPoolProvider.getForkJoinPool().submit(() -> triggers.stream().parallel().forEach(triggerSample -> {
            log.info("Project UUID {}, SessionId {}, user {}, action '{}' for Trigger [{}] is started...",
                    projectUuid, sessionId, user, action, triggerSample.getTriggerId());
            performActionForTrigger(triggerSample, actionValue, user, isSuccess, availableServers);
            triggerStates.put(triggerSample.getTriggerId(), triggerSample.getTriggerState());
        }));
        waitForCompletion(sessionId, triggers.size(), triggerStates);
    }

    protected Result performActionForTrigger(TriggerSample triggerSample,
                                             ActivationServiceConstants actionValue,
                                             ConcurrentMap<String, Boolean> availableServers) {
        return performActionForTrigger(triggerSample, actionValue, new StubUser("Stubs"), availableServers);
    }

    protected void performActionForTrigger(TriggerSample triggerSample,
                                           ActivationServiceConstants actionValue,
                                           StubUser user,
                                           AtomicBoolean isSuccess,
                                           ConcurrentMap<String, Boolean> availableServers) {
        Result result = performActionForTrigger(triggerSample, actionValue, user, availableServers);
        if (!result.isSuccess()) {
            isSuccess.set(false);
        }
    }

    protected Result performActionForTrigger(TriggerSample triggerSample,
                                             ActivationServiceConstants action,
                                             StubUser user,
                                             ConcurrentMap<String, Boolean> availableServers) {
        long startTime = System.nanoTime();
        try {
            return doTriggerAction(triggerSample, action, availableServers);
        } catch (Exception exc) {
            String errorDescription = String.format(
                    "Error while %s the trigger with id %s. Cause: %s",
                    action,
                    triggerSample.getTriggerId(),
                    exc.getCause()
            );
            metricsAggregateService.incrementRequestToProject(triggerSample.getProjectUuid(),
                    Metric.ATP_ITF_STUBS_ERROR_TRIGGER_BY_PROJECT);
            log.error(errorDescription, exc);
            try {
                updateTriggerStatus(triggerSample.getTriggerId(), TriggerState.ERROR.toString(), errorDescription);
            } catch (Exception e) {
                log.error("Error while updating trigger status via executor: ", exc);
            }
            return new Result(false, errorDescription);
        } finally {
            long elapsedTime = System.nanoTime() - startTime;
            log.info("Trigger '{}' [ID={}] duration for {}: {} (s)", triggerSample.getTriggerName(),
                    triggerSample.getTriggerId(), action, String.format("%.3f", (double) elapsedTime / 1000000000.0));
        }
    }

    protected Result doTriggerAction(TriggerSample triggerSample,
                                     ActivationServiceConstants action,
                                     ConcurrentMap<String, Boolean> availableServers) throws Exception {
        Result response = new Result();
        TriggerState state;
        switch (action) {
            case SWITCH:
                if (!triggerSample.getTriggerState().equals(TriggerState.INACTIVE)) {
                    if (triggerSample.getTriggerState().equals(TriggerState.ACTIVE)) {
                        triggerMaintainer.deactivate(triggerSample);
                    }
                    state = TriggerState.INACTIVE;
                } else {
                    triggerMaintainer.activate(triggerSample, availableServers);
                    state = TriggerState.ACTIVE;
                    metricsAggregateService.incrementRequestToProject(triggerSample.getProjectUuid(),
                            Metric.ATP_ITF_STUBS_ACTIVE_TRIGGER_BY_PROJECT);
                }
                break;
            case ACTIVATE:
                triggerMaintainer.activate(triggerSample, availableServers);
                state = TriggerState.ACTIVE;
                metricsAggregateService.incrementRequestToProject(triggerSample.getProjectUuid(),
                        Metric.ATP_ITF_STUBS_ACTIVE_TRIGGER_BY_PROJECT);
                break;
            case DEACTIVATE:
                triggerMaintainer.deactivate(triggerSample);
                state = TriggerState.INACTIVE;
                break;
            case SYNC:
            case RE_ACTIVATE:
                this.doTriggerAction(triggerSample, ActivationServiceConstants.DEACTIVATE, availableServers);
                this.doTriggerAction(triggerSample, ActivationServiceConstants.ACTIVATE, availableServers);
                state = TriggerState.ACTIVE;
                break;
            default:
                throw new IllegalStateException("Unexpected action: " + action
                        + " for trigger with id: " + triggerSample.getTriggerId());
        }
        try {
            response = updateTriggerStatus(triggerSample.getTriggerId(), state.toString(), StringUtils.EMPTY);
            triggerSample.setTriggerState(state);
        } catch (Exception e) {
            log.error("Error while updating trigger status via executor: ", e);
        }
        return response;
    }

    /**
     * Performs activation of list of triggers.
     *
     * @param triggers - triggers to activate,
     * @param availableServers - (un)availability servers map,
     * @param sessionId - generated sessionId for logging purposes.
     */
    public void activateListOfTriggers(List<TriggerSample> triggers,
                                       ConcurrentMap<String, Boolean> availableServers,
                                       String sessionId) {
        StubUser user = new StubUser();
        user.setId("0");
        user.setName("itf");
        performBulkAction(triggers, availableServers, ActivationServiceConstants.ACTIVATE, user, sessionId);
    }

    /**
     * Performs (de)activation of list of triggers.
     *
     * @param triggers - triggers to (de)activate,
     * @param availableServers - (un)availability servers map,
     * @param action - action to perform,
     * @param user - user performing action,
     * @param sessionId - generated sessionId for logging purposes.
     */
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE",
            justification = "Checked; result of pool.submit() is not needed currently")
    public ServerTriggerStateResponse performBulkAction(List<TriggerSample> triggers,
                                                        ConcurrentMap<String, Boolean> availableServers,
                                                        ActivationServiceConstants action,
                                                        StubUser user,
                                                        String sessionId) {
        Map<BigInteger, TriggerState> triggerStates = new ConcurrentHashMap<>();
        if (triggers.isEmpty()) {
            log.info("Session {}, action {}: No triggers to process.", sessionId, action);
            return new ServerTriggerStateResponse(triggerStates, StringUtils.EMPTY, user, sessionId);
        }
        threadPoolProvider.getForkJoinPool().submit(() -> triggers.stream().parallel().forEach(triggerSample -> {
            performActionForTrigger(triggerSample, action, availableServers);
            triggerStates.put(triggerSample.getTriggerId(), triggerSample.getTriggerState());
        }));
        waitForCompletion(sessionId, triggers.size(), triggerStates);
        return new ServerTriggerStateResponse(triggerStates, StringUtils.EMPTY, user, sessionId);
    }

    private void waitForCompletion(String sessionId,
                                   int totalCount,
                                   Map<BigInteger, TriggerState> triggerStates) {
        long startingTimestamp = System.currentTimeMillis();
        while (triggerStates.size() < totalCount) {
            try {
                if (System.currentTimeMillis() - startingTimestamp > bulkProcessingMaxTime) {
                    log.warn("Session {}: processing is interrupted due to max time limit {} ms", sessionId,
                            bulkProcessingMaxTime);
                    break;
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                log.warn("Session {}: processing is interrupted after {} ms", sessionId,
                        System.currentTimeMillis() - startingTimestamp);
                break;
            }
        }
        log.info("Session {}: All ({}) triggers are processed, elapsed {} ms {}",
                sessionId,
                totalCount,
                System.currentTimeMillis() - startingTimestamp,
                (totalCount - triggerStates.size() > 0
                        ? ", pending count: " + (totalCount - triggerStates.size()) : ""));

    }
}

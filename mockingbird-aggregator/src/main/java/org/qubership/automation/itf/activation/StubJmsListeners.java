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

package org.qubership.automation.itf.activation;

import static org.qubership.automation.itf.activation.ActivationServiceConstants.ACTIVATE;
import static org.qubership.automation.itf.activation.ActivationServiceConstants.DEACTIVATE;
import static org.qubership.automation.itf.activation.ActivationServiceConstants.ENVIRONMENT;
import static org.qubership.automation.itf.activation.ActivationServiceConstants.RE_ACTIVATE;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.jms.JMSException;

import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.integration.configuration.annotation.AtpJaegerLog;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.atp.multitenancy.core.header.CustomHeader;
import org.qubership.automation.itf.activation.impl.ActivationService;
import org.qubership.automation.itf.activation.impl.CommonTriggerActivationService;
import org.qubership.automation.itf.activation.impl.EnvironmentActivationService;
import org.qubership.automation.itf.activation.impl.SystemServerTriggerActivationService;
import org.qubership.automation.itf.activation.impl.TriggerActivationService;
import org.qubership.automation.itf.core.model.communication.StubUser;
import org.qubership.automation.itf.core.model.communication.message.ItfConfigurationMessage;
import org.qubership.automation.itf.core.model.communication.message.ServerTriggerSyncRequest;
import org.qubership.automation.itf.core.model.communication.message.TriggerBulkPerformRequest;
import org.qubership.automation.itf.core.model.communication.message.TriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfigsHolder;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.eds.ExternalDataManagementService;
import org.qubership.automation.itf.core.util.eds.model.FileInfo;
import org.qubership.automation.itf.core.util.eds.service.EdsContentType;
import org.qubership.automation.itf.core.util.mdc.MdcField;
import org.qubership.automation.itf.core.util.transport.service.LockProvider;
import org.qubership.automation.itf.core.util.transport.service.SessionHandler;
import org.qubership.automation.itf.ui.model.RouteEvent;
import org.qubership.automation.itf.ui.model.RouteInfoDto;
import org.qubership.automation.itf.ui.service.TriggerRouteService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StubJmsListeners {

    private final TriggerServiceFactory triggerServiceFactory;
    private final ObjectMapper jmsMessageConverterObjectMapper;
    private final ExternalDataManagementService externalDataManagementService;
    private final ThreadPoolProvider threadPoolProvider;
    private final TriggerRouteService triggerRouteService;

    private final String executorStubsResponseSelectorKey = "hostname";
    private final String executorStubsResponseSelectorValue = "'${hostname}'";
    private final String executorStubsResponseSelectorData =
            executorStubsResponseSelectorKey + "=" + executorStubsResponseSelectorValue;

    /**
     * Constructor using earlier initialized beans.
     */
    @Autowired
    public StubJmsListeners(TriggerServiceFactory triggerServiceFactory,
                            @Qualifier(value = "jmsMessageConverterObjectMapper") ObjectMapper
                                    jmsMessageConverterObjectMapper,
                            ExternalDataManagementService externalDataManagementService,
                            ThreadPoolProvider threadPoolProvider,
                            TriggerRouteService triggerRouteService) {
        this.triggerServiceFactory = triggerServiceFactory;
        this.jmsMessageConverterObjectMapper = jmsMessageConverterObjectMapper;
        this.externalDataManagementService = externalDataManagementService;
        this.threadPoolProvider = threadPoolProvider;
        this.triggerRouteService = triggerRouteService;
    }

    /**
     * On receive trigger configuration (activate, deactivate, sync) messages.
     * "X-Project-Id" StringProperty (header) is required for income message to properly work ITF multi-tenancy
     * (additional db clusters).
     *
     * @param message - AMQ message with "type" parameter and two types of actions.
     *                1. "sync" (update action)
     *                Bulk action to reactivate/deactivate list of triggers for System-Server (After click on save
     *                button on UI).
     *                2. activate\deactivate\turn off actions for one certainly trigger or whole environment.
     */
    @JmsListener(destination = "${message-broker.configurator-stubs.topic}",
            containerFactory = "stubsTopicJmsListenerContainerFactory")
    public void onConfiguratorStubsMessage(ActiveMQTextMessage message) {
        try {
            log.info("Message received from topic '{}': {}", message.getDestination(), message.getText());
            String projectUuid = message.getStringProperty(CustomHeader.X_PROJECT_ID);
            MdcUtils.put(MdcField.PROJECT_ID.toString(), projectUuid);
            String type = jmsMessageConverterObjectMapper.readTree(message.getText()).get("type").asText();
            String action = jmsMessageConverterObjectMapper.readTree(message.getText()).hasNonNull("action")
                    ? jmsMessageConverterObjectMapper.readTree(message.getText()).get("action").asText()
                    : StringUtils.EMPTY;
            ActivationService service = triggerServiceFactory.getService(type);
            if (service != null) {
                CompletableFuture.runAsync(() ->
                        processTriggersActivationMessage(message, type, action, projectUuid, service),
                        threadPoolProvider.getAsyncTasksPool());
            } else {
                log.error("There is no service for '{}' type", type);
            }
        } catch (JMSException | JsonProcessingException e) {
            log.error("Error while message processing: {}", e.getMessage());
            sendFailMessageToConfigurator(message, e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    private void sendFailMessageToConfigurator(ActiveMQTextMessage message, String error) {
        try {
            String sessionId = jmsMessageConverterObjectMapper.readTree(message.getText()).get("sessionId").asText();
            String user = jmsMessageConverterObjectMapper.readTree(message.getText()).get("user").toString();
            StubUser stubUser = new ObjectMapper().readValue(user, StubUser.class);
            ActivationService service = triggerServiceFactory.getService(ActivationServiceConstants.TRIGGER.getValue());
            TriggerConfigurationResponse response = new TriggerConfigurationResponse(
                    String.format("Message processing error. Please contact support. Cause: %s", error),
                    stubUser,
                    sessionId);
            ((TriggerActivationService)service).getSender()
                    .send(response, message.getStringProperty(CustomHeader.X_PROJECT_ID));
        } catch (JMSException | JsonProcessingException e) {
            log.error("Error while configurator_stubs topic message processing : {}", e.getMessage());
        }
    }

    /**
     * On receive responses prepared by itf-executor.
     */
    @JmsListener(destination = "${message-broker.executor-stubs-outgoing-response.queue}",
            containerFactory = "stubsQueueJmsListenerContainerFactory",
            selector = executorStubsResponseSelectorData
    )
    @AtpJaegerLog()
    public void onExecutorStubsOutgoingResponseMessage(ActiveMQTextMessage activeMqTextMessage) {
        try {
            activeMqTextMessage.acknowledge();
            TriggerExecutionMessage triggerExecutionMessage = jmsMessageConverterObjectMapper.readValue(
                    activeMqTextMessage.getText(), TriggerExecutionMessage.class);
            String sessionId = triggerExecutionMessage.getSessionId();
            MdcUtils.put(MdcField.SESSION_ID.toString(), sessionId);
            String traceId = activeMqTextMessage.getStringProperty("traceId");
            MdcUtils.put(MdcField.TRACE_ID.toString(), traceId);
            log.info("Response is received for sessionId: {}", sessionId);
            Message message = triggerExecutionMessage.getMessage();
            if (message != null) {
                SessionHandler.INSTANCE.addMessage(sessionId, message);
            } else {
                log.warn("Response message is NULL for sessionId: {}", sessionId);
            }
            LockProvider.INSTANCE.notify(sessionId);
        } catch (JMSException | JsonProcessingException e) {
            log.error("Error while message processing: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error while executor_stubs_outgoing_response queue message processing : {}", e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    /**
     * On receive messages about uploaded files.
     */
    @JmsListener(destination = "${message-broker.eds-update.topic}",
            containerFactory = "stubsTopicJmsListenerContainerFactory")
    public void onExternalDataStorageUpdateMessage(ActiveMQTextMessage activeMqTextMessage) {
        try {
            FileInfo fileInfo = jmsMessageConverterObjectMapper.readValue(activeMqTextMessage.getText(),
                    FileInfo.class);
            CompletableFuture.runAsync(() -> processUpdateFileMessage(fileInfo),
                    threadPoolProvider.getAsyncTasksPool());
        } catch (JMSException | JsonProcessingException e) {
            log.error("Error while message processing: {}", e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    @JmsListener(destination = "${message-broker.stubs-route-info.request.topic}",
            containerFactory = "stubsTopicJmsListenerContainerFactory")
    public void onStubsRouteInfoRequestTopic(ActiveMQTextMessage activeMqTextMessage) {
        try {
            RouteEvent event = jmsMessageConverterObjectMapper.readValue(
                    activeMqTextMessage.getText(), RouteEvent.class);
            switch (event.getEventType()) {
                case COLLECT: {
                    triggerRouteService.collectRouteInfo(event);
                    break;
                }
                case STOP: {
                    if (!event.getPodNameRouteToStop().equals(Config.getConfig().getRunningHostname())) {
                        return;
                    }
                    triggerRouteService.stopRoute(event);
                    break;
                }
                default: {
                    throw new RuntimeException("Unknown route event type: " + event.getEventType());
                }
            }
        } catch (JMSException | JsonProcessingException e) {
            log.error("Error while message processing: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error while stubs-route-info.request topic message processing: {}", e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    @JmsListener(destination = "${message-broker.stubs-route-info.response.topic}",
            containerFactory = "stubsTopicJmsListenerContainerFactory")
    public void onStubsRouteInfoResponseTopic(ActiveMQTextMessage activeMqTextMessage) {
        try {
            RouteInfoDto event = jmsMessageConverterObjectMapper.readValue(
                    activeMqTextMessage.getText(), RouteInfoDto.class);
            if (!event.getRequestPodName().equals(Config.getConfig().getRunningHostname())) {
                return;
            }
            triggerRouteService.putToCache(event);
        } catch (JMSException | JsonProcessingException e) {
            log.error("Error while message processing: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error while stubs-route-info.response topic message processing: {}", e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    private void processTriggersActivationMessage(ActiveMQTextMessage message,
                                                  String type,
                                                  String action,
                                                  String projectUuid,
                                                  ActivationService service) {
        try {
            if ("sync".equals(type)) {
                ServerTriggerSyncRequest request = jmsMessageConverterObjectMapper.readValue(message.getText(),
                        ServerTriggerSyncRequest.class);
                putMdcFields(projectUuid, request.getSessionId());
                ((SystemServerTriggerActivationService) service).perform(request, projectUuid);
            } else if ((ACTIVATE.getValue().equals(action) || DEACTIVATE.getValue().equals(action)
                    || RE_ACTIVATE.getValue().equals(action)) && ENVIRONMENT.getValue().equals(type)) {
                TriggerBulkPerformRequest request = jmsMessageConverterObjectMapper.readValue(message.getText(),
                        TriggerBulkPerformRequest.class);
                putMdcFields(projectUuid, request.getSessionId());
                ((EnvironmentActivationService) service).perform(request);
            } else {
                ItfConfigurationMessage request = jmsMessageConverterObjectMapper.readValue(message.getText(),
                        ItfConfigurationMessage.class);
                putMdcFields(projectUuid, request.getSessionId());
                ((CommonTriggerActivationService) service).perform(request.getId(), request.getAction(),
                        request.getUser(), request.getSessionId(), projectUuid);
            }
            log.info("Message of type '{}' processing is completed", type);
        } catch (JMSException | JsonProcessingException e) {
            log.error("Error while message processing: {}", e.getMessage());
            sendFailMessageToConfigurator(message, e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    private void processUpdateFileMessage(FileInfo fileInfo) {
        try {
            MdcUtils.put(MdcField.PROJECT_ID.toString(), fileInfo.getProjectUuid());
            if (EdsContentType.WSDL_XSD.getStringValue().equals(fileInfo.getContentType())
                    || EdsContentType.KEYSTORE.getStringValue().equals(fileInfo.getContentType())
                    || EdsContentType.FAST_STUB.getStringValue().equals(fileInfo.getContentType())) {
                switch (fileInfo.getEventType()) {
                    case UPLOAD: {
                        File savedFile;
                        if (Objects.nonNull(fileInfo.getObjectId())) {
                            savedFile = externalDataManagementService.getFileManagementService()
                                    .save(externalDataManagementService.getExternalStorageService()
                                            .getFileInfo(fileInfo.getObjectId()));
                        } else {
                            savedFile = externalDataManagementService.getFileManagementService().save(fileInfo);
                        }
                        log.info("File {} is loaded into local storage successfully.", fileInfo.getFileName());
                        postProcess(fileInfo, savedFile);
                        break;
                    }
                    case DELETE: {
                        externalDataManagementService.getFileManagementService().delete(fileInfo);
                        if (EdsContentType.FAST_STUB.getStringValue().equals(fileInfo.getContentType())) {
                            String fastStubsKey = URLDecoder.decode(fileInfo.getFileName(), "UTF-8")
                                    .replaceAll("__", "/")
                                    .replaceAll(".json", "");
                            FastResponseConfigsHolder.INSTANCE.resetConfigByKey(fastStubsKey);
                        }
                        break;
                    }
                    default: {
                        throw new RuntimeException("Unknown file event type: " + fileInfo.getEventType());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error while message processing: {}", e.getMessage());
        }
    }

    private void putMdcFields(String projectUuid, String sessionId) {
        putIfNotBlank(MdcField.PROJECT_ID.toString(), projectUuid);
        putIfNotBlank(MdcField.SESSION_ID.toString(), sessionId);
    }

    private void putIfNotBlank(String mdcFieldName, String value) {
        if (StringUtils.isBlank(value)) {
            log.warn("Can't put blank value into MdcField '{}'", mdcFieldName);
            return;
        }
        MdcUtils.put(mdcFieldName, value);
    }

    private void postProcess(FileInfo fileInfo, File savedFile) {
        if (savedFile.exists() && EdsContentType.FAST_STUB.getStringValue().equals(fileInfo.getContentType())) {
            FastResponseConfigsHolder.INSTANCE.loadFromFile(fileInfo.getProjectUuid().toString(), savedFile);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class TriggerConfigurationResponse {

        private String errorMessage;
        private StubUser user;
        private String sessionId;
    }

}

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

package org.qubership.automation.itf.stubs.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.representations.AccessToken;
import org.qubership.automation.itf.communication.FastStubsInformation;
import org.qubership.automation.itf.communication.StubsIntegrationMessageSender;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfig;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfigsHolder;
import org.qubership.automation.itf.core.stub.fast.FastStubsTreeView;
import org.qubership.automation.itf.core.stub.fast.ResponseDescription;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.core.stub.fast.TransportConfig;
import org.qubership.automation.itf.core.util.eds.ExternalDataManagementService;
import org.qubership.automation.itf.core.util.eds.model.FileEventType;
import org.qubership.automation.itf.core.util.eds.model.FileInfo;
import org.qubership.automation.itf.core.util.eds.service.EdsContentType;
import org.qubership.automation.itf.ui.controls.FastStubsProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FastStubsService {

    private final ExternalDataManagementService externalDataManagementService;
    private final StubsIntegrationMessageSender stubsIntegrationMessageSender;
    private final ObjectMapper objectMapper;

    /**
     * Constructor.
     */
    @Autowired
    public FastStubsService(ExternalDataManagementService externalDataManagementService,
                            StubsIntegrationMessageSender stubsIntegrationMessageSender,
                            ObjectMapper objectMapper) {
        this.externalDataManagementService = externalDataManagementService;
        this.stubsIntegrationMessageSender = stubsIntegrationMessageSender;
        this.objectMapper = objectMapper;
    }

    /**
     * Find configuration for parameters.
     */
    public List<FastStubsInformation> findFastStubsConfigs(List<FastStubsInformation> fastStubsInformations,
                                                           UUID projectUuid) {
        try {
            return fastStubsInformations.stream().map(fastStubsInformation -> {

                StubEndpointConfig cfg = FastResponseConfigsHolder.INSTANCE.getConfig(projectUuid.toString(),
                        fastStubsInformation.getTransportType(), fastStubsInformation.getEndpoint());

                if (Objects.isNull(cfg)) {
                    return fastStubsInformation;
                }

                List<ResponseDescription> responseDescriptions = cfg.getConditionalResponses()
                        .stream()
                        .filter(responseDescription ->
                                responseDescription.getName().equals(fastStubsInformation.getSituationName()))
                        .collect(Collectors.toList());

                fastStubsInformation.setExist(!responseDescriptions.isEmpty()
                        || Objects.nonNull(cfg.getDefaultResponse())
                        && cfg.getDefaultResponse().getName().equals(fastStubsInformation.getSituationName()));

                return fastStubsInformation;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            String error = String.format("An error occurred while find fast stubs configurations. %s",
                    (Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage()));
            log.error(error, e);
            throw new FastStubsProcessingException(error);
        }
    }

    /**
     * Get list of fast-stub endpoints by project UUID.
     */
    public List<FastStubsTreeView> endpoints(UUID projectUuid) {
        try {
            return FastResponseConfigsHolder.INSTANCE.getEndpoints(projectUuid);
        } catch (Exception e) {
            String error = String.format("An error occurred while retrieving the list of fast stubs configuration. %s",
                    (Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage()));
            log.error(error, e);
            throw new FastStubsProcessingException(error);
        }
    }

    /**
     * Get configuration for parameters.
     */
    public StubEndpointConfig getConfiguration(String configuredEndpoint,
                                               StubEndpointConfig.TransportTypes transportTypes,
                                               UUID projectUuid) {
        try {
            return FastResponseConfigsHolder.INSTANCE
                    .getConfig(projectUuid.toString(), transportTypes.toString(), configuredEndpoint);
        } catch (Exception e) {
            String error = String.format("An error occurred while retrieving fast stubs configuration. %s",
                    (Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage()));
            log.error(error, e);
            throw new FastStubsProcessingException(error);
        }
    }

    /**
     * Update configuration from parameters.
     */
    public void updateConfiguration(StubEndpointConfig.TransportTypes transportTypes,
                                    StubEndpointConfig stubEndpointConfig, UUID projectUuid) {
        try {
            FastResponseConfig fastConfig = new FastResponseConfig();
            fastConfig.setProjectUuid(projectUuid.toString());

            TransportConfig trConfig = new TransportConfig();
            trConfig.setTransportType(transportTypes);
            trConfig.setEndpoints(Collections.singletonList(stubEndpointConfig));
            fastConfig.setTransportConfigs(Collections.singletonList(trConfig));

            String fileName = String.format("%s__%s__%s.json",
                    projectUuid, transportTypes.name(),
                    URLEncoder.encode(stubEndpointConfig.getConfiguredEndpoint(), "UTF-8"));

            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String userName = "Undefined";
            UUID userId = null;
            if (principal instanceof KeycloakPrincipal) {
                AccessToken accessToken = ((KeycloakPrincipal) principal).getKeycloakSecurityContext().getToken();
                userId = UUID.fromString(((KeycloakPrincipal) principal).getName());
                userName = accessToken.getName();
            }

            storeFileAndNotifyInstances(fileName, new ByteArrayInputStream(objectMapper.writeValueAsBytes(fastConfig)),
                    userName, userId, projectUuid);
        } catch (UnsupportedEncodingException | JsonProcessingException e) {
            String error = String.format("Error while update fast stubs configuration. %s",
                    (Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage()));
            log.error(error, e);
            throw new FastStubsProcessingException(error);
        }
    }

    /**
     * Delete configurations for list of endpoints parameter.
     *
     * @param fastStubsTreeViews List of fast stub endpoints,
     * @param projectUuid UUID of project.
     */
    public void deleteConfiguration(List<FastStubsTreeView> fastStubsTreeViews, UUID projectUuid) {
        try {
            for (FastStubsTreeView fastStubsTreeView : fastStubsTreeViews) {
                String fileName = String.format("%s__%s__%s.json",
                        projectUuid, fastStubsTreeView.getTransportType().name(),
                        URLEncoder.encode(fastStubsTreeView.getEndpoint(), "UTF-8"));

                externalDataManagementService.getExternalStorageService()
                        .delete(EdsContentType.FAST_STUB.getStringValue(), projectUuid, "", fileName);

                sendMessageToExternalDataStorageUpdateTopic(null, fileName, "",
                        EdsContentType.FAST_STUB.getStringValue(), projectUuid, null,
                        FileEventType.DELETE, stubsIntegrationMessageSender, projectUuid.toString());
            }
        } catch (Exception e) {
            String error = String.format("Error while deleting fast stubs configuration. %s",
                    (Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage()));
            log.error(error, e);
            throw new FastStubsProcessingException(error);
        }
    }

    private void storeFileAndNotifyInstances(String fileName, InputStream inputStream,
                                             String userName, UUID userId, UUID projectUuid) {
        ObjectId storedObjectId = externalDataManagementService.getExternalStorageService()
                .store(EdsContentType.FAST_STUB.getStringValue(), projectUuid,
                        userName, userId, "", fileName, inputStream);

        checkStoredObjectIdAndSendMessageToExternalDataStorageUpdateTopic(storedObjectId,
                fileName, EdsContentType.FAST_STUB.getStringValue(), projectUuid,
                inputStream, stubsIntegrationMessageSender, projectUuid.toString());
    }

    private void checkStoredObjectIdAndSendMessageToExternalDataStorageUpdateTopic(
            ObjectId storedObjectId, String fileName, String contentType, UUID projectUuid, InputStream inputStream,
            StubsIntegrationMessageSender stubsIntegrationMessageSender, String tenantId) {
        log.info("Stored object id for file '{}' is {}null", fileName, (storedObjectId == null) ? "" : "not ");
        if (storedObjectId != null) {
            sendMessageToExternalDataStorageUpdateTopic(storedObjectId, fileName, "", contentType,
                    projectUuid, null, FileEventType.UPLOAD, stubsIntegrationMessageSender, tenantId);
        } else {
            sendMessageToExternalDataStorageUpdateTopic(null, fileName, "", contentType,
                    projectUuid, inputStream, FileEventType.UPLOAD, stubsIntegrationMessageSender, tenantId);
        }
    }

    private void sendMessageToExternalDataStorageUpdateTopic(
            ObjectId objectId, String fileName, String filePath, String contentType, UUID projectUuid,
            InputStream inputStream, FileEventType eventType,
            StubsIntegrationMessageSender stubsIntegrationMessageSender, String tenantId) {
        stubsIntegrationMessageSender.sendToEdsUpdateTopic(
                new FileInfo(objectId, fileName, filePath, contentType, projectUuid, inputStream, eventType), tenantId);
    }
}

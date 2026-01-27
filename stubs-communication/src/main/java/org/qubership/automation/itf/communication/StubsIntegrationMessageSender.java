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

package org.qubership.automation.itf.communication;

import java.util.HashMap;
import java.util.Map;

import org.qubership.atp.multitenancy.core.header.CustomHeader;
import org.qubership.atp.multitenancy.interceptor.jms.AtpJmsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StubsIntegrationMessageSender {

    private final AtpJmsTemplate integrationJmsTemplate;

    @Value("${message-broker.stubs-configurator.topic}")
    private String stubsConfiguratorTopic;
    @Value("${message-broker.eds-update.topic}")
    private String externalDataStorageUpdateTopic;
    @Value("${message-broker.stubs-route-info.request.topic}")
    private String routeInfoRequestTopic;
    @Value("${message-broker.stubs-route-info.response.topic}")
    private String routeInfoResponseTopic;

    /**
     * Send message with added X_PROJECT_ID header into stubsConfiguratorTopic.
     *
     * @param message - message to send,
     * @param tenantId - tenantId (project Uuid).
     */
    public void send(Object message, Object tenantId) {
        sendMessage(message, tenantId, stubsConfiguratorTopic);
    }

    /**
     * Send message with added X_PROJECT_ID header into externalDataStorageUpdateTopic.
     *
     * @param message - message to send,
     * @param tenantId - tenantId (project Uuid).
     */
    public void sendToEdsUpdateTopic(Object message, Object tenantId) {
        sendMessage(message, tenantId, externalDataStorageUpdateTopic);
    }

    /**
     * Send message with added X_PROJECT_ID header into routeInfoRequestTopic.
     *
     * @param message - message to send,
     * @param tenantId - tenantId (project Uuid).
     */
    public void sendToRouteInfoRequestTopic(Object message, Object tenantId) {
        sendMessage(message, tenantId, routeInfoRequestTopic);
    }

    /**
     * Send message with added X_PROJECT_ID header into routeInfoResponseTopic.
     *
     * @param message - message to send,
     * @param tenantId - tenantId (project Uuid).
     */
    public void sendToRouteInfoResponseTopic(Object message, Object tenantId) {
        sendMessage(message, tenantId, routeInfoResponseTopic);
    }

    private void sendMessage(Object message, Object tenantId, String topicName) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CustomHeader.X_PROJECT_ID, tenantId);
        integrationJmsTemplate.convertAndSend(topicName, message, properties);
    }
}

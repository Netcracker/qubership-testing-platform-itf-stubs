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
import org.qubership.automation.itf.core.model.communication.message.TriggerExecutionMessage;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Component
public class TriggerExecutionMessageSender {

    private static AtpJmsTemplate executorJmsTemplate;
    private static String queue;

    /**
     * Constructor for {@code TriggerExecutionMessageSender}.
     *
     * @param queue               The name of the queue for incoming requests to the stubs executor.
     * @param executorJmsTemplate The JMS template for execution.
     */
    @Autowired
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "No way due to usage inside Camel .process() method")
    public TriggerExecutionMessageSender(@Value("${message-broker.stubs-executor-incoming-request.queue}") String queue,
                                         AtpJmsTemplate executorJmsTemplate) {
        TriggerExecutionMessageSender.executorJmsTemplate = executorJmsTemplate;
        TriggerExecutionMessageSender.queue = queue;
    }

    /**
     * Send message with added X_PROJECT_ID header.
     *
     * @param message - message to send,
     * @param tenantId - tenantId (project Uuid).
     */
    public static void send(TriggerExecutionMessage message, Object tenantId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CustomHeader.X_PROJECT_ID, tenantId);
        properties.put("traceId", MDC.get("traceId"));
        executorJmsTemplate.convertAndSend(queue, message, properties);
    }
}

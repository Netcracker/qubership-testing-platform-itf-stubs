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

package org.qubership.automation.itf.trigger.camel;

import org.qubership.atp.multitenancy.interceptor.jms.AtpJmsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Component
public class ToReportingMessageSender {


    private static AtpJmsTemplate reportsQueueJmsTemplateStatic;
    private static String reportsIntegrationQueueStatic;

    private AtpJmsTemplate reportsQueueJmsTemplate;
    private String reportsIntegrationQueue;

    /**
     * Constructor.
     *
     * @param reportsQueueJmsTemplate - JmsTemplate to send messages to reporting queue,
     * @param reportsIntegrationQueue - Name of reporting queue.
     */
    @Autowired
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "No way due to usage inside Camel .process() method")
    public ToReportingMessageSender(AtpJmsTemplate reportsQueueJmsTemplate,
                                    @Value("${message-broker.reports.queue}") String reportsIntegrationQueue) {
        this.reportsQueueJmsTemplate = reportsQueueJmsTemplate;
        this.reportsIntegrationQueue = reportsIntegrationQueue;
        reportsQueueJmsTemplateStatic = reportsQueueJmsTemplate;
        reportsIntegrationQueueStatic = reportsIntegrationQueue;
    }

    public void sendMessageToReportingQueue(Object message) {
        reportsQueueJmsTemplate.convertAndSend(reportsIntegrationQueue, message);
    }

    public static void sendMessageToReportingQueueStatic(Object message) {
        reportsQueueJmsTemplateStatic.convertAndSend(reportsIntegrationQueueStatic, message);
    }
}

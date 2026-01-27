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

package org.qubership.automation.itf.integration.config.jms.template;

import org.qubership.atp.multitenancy.interceptor.jms.AtpJmsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ExecutorJmsTemplateConfiguration {

    @Value("${message-broker.stubs-executor-incoming-request.message-time-to-live}")
    private int stubsExecutorIncomingRequestQueueMessagesTimeToLive;

    private final MessageConverter jacksonJmsMessageConverter;

    /**
     * Init JmsTemplate to send messages to executor queue.
     *
     * @param executorJmsTemplateInstance - JmsTemplate to send messages to executor queue,
     * @return Configured executorJmsTemplate
     */
    @Bean
    public AtpJmsTemplate executorJmsTemplate(AtpJmsTemplate executorJmsTemplateInstance) {
        executorJmsTemplateInstance.setMessageConverter(jacksonJmsMessageConverter);
        executorJmsTemplateInstance.setPubSubDomain(false);
        ((JmsTemplate) executorJmsTemplateInstance).setExplicitQosEnabled(true);
        ((JmsTemplate) executorJmsTemplateInstance).setTimeToLive(stubsExecutorIncomingRequestQueueMessagesTimeToLive);
        return executorJmsTemplateInstance;
    }
}

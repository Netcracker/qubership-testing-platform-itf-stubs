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

package org.qubership.automation.itf.integration.config.jms.listener.factory;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

@ComponentScan(basePackages = "org.qubership.automation.itf.communication")
@Configuration
@EnableJms
public class QueueJmsListenerContainerFactoryConfiguration {

    @Value("${message-broker.executor-stubs.listenerContainerFactory.concurrency}")
    private String executorStubsListenerConcurrency;
    @Value("${message-broker.executor-stubs.listenerContainerFactory.maxMessagesPerTask}")
    private String executorStubsListenerMaxMessagesPerTask;

    /**
     * Init factory for stub queue listener.
     *
     * @param activeMqConnectionFactory - ActiveMq connection factory,
     * @param jmsQueueListenerContainerFactoryInstance - jmsQueueListenerContainer factory,
     * @return configured stubsQueueJmsListenerContainerFactory.
     */
    @Bean
    public DefaultJmsListenerContainerFactory stubsQueueJmsListenerContainerFactory(
            ConnectionFactory activeMqConnectionFactory,
            DefaultJmsListenerContainerFactory jmsQueueListenerContainerFactoryInstance) {
        jmsQueueListenerContainerFactoryInstance.setConnectionFactory(activeMqConnectionFactory);
        jmsQueueListenerContainerFactoryInstance.setPubSubDomain(false);
        jmsQueueListenerContainerFactoryInstance.setConcurrency(executorStubsListenerConcurrency);
        jmsQueueListenerContainerFactoryInstance.setAutoStartup(false);
        if (executorStubsListenerMaxMessagesPerTask != null && !executorStubsListenerMaxMessagesPerTask.isEmpty()) {
            int maxMessagesPerTask = Integer.parseInt(executorStubsListenerMaxMessagesPerTask);
            if (maxMessagesPerTask == -1 || maxMessagesPerTask > 0) {
                jmsQueueListenerContainerFactoryInstance.setMaxMessagesPerTask(maxMessagesPerTask);
            }
        }
        jmsQueueListenerContainerFactoryInstance.setCacheLevel(DefaultMessageListenerContainer.CACHE_CONSUMER);
        jmsQueueListenerContainerFactoryInstance.setSessionAcknowledgeMode(ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);
        return jmsQueueListenerContainerFactoryInstance;
    }
}

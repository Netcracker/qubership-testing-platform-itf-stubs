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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

@ComponentScan(basePackages = "org.qubership.automation.itf.communication")
@Configuration
@EnableJms
public class TopicJmsListenerContainerFactoryConfiguration {

    /**
     * Init factory for stub topics listener.
     *
     * @param activeMqConnectionFactory - factory to create ActiveMq connections,
     * @param jmsTopicListenerContainerFactoryInstance - factory to manage jmsTopicListeners.
     * @return factory created and configured.
     */
    @Bean
    public DefaultJmsListenerContainerFactory stubsTopicJmsListenerContainerFactory(
            ConnectionFactory activeMqConnectionFactory,
            DefaultJmsListenerContainerFactory jmsTopicListenerContainerFactoryInstance) {
        jmsTopicListenerContainerFactoryInstance.setConnectionFactory(activeMqConnectionFactory);
        jmsTopicListenerContainerFactoryInstance.setPubSubDomain(true);
        jmsTopicListenerContainerFactoryInstance.setConcurrency("1-1");
        jmsTopicListenerContainerFactoryInstance.setAutoStartup(false);
        return jmsTopicListenerContainerFactoryInstance;
    }
}

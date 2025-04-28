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

package org.qubership.automation.itf.trigger;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.qubership.atp.multitenancy.interceptor.jms.AtpJmsTemplate;
import org.qubership.automation.itf.core.util.engine.TemplateEngine;
import org.qubership.automation.itf.core.util.engine.TemplateEngineFactory;
import org.qubership.automation.itf.trigger.template.velocity.VelocityTemplateEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class FastStubsConfiguration {

    @Value("${message-broker.url}")
    private String brokerUrl;
    @Value("${message-broker.reports.useCompression}")
    private String reportsUseCompression;
    @Value("${message-broker.reports.useAsyncSend}")
    private String reportsUseAsyncSend;
    @Value("${message-broker.reports.maxThreadPoolSize}")
    private int maxThreadPoolSize;
    @Value("${message-broker.reports.connectionsPoolSize}")
    private int connectionsPoolSize;
    @Value("${message-broker.reports.message-time-to-live:180000}")
    private int reportingQueueMessagesTimeToLive;

    @Bean
    public VelocityTemplateEngine fastStubVelocityTemplateEngine() {
        return new VelocityTemplateEngine();
    }

    /**
     * Configure reportsActiveMqConnectionFactory to manage connections to reporting queue.
     *
     * @return created and configured reportsActiveMqConnectionFactory.
     */
    @Bean
    public ActiveMQConnectionFactory reportsActiveMqConnectionFactory() {
        ActiveMQConnectionFactory activeMqConnectionFactory = new ActiveMQConnectionFactory();
        activeMqConnectionFactory.setBrokerURL(brokerUrl);
        activeMqConnectionFactory.setMaxThreadPoolSize(maxThreadPoolSize);
        activeMqConnectionFactory.setUseCompression(Boolean.parseBoolean(reportsUseCompression));
        activeMqConnectionFactory.setUseAsyncSend(Boolean.parseBoolean(reportsUseAsyncSend));
        activeMqConnectionFactory.setAlwaysSyncSend(!activeMqConnectionFactory.isUseAsyncSend());
        return activeMqConnectionFactory;
    }

    /**
     * Configure reportsPooledConnectionFactory to manage connection pool to reporting queue.
     *
     * @param reportsActiveMqConnectionFactory - ActiveMq connection factory,
     * @return created and configured reportsPooledConnectionFactory.
     */
    @Bean
    public ConnectionFactory reportsPooledConnectionFactory(
            ActiveMQConnectionFactory reportsActiveMqConnectionFactory) {
        PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory();
        pooledConnectionFactory.setConnectionFactory(reportsActiveMqConnectionFactory);
        pooledConnectionFactory.setMaxConnections(connectionsPoolSize);
        pooledConnectionFactory.setCreateConnectionOnStartup(true);
        return pooledConnectionFactory;
    }

    @Bean(name = "TemplateEngineFactory")
    public TemplateEngine getTemplateEngineFactory() {
        TemplateEngineFactory.init(fastStubVelocityTemplateEngine());
        return TemplateEngineFactory.get();
    }

    /**
     * Configure reportsQueueJmsTemplate.
     *
     * @param reportingJmsTemplateInstance - JmsTemplate to sent messages to reporting,
     * @return configured reportsQueueJmsTemplate.
     */
    @Bean
    public AtpJmsTemplate reportsQueueJmsTemplate(AtpJmsTemplate reportingJmsTemplateInstance) {
        reportingJmsTemplateInstance.setPubSubDomain(false);
        if (reportingJmsTemplateInstance instanceof JmsTemplate) {
            ((JmsTemplate) reportingJmsTemplateInstance).setExplicitQosEnabled(true);
            ((JmsTemplate) reportingJmsTemplateInstance).setTimeToLive(reportingQueueMessagesTimeToLive);
        }
        return reportingJmsTemplateInstance;
    }
}

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

package org.qubership.automation.itf.integration.config.jms.template.instance;

import javax.jms.ConnectionFactory;

import org.jetbrains.annotations.NotNull;
import org.qubership.automation.itf.integration.config.jms.DefaultJmsTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@ConditionalOnProperty(value = {"atp.multi-tenancy.enabled"}, havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class DefaultJmsTemplateInstancesConfiguration {

    private final ConnectionFactory activeMqConnectionFactory;
    private final ConnectionFactory reportsPooledConnectionFactory;

    @Bean
    public DefaultJmsTemplate integrationJmsTemplateInstance() {
        return initDefaultJmsTemplate(activeMqConnectionFactory);
    }

    @Bean
    public DefaultJmsTemplate executorJmsTemplateInstance() {
        return initDefaultJmsTemplate(activeMqConnectionFactory);
    }

    @Bean
    public DefaultJmsTemplate reportingJmsTemplateInstance() {
        return initDefaultJmsTemplate(reportsPooledConnectionFactory);
    }

    @NotNull
    private DefaultJmsTemplate initDefaultJmsTemplate(ConnectionFactory connectionFactory) {
        DefaultJmsTemplate defaultJmsTemplate = new DefaultJmsTemplate();
        defaultJmsTemplate.setConnectionFactory(connectionFactory);
        return defaultJmsTemplate;
    }
}

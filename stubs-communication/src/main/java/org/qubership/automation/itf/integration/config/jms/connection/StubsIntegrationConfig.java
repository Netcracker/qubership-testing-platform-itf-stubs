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

package org.qubership.automation.itf.integration.config.jms.connection;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@ComponentScan(basePackages = "org.qubership.automation.itf.communication")
@Configuration
@EnableJms
public class StubsIntegrationConfig {

    @Value("${message-broker.url}")
    private String brokerUrl;
    @Value("${message-broker.queuePrefetch}")
    private int queuePrefetch;

    /**
     * ActiveMq Connection Factory Bean Constructor.
     */
    @Bean
    public ActiveMQConnectionFactory activeMqConnectionFactory() {
        ActiveMQConnectionFactory activeMqConnectionFactory = new ActiveMQConnectionFactory();
        activeMqConnectionFactory.setBrokerURL(brokerUrl);
        activeMqConnectionFactory.setUseAsyncSend(true);
        activeMqConnectionFactory.setUseRetroactiveConsumer(true);
        activeMqConnectionFactory.setTrustAllPackages(true);
        ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setQueuePrefetch(queuePrefetch);
        activeMqConnectionFactory.setPrefetchPolicy(prefetchPolicy);
        return activeMqConnectionFactory;
    }

    /**
     * MessageConverter Constructor.
     */
    @Bean
    public MessageConverter jacksonJmsMessageConverter(@Qualifier("jmsMessageConverterObjectMapper")
                                                                   ObjectMapper jmsMessageConverterObjectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        converter.setObjectMapper(jmsMessageConverterObjectMapper);
        return converter;
    }

    /**
     * ObjectMapper for executor-stubs\stubs-executor jms messages.
     *
     * @return jmsMessageConverterObjectMapper
     */
    @Bean
    public ObjectMapper jmsMessageConverterObjectMapper() {
        ObjectMapper jmsMessageConverterObjectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setFailOnUnknownId(false);
        jmsMessageConverterObjectMapper.setFilterProvider(filterProvider);
        return jmsMessageConverterObjectMapper;
    }

    /**
     * Default objectMapper bean without any configs.
     * This mapper is required for new MappingJackson2MessageConverter() in the
     * {@link StubsIntegrationConfig#jacksonJmsMessageConverter},
     * when creates new instance, but later we set our jmsMessageConverterObjectMapper.
     *
     * @return new ObjectMapper().
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

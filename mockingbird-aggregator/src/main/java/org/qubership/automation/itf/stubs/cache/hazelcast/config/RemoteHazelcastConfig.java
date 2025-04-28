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

package org.qubership.automation.itf.stubs.cache.hazelcast.config;

import static org.qubership.automation.itf.core.util.constants.CacheNames.ATP_ITF_PROJECT_SETTINGS;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Import(CommonHazelcastConfig.class)
@ConditionalOnProperty(value = "hazelcast.cache.enabled", havingValue = "true")
@Slf4j
public class RemoteHazelcastConfig {

    /**
     * Create {@link HazelcastInstance} bean.
     *
     * @return bean
     */
    @Bean(name = "hazelcastClient")
    public HazelcastInstance hazelcastClient(@Qualifier("clientConfig") ClientConfig clientConfig) {
        HazelcastInstance hazelcastClient = HazelcastClient.getOrCreateHazelcastClient(clientConfig);
        addProjectSettingsNearCacheConfig(clientConfig);
        return hazelcastClient;
    }

    private void addProjectSettingsNearCacheConfig(ClientConfig clientConfig) {
        NearCacheConfig projectSettingsNearCacheConfig = clientConfig.getNearCacheConfig(ATP_ITF_PROJECT_SETTINGS);
        if (projectSettingsNearCacheConfig == null) {
            projectSettingsNearCacheConfig = new NearCacheConfig(ATP_ITF_PROJECT_SETTINGS)
                    .setInMemoryFormat(InMemoryFormat.OBJECT);
            log.info("Hazelcast Near Cache config ATP_ITF_PROJECT_SETTINGS is created");
        }
        clientConfig.addNearCacheConfig(projectSettingsNearCacheConfig);
    }
}

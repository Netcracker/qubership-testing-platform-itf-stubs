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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;

@Configuration
@ConditionalOnProperty(value = "hazelcast.cache.enabled", havingValue = "false")
@Slf4j
public class LocalHazelcastConfig {

    /**
     * Create {@link HazelcastInstance} bean.
     *
     * @return bean
     */
    @Bean(name = "hazelcastClient")
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setClusterName("local-itf-stubs-hazelcast-cluster");
        config.setInstanceName("local-itf-stubs-hc-cache-instance");
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        addLocalMapConfigs(hazelcastInstance);
        return hazelcastInstance;
    }

    /**
     * Create new empty Imap for local using...just to start atp-itf-stubs on local machine.
     *
     * @param hazelcastInstance local HazelcastInstance
     */
    private void addLocalMapConfigs(HazelcastInstance hazelcastInstance) {
        Config config = hazelcastInstance.getConfig();
        try {
            log.info("Try to add config for '{}' local hazelcast map", ATP_ITF_PROJECT_SETTINGS);
            config.addMapConfig(new MapConfig().setName(ATP_ITF_PROJECT_SETTINGS));
            log.info("Config for '{}' local hazelcast map was added", ATP_ITF_PROJECT_SETTINGS);
        } catch (Exception exception) {
            log.warn("Map '{}' already created on local Hazelcast cluster side. "
                    + "It's not possible to change existing map config.", ATP_ITF_PROJECT_SETTINGS, exception);
        }
    }
}

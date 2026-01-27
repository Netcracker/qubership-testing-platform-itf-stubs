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

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableCaching
@Configuration
public class CommonHazelcastConfig {

    /**
     * Hazelcast Cache Manager constructor.
     *
     * @param hazelcastClient - HazelcastInstance parameter.
     * @return CacheManager object.
     */
    @Bean(name = "hazelcastCacheManager")
    public CacheManager hazelcastCacheManager(@Qualifier("hazelcastClient") HazelcastInstance hazelcastClient) {
        List<Cache> caches = new ArrayList<>();
        IMap<Object, Object> projectSettings = hazelcastClient.getMap(ATP_ITF_PROJECT_SETTINGS);
        caches.add(new ConcurrentMapCache(ATP_ITF_PROJECT_SETTINGS, projectSettings, false));
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(caches);
        return cacheManager;
    }
}

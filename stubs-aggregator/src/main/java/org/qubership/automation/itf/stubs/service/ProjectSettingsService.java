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

package org.qubership.automation.itf.stubs.service;

import java.util.Map;

import org.qubership.automation.itf.core.util.constants.CacheNames;
import org.qubership.automation.itf.core.util.services.projectsettings.AbstractProjectSettingsService;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PLEASE NOTE:
 * When you work with project settings in atp-itf-stubs you can't fill anything into it (even it is a remote
 * hazelcast instance), this process do atp-itf-executor service which have db.
 * atp-itf-stubs can get actual sync with atp-itf-executor service and remote cluster values when it connected to
 * remote cluster only. (hazelcast.cache.enabled=true)
 * Also, in atp-itf-stubs you can use only "get with default values" methods, because this Imap
 * doesn't have db connection to get values if remote cluster return null or empty value (we don't have db in
 * atp-itf-stubs service)
 */
@RequiredArgsConstructor
@Slf4j
public class ProjectSettingsService extends AbstractProjectSettingsService {

    private final HazelcastInstance hazelcastClient;

    @Override
    protected IMap<String, Map<String, String>> getProjectSettingsCache() {
        return hazelcastClient.getMap(CacheNames.ATP_ITF_PROJECT_SETTINGS);
    }
}

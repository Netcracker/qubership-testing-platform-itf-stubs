/*
 * # Copyright 2024-2026 NetCracker Technology Corporation
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

package org.qubership.automation.itf.ui.config;

import java.util.Set;

import org.qubership.automation.itf.activation.impl.OnDestroyTriggersActivationService;
import org.qubership.automation.itf.activation.impl.OnStartupTriggersActivationService;
import org.qubership.automation.itf.utils.loader.TriggerClassLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TriggerContext {

    private final OnStartupTriggersActivationService onStartupTriggersActivationService;
    private final OnDestroyTriggersActivationService onDestroyTriggersActivationService;
    private final TriggerClassLoader triggerClassLoader;

    @Value("${trigger.folder}")
    private String triggerFolder;

    @Value("${trigger.lib}")
    private String triggersCustomLibFolder;

    /**
     * Constructor.
     */
    @Autowired
    public TriggerContext(OnStartupTriggersActivationService onStartupTriggersActivationService,
                          OnDestroyTriggersActivationService onDestroyTriggersActivationService,
                          TriggerClassLoader triggerClassLoader) {
        this.onStartupTriggersActivationService = onStartupTriggersActivationService;
        this.onDestroyTriggersActivationService = onDestroyTriggersActivationService;
        this.triggerClassLoader = triggerClassLoader;
    }

    /**
     * Perform init of transport modules.
     * In addition to bundled libraries, custom libraries from 'triggersCustomLibFolder' are loaded too.
     */
    @PostConstruct
    public void init() {
        log.info("Start transport modules loading...");
        try {
            triggerClassLoader.load(triggerFolder, triggersCustomLibFolder);
            log.info("Transport modules are loaded successfully.");
        } catch (Exception e) {
            log.error("Error initialing transport modules", e);
        }
    }

    /**
     * Activate triggers.
     * Activation process is organized by transport types, in order to primarily activate
     * more often used (and quickly activated) REST/SOAP triggers.
     */
    public Thread activateTriggers() {
        Thread thread = new Thread(() -> {
            Set<String> triggerTypes = triggerClassLoader.getClassLoaderHolder().keySet();
            onStartupTriggersActivationService.perform(triggerTypes);
        });
        thread.start();
        return thread;
    }

    public void destroy() {
        triggerClassLoader.cleanClassLoaders();
        onDestroyTriggersActivationService.perform();
    }

    public boolean isListForActivationReceived() {
        return onStartupTriggersActivationService.isListForActivationReceived();
    }
}

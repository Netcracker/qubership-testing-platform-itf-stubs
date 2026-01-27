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

package org.qubership.automation.itf.activation;

import java.util.HashMap;
import java.util.Map;

import org.qubership.automation.itf.activation.impl.ActivationService;
import org.qubership.automation.itf.activation.impl.EnvironmentActivationService;
import org.qubership.automation.itf.activation.impl.OnStartupTriggersActivationService;
import org.qubership.automation.itf.activation.impl.SystemServerTriggerActivationService;
import org.qubership.automation.itf.activation.impl.TriggerActivationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TriggerServiceFactory {

    private static Map<ActivationServiceConstants, ActivationService> activationServiceMap = new HashMap<>();

    /**
     * Constructor.
     */
    @Autowired
    public TriggerServiceFactory(TriggerActivationService triggerActivationService,
                                 EnvironmentActivationService environmentActivationService,
                                 OnStartupTriggersActivationService onStartupTriggersActivationService,
                                 SystemServerTriggerActivationService systemServerTriggerActivationService) {
        activationServiceMap.put(ActivationServiceConstants.TRIGGER, triggerActivationService);
        activationServiceMap.put(ActivationServiceConstants.ENVIRONMENT, environmentActivationService);
        activationServiceMap.put(ActivationServiceConstants.ON_STARTUP, onStartupTriggersActivationService);
        activationServiceMap.put(ActivationServiceConstants.SYNC, systemServerTriggerActivationService);
    }

    public ActivationService getService(String type) {
        return activationServiceMap.get(ActivationServiceConstants.getByValue(type));
    }
}

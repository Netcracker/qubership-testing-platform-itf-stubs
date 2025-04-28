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

package org.qubership.automation.itf.activation.impl;

import org.qubership.automation.itf.activation.TriggersCache;
import org.qubership.automation.itf.core.util.exception.TriggerException;
import org.qubership.automation.itf.trigger.camel.Trigger;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnDestroyTriggersActivationService {

    private final TriggersCache triggersCache;

    /**
     * TODO Add JavaDoc.
     */
    public void perform() {
        log.info("Deactivating all triggers...");
        triggersCache.applyConsumerToEach(this::deactivateTrigger);
        triggersCache.clear();
        log.info("All triggers were successfully deactivated.");
    }

    private void deactivateTrigger(Trigger trigger) {
        String triggerName = trigger.getTriggerConfigurationDescriptor().getName() + " ["
                + trigger.getTriggerConfigurationDescriptor().getId() + "]";
        try {
            log.info("Deactivating trigger '{}'...", triggerName);
            trigger.deactivate();
            log.info("Trigger '{}' was successfully deactivated...", triggerName);
        } catch (TriggerException e) {
            log.error("Could not deactivate trigger '{}'.", triggerName, e);
        }
    }
}



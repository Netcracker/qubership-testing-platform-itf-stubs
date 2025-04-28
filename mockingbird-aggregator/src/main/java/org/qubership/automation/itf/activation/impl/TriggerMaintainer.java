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

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentMap;

import org.qubership.automation.itf.activation.TriggersCache;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.exception.TriggerException;
import org.qubership.automation.itf.trigger.camel.Trigger;
import org.qubership.automation.itf.utils.loader.TriggerClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TriggerMaintainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerMaintainer.class);
    @Value("${test.server.availability}")
    private boolean testServerAvailability;
    private TriggerClassLoader triggerClassLoader;
    private TriggersCache triggersCache;

    @Autowired
    public TriggerMaintainer(TriggersCache triggersCache, TriggerClassLoader triggerClassLoader) {
        this.triggersCache = triggersCache;
        this.triggerClassLoader = triggerClassLoader;
    }

    /**
     * TODO Add JavaDoc.
     */
    public void activate(TriggerSample triggerSample, ConcurrentMap<String, Boolean> availableServers)
            throws TriggerException {
        LOGGER.info("Activating trigger [{}]...", triggerSample.getTriggerId());
        if (triggersCache.get(triggerSample.getTriggerId()) != null) {
            LOGGER.warn("Trigger [{}] already activated, trying to reactivate 1st...", triggerSample.getTriggerId());
            deactivate(triggerSample);
        }
        Trigger trigger = createNewTrigger(triggerSample);
        if (testServerAvailability && !trigger.checkIfServerAvailable(availableServers)) {
            LOGGER.warn("Trigger [{}] activation skipped (server is unavailable)", triggerSample.getTriggerId());
            return;
        }
        trigger.activate();
        triggersCache.put(triggerSample.getTriggerId(), trigger);
        LOGGER.info("Trigger [{}] is activated.", triggerSample.getTriggerId());
    }

    /**
     * TODO Add JavaDoc.
     */
    public void deactivate(TriggerSample triggerSample) throws TriggerException {
        LOGGER.info("Deactivating trigger [{}]...", triggerSample.getTriggerId());
        Trigger trigger = triggersCache.get(triggerSample.getTriggerId());
        if (trigger != null) {
            trigger.deactivate();
            triggersCache.remove(triggerSample.getTriggerId());
            LOGGER.info("Trigger [{}] is deactivated.", triggerSample.getTriggerId());
        } else {
            LOGGER.warn("Cannot deactivate trigger [{}], trigger not found.", triggerSample.getTriggerId());
        }
    }

    /**
    *  Creates new Trigger based on TriggerSample.
    */
    public Trigger createNewTrigger(TriggerSample triggerSample) throws TriggerException {
        try {
            String triggerClass = TransportTypeToTriggerClassMapping
                    .getTriggerClassName(triggerSample.getTransportType());
            StorableDescriptor triggerStorableDescriptor = createStorableDescriptorFromTriggerSample(triggerSample);
            return triggerClassLoader
                    .getConstructorByClass(triggerClass, StorableDescriptor.class, ConnectionProperties.class)
                    .newInstance(triggerStorableDescriptor, triggerSample.getTriggerProperties());
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException | InvocationTargetException e) {
            throw new TriggerException("Error while trying to create trigger of type "
                    + TransportTypeToTriggerClassMapping.getTriggerClassName(triggerSample.getTransportType())
                    + " for transport " + triggerSample.getTriggerTypeName(), e);
        }
    }

    private StorableDescriptor createStorableDescriptorFromTriggerSample(TriggerSample triggerSample) {
        return new StorableDescriptor(triggerSample.getTriggerId(), triggerSample.getTriggerName(),
                triggerSample.getProjectUuid(), triggerSample.getProjectId());
    }
}

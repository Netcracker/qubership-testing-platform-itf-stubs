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

package org.qubership.automation.itf.trigger.camel.inbound;

import java.util.UUID;

import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.trigger.camel.AbstractTriggerImpl;
import org.qubership.automation.itf.trigger.camel.CamelContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCamelTrigger extends AbstractTriggerImpl implements CamelContextProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractCamelTrigger.class);

    {
        start();
    }

    private String id = "inb" + UUID.randomUUID();

    protected AbstractCamelTrigger(StorableDescriptor triggerConfigurationDescriptor,
                                   ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    public String getId() {
        return this.id;
    }

    public void generateNewId() {
        this.id = "inb" + UUID.randomUUID();
    }
}

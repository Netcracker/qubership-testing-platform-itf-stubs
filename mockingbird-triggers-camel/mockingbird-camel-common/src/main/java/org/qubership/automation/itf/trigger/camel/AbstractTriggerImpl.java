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

package org.qubership.automation.itf.trigger.camel;

import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.constants.TriggerState;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.exception.Exceptions;
import org.qubership.automation.itf.core.util.exception.TriggerException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public abstract class AbstractTriggerImpl implements Trigger {

    private TriggerState state = TriggerState.INACTIVE;
    private final StorableDescriptor triggerConfigurationDescriptor;
    private ConnectionProperties connectionProperties;
    private String error;

    protected AbstractTriggerImpl(StorableDescriptor triggerConfigurationDescriptor,
                                  ConnectionProperties connectionProperties) {
        this.triggerConfigurationDescriptor = triggerConfigurationDescriptor;
        this.connectionProperties = connectionProperties;
    }

    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "Checked; it's correct")
    public final TriggerState getState() {
        return state;
    }

    /**
     * Synchronized activation of a trigger.
     */
    public final synchronized void activate() throws TriggerException {
        error = null;
        state = TriggerState.STARTING;
        try {
            activateSpecificTrigger();
            state = TriggerState.ACTIVE;
        } catch (Throwable e) {
            state = TriggerState.ERROR;
            error = Exceptions.getMessagesOnly(e);
            //any transports hangs in activation state. Let's avoid it.
            //so, if we are failed at start, probably ITF has created any objects and objects has taken resources
            //let's call deactivation to cleanup resources
            try {
                deactivateSpecificTrigger();
            } catch (Exception er) {
                /*do nothing. We don't care about deactivation functions in activation*/
            }
            throw new TriggerException(String.format("Unable to activate trigger for configuration %s",
                    triggerConfigurationDescriptor.getName()), e);
        }
    }

    protected abstract void activateSpecificTrigger() throws Exception;

    /**
     * Synchronized deactivation of a trigger.
     */
    public final synchronized void deactivate() throws TriggerException {
        error = null;
        state = TriggerState.SHUTTING_DOWN;
        try {
            deactivateSpecificTrigger();
            state = TriggerState.INACTIVE;
        } catch (Exception e) {
            state = TriggerState.ERROR;
            error = ExceptionUtils.getStackTrace(e);
            throw new TriggerException(String.format("Unable to deactivate trigger for configuration %s",
                    triggerConfigurationDescriptor.getName()), e);
        }
    }

    protected abstract void deactivateSpecificTrigger() throws Exception;

    @Override
    public StorableDescriptor getTriggerConfigurationDescriptor() {
        return triggerConfigurationDescriptor;
    }

    protected ConnectionProperties getConnectionProperties() {
        return connectionProperties;
    }

    protected void setConnectionProperties(ConnectionProperties connectionProperties) {
        this.connectionProperties = connectionProperties;
    }

    @Override
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "Checked; it's correct")
    public String getError() {
        return error;
    }

    @Override
    public void applyConfiguration(ConnectionProperties connectionProperties) throws TriggerException {
        if (state.isOn()) {
            deactivate();
        }
        applyTriggerProperties(connectionProperties);
        if (state.isOn()) {
            activate();
        }
    }

    protected abstract void applyTriggerProperties(ConnectionProperties connectionProperties) throws TriggerException;

    protected String getPrefixWithProjectUuid() {
        return "/" + getTriggerConfigurationDescriptor().getProjectUuid();
    }

    @Override
    public boolean checkIfServerAvailable(Map<String, Boolean> availableServers) {
        return true;
    }
}

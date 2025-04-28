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

package org.qubership.automation.itf.trigger.jms;

import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.camel.Component;
import org.apache.camel.component.jms.DefaultJmsMessageListenerContainer;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConsumer;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.trigger.camel.inbound.AbstractCamelTrigger;
import org.qubership.automation.itf.trigger.jms.inbound.JmsRoutingBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.ExponentialBackOff;

import com.google.common.base.Strings;

public class JmsTrigger extends AbstractCamelTrigger {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsTrigger.class);

    private static final long JMS_CONNECTION_RECOVERY_INTERVAL_DEFAULT;
    private static final long JMS_CONNECTION_MAX_ATTEMPTS_DEFAULT;
    private static final long JMS_CONNECTION_MAX_ELAPSED_TIME_DEFAULT;

    static {
        JMS_CONNECTION_RECOVERY_INTERVAL_DEFAULT = Long.parseLong(
                Config.getConfig().getStringOrDefault("jms.connection.recovery.interval", "5000"),
                10);
        JMS_CONNECTION_MAX_ATTEMPTS_DEFAULT = computeMaxAttempts(
                Config.getConfig().getStringOrDefault("jms.connection.max.attempts", "3600"));
        JMS_CONNECTION_MAX_ELAPSED_TIME_DEFAULT = Long.parseLong(
                Config.getConfig().getStringOrDefault("jms.connection.max.elapsed.time", "18000000"),
                10);
    }

    /**
     * Trigger constructor.
     */
    public JmsTrigger(StorableDescriptor triggerConfigurationDescriptor, ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        InitialContext initialContext = null;
        try {
            /* Check if the component was added before.
             * If Yes - Fully remove it ignoring exceptions
             *   in case of some errors while activating jmstrigger remains in 'InActive' state
             *   but CAMEL_CONTEXT contains the component for it.
             *   So later when we try to activate trigger (and addComponent to CAMEL_CONTEXT) - we are
             * facing the exception:
             * IllegalArgumentException: Cannot add component as it's already previously added:
             * inbc7c2eab4-d352-49d1-aac7-e38a49bd43f9
             */
            Component prevAdded = CAMEL_CONTEXT.hasComponent(getId());
            if (prevAdded != null) {
                try {
                    LOGGER.info("Before activation: {} - CAMEL_CONTEXT already has component with the same ID {}. It "
                            + "will be deactivated.", this, getId());
                    deactivateSpecificTrigger();
                } catch (Exception ex) {
                    // Do nothing. If there are errors preventing activation let them appear while activation itself
                    LOGGER.debug("Trigger {} deactivation before its activation - ignoring the exception:",
                            getId(), ex);
                }
            }

            initialContext = createContext();
            JmsComponent jmsComponent = getComponent(initialContext);
            String destinationName = getConnectionProperties().obtain(JmsConstants.DESTINATION);
            Destination destination = JmsHelper.isPathToDestination(destinationName)
                    ? null : getDestination(initialContext, destinationName);
            initialContext.close();

            CAMEL_CONTEXT.addComponent(getId(), jmsComponent);

            CAMEL_CONTEXT.addRoutes(
                    new JmsRoutingBuilder(getConnectionProperties(), getTriggerConfigurationDescriptor(),
                            destination, jmsComponent, getId()));

            Object jmsConnectionRecoveryIntervalConfigured = getConnectionProperties()
                    .obtain(JmsConstants.RECOVERY_INTERVAL);
            long jmsConnectionRecoveryInterval = jmsConnectionRecoveryIntervalConfigured != null
                    && !StringUtils.isEmpty((String) jmsConnectionRecoveryIntervalConfigured)
                    ? Long.parseLong((String) jmsConnectionRecoveryIntervalConfigured, 10) :
                    JMS_CONNECTION_RECOVERY_INTERVAL_DEFAULT;
            if (jmsConnectionRecoveryInterval < JMS_CONNECTION_RECOVERY_INTERVAL_DEFAULT) {
                jmsConnectionRecoveryInterval = JMS_CONNECTION_RECOVERY_INTERVAL_DEFAULT;
            }

            Object jmsConnectionMaxAttemptsConfigured = getConnectionProperties().obtain(JmsConstants.MAX_ATTEMTPS);
            long jmsConnectionMaxAttempts = jmsConnectionMaxAttemptsConfigured != null
                    && !StringUtils.isEmpty((String) jmsConnectionMaxAttemptsConfigured)
                    ? computeMaxAttempts((String) jmsConnectionMaxAttemptsConfigured) :
                    JMS_CONNECTION_MAX_ATTEMPTS_DEFAULT;
            if (jmsConnectionMaxAttempts < 0 || jmsConnectionMaxAttempts > JMS_CONNECTION_MAX_ATTEMPTS_DEFAULT * 2) {
                jmsConnectionMaxAttempts = JMS_CONNECTION_MAX_ATTEMPTS_DEFAULT;
            }

            long jmsConnectionMaxElapsedTime = jmsConnectionMaxAttempts > JMS_CONNECTION_MAX_ATTEMPTS_DEFAULT
                    ? JMS_CONNECTION_MAX_ELAPSED_TIME_DEFAULT * 2 : JMS_CONNECTION_MAX_ELAPSED_TIME_DEFAULT;

            ExponentialBackOff backOff = new ExponentialBackOff();
            backOff.setInitialInterval(jmsConnectionRecoveryInterval);
            backOff.setMultiplier(ExponentialBackOff.DEFAULT_MULTIPLIER);
            backOff.setMaxInterval(ExponentialBackOff.DEFAULT_MAX_INTERVAL * 3);
            backOff.setMaxElapsedTime(jmsConnectionMaxElapsedTime);

            ((DefaultJmsMessageListenerContainer) ((JmsConsumer) CAMEL_CONTEXT.getRoute(getId()).getConsumer())
                    .getListenerContainer()).setBackOff(backOff);
        } finally {
            if (initialContext != null) {
                initialContext.close();
            }
        }
    }

    private static long computeMaxAttempts(String configValue) {
        return configValue.equalsIgnoreCase("unlimited")
                ? Long.MAX_VALUE
                : Long.parseLong(configValue, 10);
    }

    private Destination getDestination(InitialContext initialContext, String destinationName) throws NamingException {
        String escDestinationName = StringEscapeUtils.escapeHtml4(destinationName);
        return (Destination) initialContext.lookup(escDestinationName);
    }

    private JmsComponent getComponent(InitialContext initialContext) throws NamingException {
        String strConnectionFactory = String.valueOf(getConnectionProperties().get(JmsConstants.CONNECTION_FACTORY));
        String escConnectionFactory = StringEscapeUtils.escapeHtml4(strConnectionFactory);
        ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(escConnectionFactory);
        return JmsComponent.jmsComponent(connectionFactory);
    }

    @Override
    protected void deactivateSpecificTrigger() throws Exception {
        CAMEL_CONTEXT.stopRoute(getId());
        CAMEL_CONTEXT.removeRoute(getId());
        CAMEL_CONTEXT.removeComponent(getId());
        LOGGER.info("{} [{}] is deactivated successfully", CAMEL_CONTEXT, getId());
        generateNewId();
    }

    private InitialContext createContext() throws NamingException {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        Properties env = new Properties();
        JmsHelper.putSafe(env, Context.INITIAL_CONTEXT_FACTORY,
                getConnectionProperties().get(JmsConstants.INITIAL_CONTEXT_FACTORY));
        JmsHelper.putSafe(env, Context.SECURITY_PRINCIPAL, getConnectionProperties().get(JmsConstants.PRINCIPAL));
        JmsHelper.putSafe(env, Context.SECURITY_CREDENTIALS, getConnectionProperties().get(JmsConstants.CREDENTIALS));
        JmsHelper.putSafe(env, Context.PROVIDER_URL, getConnectionProperties().get(JmsConstants.PROVIDER_URL));
        JmsHelper.putSafe(env, Context.SECURITY_AUTHENTICATION,
                getConnectionProperties().get(JmsConstants.AUTHENTICATION));
        String messageSelector = (String) getConnectionProperties().get(JmsConstants.MESSAGE_SELECTOR);
        if (!Strings.isNullOrEmpty(messageSelector)) {
            JmsHelper.putSafe(env, JmsConstants.MESSAGE_SELECTOR, messageSelector);
        }
        if (getConnectionProperties().get(JmsConstants.ADDITIONAL_JNDI_PROPERTIES) != null) {
            env.putAll((Map<?, ?>) getConnectionProperties().get(JmsConstants.ADDITIONAL_JNDI_PROPERTIES));
        }
        return new InitialContext(env);
    }

    @Override
    public void applyTriggerProperties(ConnectionProperties connectionProperties) {
        setConnectionProperties(connectionProperties);
    }

    @Override
    public String toString() {
        return "JmsTrigger{"
                + "destination='" + getConnectionProperties().get(JmsConstants.DESTINATION)
                + "', providerUrl='" + getConnectionProperties().get(JmsConstants.PROVIDER_URL)
                + "', triggerConfiguration='" + getTriggerConfigurationDescriptor().getName() + "'}";
    }

    public boolean checkIfServerAvailable(Map<String, Boolean> availableServers,
                                          ConnectionProperties connectionProperties) {
        String providerUrl = (String) connectionProperties.get(JmsConstants.PROVIDER_URL);
        return availableServers.computeIfAbsent(providerUrl, key -> testConnection(providerUrl, connectionProperties));
    }

    private boolean testConnection(String providerUrl, ConnectionProperties connectionProperties) {
        Context context = null;
        try {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            Hashtable<String, Object> env = new Hashtable<>();
            env.put(Context.PROVIDER_URL, providerUrl);
            env.put(Context.INITIAL_CONTEXT_FACTORY, connectionProperties.get(JmsConstants.INITIAL_CONTEXT_FACTORY));
            env.put("weblogic.jndi.createIntermediateContexts", "true");
            context = new InitialContext(env);
            TopicConnectionFactory tconFactory = (TopicConnectionFactory) context
                    .lookup("javax.jms.TopicConnectionFactory");
            TopicConnection con = tconFactory.createTopicConnection();
            con.close();
            return Boolean.TRUE;
        } catch (NamingException | JMSException e) {
            return Boolean.FALSE;
        } finally {
            try {
                if (context != null) {
                    context.close();
                }
            } catch (Exception e) {
                LOGGER.warn("Availability check for {}: Context isn't closed due to error", providerUrl, e);
            }
        }
    }
}

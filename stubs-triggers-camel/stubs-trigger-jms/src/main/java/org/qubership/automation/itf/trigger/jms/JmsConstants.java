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

public interface JmsConstants {
    String DESTINATION_TYPE = "destinationType";
    String DESTINATION = "destination";
    String CONNECTION_FACTORY = "connectionFactory";
    String CREDENTIALS = "credentials";
    String PRINCIPAL = "principal";
    String ADDITIONAL_JNDI_PROPERTIES = "addJndiProps";
    String PROVIDER_URL = "providerUrl";
    String WSDL_PATH = "wsdlPath";
    String INITIAL_CONTEXT_FACTORY = "initialContextFactory";
    String AUTHENTICATION = "authentication";
    String MESSAGE_SELECTOR = "messageSelector";
    String JMS_HEADERS = "jmsHeaders";
    String RECOVERY_INTERVAL = "recoveryInterval";
    String MAX_ATTEMTPS = "maxAttempts";
}

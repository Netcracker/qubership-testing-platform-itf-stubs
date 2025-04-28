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

package org.qubership.automation.itf.integration.config.jms;

import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;

import org.qubership.atp.multitenancy.interceptor.jms.AtpJmsTemplate;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

public class DefaultJmsTemplate extends JmsTemplate implements AtpJmsTemplate {

    @Override
    public void convertAndSend(String destination, Object message, Map<String, Object> properties) throws JmsException {
        super.send(destination, (session) -> {
            MessageConverter messageConverter = super.getMessageConverter();
            if (messageConverter == null) {
                throw new RuntimeException(String.format("MessageConverter wasn't configured for %s destination",
                        destination));
            }
            Message messageToSend = messageConverter.toMessage(message, session);
            setStringProperties(properties, messageToSend);
            return messageToSend;
        });
    }

    private void setStringProperties(Map<String, Object> properties, Message messageToSend) throws JMSException {
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            messageToSend.setStringProperty(property.getKey(), String.valueOf(property.getValue()));
        }
    }
}

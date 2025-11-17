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

package org.qubership.automation.itf.trigger.jms.inbound;

import static org.qubership.automation.itf.trigger.jms.JmsConstants.DESTINATION;
import static org.qubership.automation.itf.trigger.jms.JmsConstants.DESTINATION_TYPE;
import static org.qubership.automation.itf.trigger.jms.JmsConstants.MESSAGE_SELECTOR;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.jms.Destination;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.jms.JmsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class JmsRoutingBuilder extends ItfAbstractRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsRoutingBuilder.class);
    private static final String MESSAGE_SELECTOR_PROPERTY_REGEXP = "(.+=.+)";
    private static final String JMS_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.jms.inbound.JMSInboundTransport";
    private ConnectionProperties connectionProperties;
    private Destination destination;
    private JmsComponent component;
    private String id;
    private StorableDescriptor triggerConfigurationDescriptor;

    /**
     * TODO Add JavaDoc.
     */
    public JmsRoutingBuilder(ConnectionProperties connectionProperties,
                             StorableDescriptor triggerConfigurationDescriptor,
                             Destination destination,
                             JmsComponent component,
                             String id) {
        this.triggerConfigurationDescriptor = triggerConfigurationDescriptor;
        this.connectionProperties = connectionProperties;
        this.destination = destination;
        this.component = component;
        this.id = id;
    }

    @Override
    public void configure() throws Exception {
        LOGGER.debug("Starting of JMSQueue");
        if (Strings.isNullOrEmpty((String) connectionProperties.get(DESTINATION_TYPE))) {
            throw new IllegalArgumentException("Destination type is not specified; should be 'Queue' or 'Topic'.");
        }
        String destinationType = connectionProperties.obtain(DESTINATION_TYPE);
        String destinationName = connectionProperties.obtain(DESTINATION);
        String selectorString = (String) connectionProperties.get(MESSAGE_SELECTOR);
        String selectorExpression = StringUtils.EMPTY;
        if (destinationType.equals("Queue") && !Strings.isNullOrEmpty(selectorString)) {
            selectorExpression = buildSelectorExpression(selectorString);
        }
        if (JmsHelper.isPathToDestination(destinationName)) { //backport for old format..
            component.getConfiguration().setSelector(selectorExpression);
            String jmsEndpoint = id + ':' + destinationType.toLowerCase(Locale.getDefault()) + ':' + destinationName;
            from(jmsEndpoint).process(createProcessor(jmsEndpoint))
                    .routeId(id)
                    .routeDescription(triggerConfigurationDescriptor.getProjectUuid().toString())
                    .group(TransportType.JMS_INBOUND.name());
        } else {
            JmsEndpoint jmsEndpoint = JmsEndpoint.newInstance(destination, component);
            jmsEndpoint.setSelector(selectorExpression);
            from(jmsEndpoint).process(createProcessor(jmsEndpoint.toString())).routeId(id)
                    .routeDescription(triggerConfigurationDescriptor.getProjectUuid().toString())
                    .group(TransportType.JMS_INBOUND.name());
        }
    }

    protected Processor createProcessor(String jmsEndpoint) {
        return exchange -> {
            String sessionId = UUID.randomUUID().toString();
            MetricsAggregateService.putCommonMetrics(triggerConfigurationDescriptor.getProjectUuid(), sessionId);
            LOGGER.info("Project: {}. SessionId: {}. Request is received by jmsEndpoint: {}",
                    triggerConfigurationDescriptor.getProjectUuid(), sessionId, jmsEndpoint);
            try {
                startSession(exchange, JMS_INBOUND_TRANSPORT_CLASS_NAME, connectionProperties,
                        triggerConfigurationDescriptor, sessionId);
                MetricsAggregateService.incrementIncomingRequestToProject(
                        triggerConfigurationDescriptor.getProjectUuid(), TransportType.JMS_INBOUND, true);
            } catch (Exception e) {
                MetricsAggregateService.incrementIncomingRequestToProject(
                        triggerConfigurationDescriptor.getProjectUuid(), TransportType.JMS_INBOUND, false);
                throw e;
            }
        };
    }

    //building expression to JMS Selector
    private String buildSelectorExpression(String selectorString) {
        StringBuilder selectorExpression = new StringBuilder();
        String operatorLike = " LIKE ";
        String operatorOr = " OR ";
        String[] arrHeadersWithValues = selectorString.split(",");

        for (int i = 0; i < arrHeadersWithValues.length; i++) {
            StringBuilder selectorBuilder = new StringBuilder();
            if (arrHeadersWithValues[i].matches(MESSAGE_SELECTOR_PROPERTY_REGEXP)) {
                /*
                    add header name to selector expression, then add value.
                 */
                selectorBuilder
                        .append('(')
                        .append(arrHeadersWithValues[i].split("=")[0].trim())
                        .append(operatorLike)
                        .append('\'')
                        .append(arrHeadersWithValues[i].split("=")[1].trim())
                        .append('\'')
                        .append(')');
                if (i < arrHeadersWithValues.length - 1) {
                    selectorBuilder.append(operatorOr);
                }
                selectorExpression.append(selectorBuilder);
            }
        }
        if (selectorExpression.toString().trim().endsWith("OR")) {
            String expression = selectorExpression.toString().trim();
            return expression.substring(0, expression.length() - 2).trim();
        }
        return selectorExpression.toString();
    }

    @Override
    public Map<String, Object> getAdditionalProperties(Exchange exchange) {
        return new HashMap<>(); // There are currently no additional connection properties retrieved from the exchange
    }

    @Override
    public List<String> getExcludeHeadersList() {
        return null;
    }
}

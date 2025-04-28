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

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.naming.InitialContext;

import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Ignore;
import org.junit.Test;

import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.trigger.jms.InitialContextBuilder;

public class JmsRoutingBuilderTest {
    private final InitialContextBuilder initialContextBuilder = new InitialContextBuilder();

    @Ignore
    @Test
    public void testListenJMSQueue() throws Exception {
        InitialContext initialContext = initialContextBuilder.createContext();
        DefaultCamelContext context = new DefaultCamelContext();
        JmsComponent component = JmsComponent.jmsComponent((ConnectionFactory) initialContext.lookup("MB_Connection_Factory_Out"));
        Destination queue = (Destination) initialContext.lookup("MB_Out_Queue");
        context.addComponent("jms", component);
        ConnectionProperties connectionProperties = new ConnectionProperties();
        connectionProperties.put("destination", "MB_Out_Queue");
        connectionProperties.put("destinationType", "queue");
        context.addRoutes(new JmsRoutingBuilder(connectionProperties,null, queue, component, "testID"));

/*        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JmsEndpoint endpoint = JmsEndpoint.newInstance(queue, component);
                from(endpoint).process(exchange -> System.out.println(exchange.getIn().getBody(String.class)));
            }
        });*/
        //Thread.sleep(120000); //uncomment it when it would be needed.
    }
}

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

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.LoggingErrorHandlerBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.LoggerFactory;

public interface CamelContextProvider {
    CamelContext CAMEL_CONTEXT = new DefaultCamelContext();
    ProducerTemplate template = CAMEL_CONTEXT.createProducerTemplate();

    /**
     * TODO Add JavaDoc.
     */
    default void start() {
        if (!((ServiceSupport) CAMEL_CONTEXT).isStarted()) {
            try {
                synchronized (CAMEL_CONTEXT) {
                    if (!((ServiceSupport) CAMEL_CONTEXT).isStarted()) {
                        LoggingErrorHandlerBuilder errorHandlerBuilder =
                                new LoggingErrorHandlerBuilder(LoggerFactory.getLogger(CamelContextProvider.class));
                        CAMEL_CONTEXT.setErrorHandlerBuilder(errorHandlerBuilder);
                        CAMEL_CONTEXT.start();
                    }
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(CamelContextProvider.class).error("Failed starting of CamelContext", e);
            }
        }
    }

    String getId();
}

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
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.qubership.automation.itf.core.util.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CamelContextProvider {
    Logger LOGGER = LoggerFactory.getLogger(CamelContextProvider.class);
    CamelContext CAMEL_CONTEXT = new DefaultCamelContext();
    ProducerTemplate template = CAMEL_CONTEXT.createProducerTemplate();

    /**
     * TODO Add JavaDoc.
     */
    default void start() {
        if (!CAMEL_CONTEXT.isStarted()) {
            try {
                synchronized (CAMEL_CONTEXT) {
                    if (!CAMEL_CONTEXT.isStarted()) {
                        Boolean CAMEL_ROUTE_STATISTICS_ENABLED = Boolean.valueOf(Config.getConfig()
                                .getStringOrDefault("camel.route.statistics.enabled", "true"));
                        if (CAMEL_ROUTE_STATISTICS_ENABLED) {
                            CAMEL_CONTEXT.getManagementStrategy()
                                    .getManagementAgent()
                                    .setStatisticsLevel(ManagementStatisticsLevel.RoutesOnly);
                        }
                        CAMEL_CONTEXT.start();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed starting of CamelContext", e);
            }
        }
    }

    String getId();
}

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

package org.qubership.automation.itf.communication;

import java.util.Map;

import org.apache.camel.ServiceStatus;
import org.apache.camel.impl.EventDrivenConsumerRoute;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoutesInformationResponse {

    private ServiceStatus serviceStatus;
    private String version;
    private String consumer;
    private String consumerClassName;
    private String endpoint;
    private Map<String,Object> routeProperties;

    private Object routeId;

    /**
     * Create simple representation of EventDrivenConsumerRoute.
     * @param route - route to simplify.
     */
    public RoutesInformationResponse(EventDrivenConsumerRoute route) {
        this.serviceStatus = route.getStatus();
        this.consumer = route.getConsumer().toString();
        this.consumerClassName = route.getConsumer().getClass().getSimpleName();
        this.endpoint = route.getEndpoint().toString();
        this.version = route.getVersion();
        this.routeProperties = route.getProperties();
        this.routeId = routeProperties.get("id");
    }
}

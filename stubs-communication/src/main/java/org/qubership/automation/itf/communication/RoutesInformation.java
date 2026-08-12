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

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RoutesInformation {

    private ServiceStatus serviceStatus;
    private String consumer;
    private String consumerClassName;
    private String endpoint;
    @JsonIgnore
    private String transportType;
    @JsonIgnore
    private String projectUuid;
    private Map<String, Object> metaInfo;
    private Map<String, Object> exchangesProperties = new HashMap<>();
    private Object routeId;

    /**
     * Create simple representation of Camel route.
     *
     * @param route - route to simplify.
     */
    public RoutesInformation(Route route) {
        if (route instanceof StatefulService) {
            this.serviceStatus = ((StatefulService) route).getStatus();
        } else {
            this.serviceStatus = ServiceStatus.Stopped;
        }
        this.consumer = route.getConsumer().toString();
        this.consumerClassName = route.getConsumer().getClass().getSimpleName();
        this.endpoint = route.getEndpoint().toString();
        this.metaInfo = Arrays.stream(RouteMetaInfo.values())
                .map(routeMetaInfo -> routeMetaInfo.getValue())
                .filter(route.getProperties()::containsKey)
                .collect(Collectors.toMap(key -> key, route.getProperties()::get));
        this.routeId = route.getRouteId();
        this.projectUuid = metaInfo.get(RouteMetaInfo.PROJECT_UUID.getValue()).toString();
        this.metaInfo.remove(RouteMetaInfo.PROJECT_UUID.getValue());
        this.transportType = route.getGroup();
        this.metaInfo.put(RouteMetaInfo.UP_TIME.getValue(), route.getUptime());
        this.metaInfo.put(RouteMetaInfo.START_TIME.getValue(),
                Instant.now().minusMillis(route.getUptimeMillis()).toString());
    }
}

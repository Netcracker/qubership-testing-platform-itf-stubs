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

package org.qubership.automation.itf.activation.impl;

import java.util.HashMap;
import java.util.Map;

import org.qubership.automation.itf.core.model.communication.TransportType;

public class TransportTypeToTriggerClassMapping {

    private static final Map<TransportType, String> transportToTrigger = initializeMap();

    private static Map<TransportType, String> initializeMap() {
        Map<TransportType, String> map = new HashMap<>();
        map.put(TransportType.CLI_INBOUND, "org.qubership.automation.itf.trigger.cli.inbound.CliTrigger");
        map.put(TransportType.FILE_INBOUND, "org.qubership.automation.itf.trigger.file.inbound.FileInboundTrigger");
        map.put(TransportType.HTTP_INBOUND, "org.qubership.automation.itf.trigger.http.inbound.HttpInboundTrigger");
        map.put(TransportType.HTTP2_INBOUND, "org.qubership.automation.itf.trigger.http2.inbound.Http2InboundTrigger");
        map.put(TransportType.JMS_INBOUND, "org.qubership.automation.itf.trigger.jms.JmsTrigger");
        map.put(TransportType.KAFKA_INBOUND, "org.qubership.automation.itf.trigger.kafka.KafkaTrigger");
        map.put(TransportType.REST_INBOUND, "org.qubership.automation.itf.trigger.rest.inbound.RestInboundTrigger");
        map.put(TransportType.SNMP_INBOUND, "org.qubership.automation.itf.trigger.snmp.inbound.SnmpTrigger");
        map.put(TransportType.SMPP_INBOUND, "org.qubership.automation.itf.trigger.smpp.inbound.SmppTrigger");
        map.put(TransportType.SOAP_OVER_HTTP_INBOUND,
                "org.qubership.automation.itf.trigger.soap.http.inbound.SoapOverHttpTrigger");
        return map;
    }

    public static String getTriggerClassName(TransportType transportType) {
        return transportToTrigger.get(transportType);
    }

    /** Method getting transport type.
     * @param triggerClassFullQualifiedName - full class name of Trigger
     * @return enum TransportType based on trigger's class.
     */
    public static TransportType getTransportType(String triggerClassFullQualifiedName) {
        for (Map.Entry<TransportType, String> entry : transportToTrigger.entrySet()) {
            if (entry.getValue().equals(triggerClassFullQualifiedName)) {
                return entry.getKey();
            }
        }
        return null;
    }
}

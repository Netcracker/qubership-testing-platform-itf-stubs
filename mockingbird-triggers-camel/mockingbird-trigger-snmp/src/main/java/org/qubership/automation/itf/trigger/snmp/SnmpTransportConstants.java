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

package org.qubership.automation.itf.trigger.snmp;

public interface SnmpTransportConstants {
    String SNMP_VERSION = "SNMP version";
    String SNMP_VERSION_DESCRIPTION = "SNMP version number: 0 (SNMPv1), 1 (SNMPv2c), 3 (SNMPv3)";

    String HOST = "Host";
    String HOST_DESCRIPTION = "Host name";

    String PORT = "Port";
    String PORT_DESCRIPTION = "Port number";

    String PROPERTIES = "properties";
    String PROPERTIES_DESCRIPTION = "Extra Properties (name=value pairs delimited by a new-line character)";

    String SNMP_COMPONENT = "snmp";
}

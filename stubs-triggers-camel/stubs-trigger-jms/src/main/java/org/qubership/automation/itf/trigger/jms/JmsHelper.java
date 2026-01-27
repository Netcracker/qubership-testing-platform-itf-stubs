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

import java.util.Properties;

public class JmsHelper {

    private static final String FULL_PATH_TO_DESTINATION = "((.+)(/))(.+!.+)";
    private static final String MODULE_PATH_TO_DESTINATION = "(.+!.+)";

    // Check if <destinationName> consists of Full-Path-to-Destination
    //  Full-Path-to-Destination looks like this: "NCJMSServer_clust1/NCJMSModule!SMF_PRODFULFILLMENT_RMK_WMSTI_clust1"
    //  Module-Path-to-Destination looks like this: "NCJMSModule!SMF_PRODFULFILLMENT_RMK_WMSTI"
    //  (spaces are possible after '!')
    //  Otherwise it should be like this: "jms_queue_SMF_PRODFULFILLMENT_RMK_WMSTI"
    //  (This is <JNDI Name> of the queue on the server)
    public static boolean isPathToDestination(String destinationName) {
        return destinationName.matches(FULL_PATH_TO_DESTINATION) || destinationName.matches(MODULE_PATH_TO_DESTINATION);
    }

    public static boolean isJndiName(String destinationName) {
        return !isPathToDestination(destinationName);
    }

    /**
     * TODO Add JavaDoc.
     */
    public static void putSafe(Properties properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }
}

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

package org.qubership.automation.itf.activation;

import java.util.Arrays;

public enum ActivationServiceConstants {
    TRIGGER("trigger"),
    ENVIRONMENT("environment"),
    ON_STARTUP("onStartup"),
    SYNC("sync"),

    ACTIVATE("activate"),
    DEACTIVATE("deactivate"),
    RE_ACTIVATE("reActivate"),

    SWITCH("switch"),

    GET_TRIGGER_BY_ID("/trigger/%s"),
    GET_TRIGGER_BY_ENVIRONMENT_ID("/trigger/environmentId/%s"),
    GET_ALL_TRIGGERS_FOR_ACTIVATION("/trigger/all/active"),
    UPDATE_TRIGGER_STATUS("/trigger"),

    ACTION_NOT_VALID("Action '%s' is not valid"),
    TRIGGER_NOT_FOUND("Trigger is not found by id='%s'"),
    TRIGGER_STATUS_CHANGE_FAILED("Trigger status change is failed. Please contact administrator.");

    private String value;

    ActivationServiceConstants(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String getValueWithArgs(Object... args) {
        return String.format(value, args);
    }

    /**
     * TODO Add JavaDoc.
     */
    public static ActivationServiceConstants getByValue(String value) {
        return Arrays.stream(values())
                .filter(constant -> value.equals(constant.value))
                .findFirst()
                .orElse(null);
    }
}

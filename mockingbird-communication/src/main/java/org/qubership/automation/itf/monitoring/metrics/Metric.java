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

package org.qubership.automation.itf.monitoring.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Metric {

    ATP_ITF_STUBS_ACTIVE_TRIGGER_BY_PROJECT("atp_itf_stubs_active_trigger_by_project"),
    ATP_ITF_STUBS_ERROR_TRIGGER_BY_PROJECT("atp_itf_stubs_error_trigger_by_project"),
    ATP_ITF_STUBS_INCOMING_REQUEST_BY_PROJECT("atp_itf_stubs_incoming_request_by_project"),
    ATP_ITF_STUBS_INCOMING_REQUEST_SECONDS_BY_PROJECT("atp_itf_stubs_incoming_request_seconds_by_project"),
    ATP_ITF_STUBS_INCOMING_REQUEST_MESSAGE_SIZE_BY_PROJECT("atp_itf_stubs_incoming_request_message_size_by_project");
    private final String value;

}

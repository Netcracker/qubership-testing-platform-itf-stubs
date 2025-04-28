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

package org.qubership.automation.itf.trigger.http;

public interface HttpConstants {

    String ENDPOINT = "endpoint";
    String BASE_URL = "baseUrl";
    String METHOD = "method";
    String AUTH_TYPE = "authType";
    String PRINCIPAL = "principal";
    String CREDENTIALS = "credentials";
    String RESPONSE_CODE = "responseCode";
    String HEADERS = "headers";
    String CONTENT_TYPE = "contentType";
    String IS_STUB = "isStub";
    int LOOP_MAX_SIZE = 4;
    String HTTPS = "https";
    String HTTP = "http";
    String SECURE_PROTOCOL = "secureProtocol";
    String KEYSTORE_FILE = "keystore.file";
    String KEYSTORE_PASSWORD = "keystore.passwd";
}

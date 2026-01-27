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

package org.qubership.automation.itf.trigger.cli;

public interface CliConstants {

    String REMOTE_IP = "remote_ip";
    String CONNECTION_TYPE = "type";
    String REMOTE_PORT = "remote_port";
    String USER = "user";
    String PASSWORD = "password";

    interface Inbound {

        String COMMAND_DELIMITER = "command_delimiter";
        String GREETING = "greeting";
        String ALLOWED_EMPTY = "empty_commands_allowed";
    }
}

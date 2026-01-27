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

package org.qubership.automation.itf.ui.swagger;

public interface SwaggerConstants {
    String INTERNAL_API = "ITF Internal API";
    String INTERNAL_API_DESCR = "Internal API description. Expected to be used through UI, "
            + "can be called through REST, but correct behavior is not guaranteed.";

    String PUBLIC_API = "ITF Public API";
    String PUBLIC_API_DESCR = "Public API description. Called through REST.";

    String BULK_INTEGRATION_API = "Bulk Validator Integration API";
    String BULK_INTEGRATION_API_DESCR = "Bulk Validator Integration API description. "
            + "Used through UI, can be called through REST.";

    String CONTEXT_QUERY_API = "Context queries API.";
    String CONTEXT_QUERY_API_DESCR = "Context API to query context. Called through REST.";
    String CONTEXT_COMMAND_API = "Context commands API.";
    String CONTEXT_COMMAND_API_DESCR = "Context API to control context. Called through REST.";

    String TRIGGER_QUERY_API = "Trigger queries API.";
    String TRIGGER_QUERY_API_DESCR = "Trigger API to query trigger. Called through REST.";
    String TRIGGER_COMMAND_API = "Trigger commands API.";
    String TRIGGER_COMMAND_API_DESCR = "Trigger API to control trigger. Called through REST.";

    String ENVIRONMENT_TRIGGER_QUERY_API = "Environment trigger queries API.";
    String ENVIRONMENT_TRIGGER_QUERY_API_DESCR = "Environment trigger API to query environment trigger. "
            + "Called through REST.";
    String ENVIRONMENT_TRIGGER_COMMAND_API = "Environment trigger commands API.";
    String ENVIRONMENT_TRIGGER_COMMAND_API_DESCR = "Environment trigger API to control environment trigger. "
            + "Called through REST.";

    String SERVER_CONFIGURATION_QUERY_API = "Server configuration queries API.";
    String SERVER_CONFIGURATION_QUERY_API_DESCR = "Server configuration API to query server configuration. "
            + "Called through REST.";
    String SERVER_CONFIGURATION_COMMAND_API = "Server configuration commands API.";
    String SERVER_CONFIGURATION_COMMAND_API_DESCR = "Server configuration API to control server configuration. "
            + "Called through REST.";

    String MOCKINGBIRD_REST_QUERY_API = "ITF REST API queries.";
    String MOCKINGBIRD_REST_QUERY_API_DESCR = "ITF REST API to query ITF.";
    String MOCKINGBIRD_REST_COMMAND_API = "ITF REST API commands.";
    String MOCKINGBIRD_REST_COMMAND_API_DESCR = "ITF REST API to control ITF.";
    
    String EXECUTOR_COMMAND_API = "Executor commands API.";
    String EXECUTOR_COMMAND_API_DESCR = "Executor API to control execution. Called through REST.";

    String TOKEN_GENERATOR_COMMAND_API = "Token generator commands API.";
    String TOKEN_GENERATOR_COMMAND_API_DESCR = "Service used for generating authentication tokens "
            + "by provided user-password pair.";
}

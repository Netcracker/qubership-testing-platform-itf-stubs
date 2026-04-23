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

package org.qubership.automation.itf.integration.executor;

import org.qubership.atp.auth.springbootstarter.config.FeignConfiguration;
import org.qubership.automation.itf.openapi.executor.controllers.ItfStubsRequestControllerApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "${feign.atp.executor.name}",
             url = "${feign.atp.executor.url}",
             path = "${feign.atp.executor.route}",
             configuration = FeignConfiguration.class)
public interface ExecutorFeignClient extends ItfStubsRequestControllerApi {

}

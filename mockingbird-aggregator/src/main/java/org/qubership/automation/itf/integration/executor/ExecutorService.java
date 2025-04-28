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

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import org.modelmapper.ModelMapper;
import org.qubership.automation.itf.converter.DtoConvertService;
import org.qubership.automation.itf.core.model.communication.EnvironmentSample;
import org.qubership.automation.itf.core.model.communication.Result;
import org.qubership.automation.itf.core.model.communication.TriggerSample;
import org.qubership.automation.itf.core.model.communication.UpdateTriggerStatusRequest;
import org.qubership.automation.itf.openapi.executor.dto.UIUpdateTriggerStatusDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutorService {

    private final ExecutorFeignClient executorFeignClient;

    @Autowired
    private ModelMapper modelMapper;

    /**
     * Get all active triggers.
     *
     * @return a list of {@link TriggerSample} objects.
     */
    public List<TriggerSample> getAllActiveTriggers() {
        DtoConvertService dtoConvertService = new DtoConvertService(modelMapper);
        List<TriggerSample> triggerSamples = dtoConvertService
                .convertList(executorFeignClient.getAllActiveTriggers().getBody(), TriggerSample.class);
        return triggerSamples;
    }

    /**
     * Get all triggers by project UUID.
     *
     * @param projectUuid ATP project UUID
     * @return a list of {@link EnvironmentSample} objects.
     */
    public List<TriggerSample> getAllTriggersByProject(UUID projectUuid) {
        DtoConvertService dtoConvertService = new DtoConvertService(modelMapper);
        List<TriggerSample> triggerSamples = dtoConvertService
                .convertList(executorFeignClient.getAllTriggersByProject(projectUuid).getBody(), TriggerSample.class);
        return triggerSamples;
    }

    /**
     * Get all triggers with state (active and error) by project UUID.
     *
     * @param projectUuid ATP project UUID
     * @return a list of {@link EnvironmentSample} objects.
     */
    public List<TriggerSample> getAllTriggersByProjectToReActivate(UUID projectUuid) {
        DtoConvertService dtoConvertService = new DtoConvertService(modelMapper);
        List<TriggerSample> triggerSamples = dtoConvertService.convertList(
                executorFeignClient.getAllActiveAndErrorTriggersByProject(projectUuid).getBody(), TriggerSample.class);
        return triggerSamples;
    }

    /**
     * Get all triggers by environment folder.
     *
     * @param envFolderId environment folder ID
     * @return a list of {@link EnvironmentSample} objects.
     */
    public List<EnvironmentSample> getTriggersByEnvFolder(BigInteger envFolderId) {
        DtoConvertService dtoConvertService = new DtoConvertService(modelMapper);
        List<EnvironmentSample> environmentSamples = dtoConvertService.convertList(
                executorFeignClient.getTriggersByEnvFolder(envFolderId).getBody(), EnvironmentSample.class);
        return environmentSamples;
    }

    /**
     * Get trigger by id.
     *
     * @param id trigger ID
     * @return object with type {@link TriggerSample}.
     */
    public TriggerSample getTriggerById(BigInteger id) {
        DtoConvertService dtoConvertService = new DtoConvertService(modelMapper);
        TriggerSample triggerSample = dtoConvertService
                .convert(executorFeignClient.getTriggerById(id).getBody(), TriggerSample.class);
        return triggerSample;
    }

    /**
     * Get triggers by environment.
     *
     * @param environmentId environment ID
     * @return object with type {@link EnvironmentSample}.
     */
    public EnvironmentSample getTriggersByEnvironment(BigInteger environmentId) {
        DtoConvertService dtoConvertService = new DtoConvertService(modelMapper);
        EnvironmentSample environmentSample = dtoConvertService.convert(
                        executorFeignClient.getTriggersByEnvironment(environmentId).getBody(), EnvironmentSample.class);
        return environmentSample;
    }

    /**
     * Update trigger status.
     *
     * @param request request model for updating trigger status
     * @return object with type {@link Result}.
     */
    public Result updateTriggerStatus(UpdateTriggerStatusRequest request) {
        DtoConvertService dtoConvertService = new DtoConvertService(modelMapper);
        UIUpdateTriggerStatusDto requestDto = dtoConvertService.convert(request, UIUpdateTriggerStatusDto.class);
        Result result = dtoConvertService
                .convert(executorFeignClient.updateTriggerStatus(requestDto).getBody(), Result.class);
        return result;
    }
}

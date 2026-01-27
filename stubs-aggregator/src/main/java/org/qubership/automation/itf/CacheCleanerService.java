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

package org.qubership.automation.itf;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.qubership.automation.itf.core.util.transport.service.LockProvider;
import org.qubership.automation.itf.core.util.transport.service.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CacheCleanerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheCleanerService.class);

    private final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    private final long initialDelay = 120L;
    private final long delay = 120L;

    @Autowired
    public CacheCleanerService() {
    }

    /**
     * Start Cache Cleaner Service.
     */
    public void startWorker() {
        LOGGER.info("Cache Cleaner Service is started.");
        service.scheduleWithFixedDelay(() -> {
            try {
                SessionHandler.INSTANCE.cleanupCache();
                LockProvider.INSTANCE.cleanupCache();
            } catch (Throwable t) {
                LOGGER.error("Error while Caches cleaning up", t);
            }
        }, initialDelay, delay, TimeUnit.SECONDS);
    }

    /**
     * Stop Cache Cleaner Service.
     */
    public void stop() {
        LOGGER.info("Cache Cleaner Service is stopped.");
        service.shutdown();
    }
}

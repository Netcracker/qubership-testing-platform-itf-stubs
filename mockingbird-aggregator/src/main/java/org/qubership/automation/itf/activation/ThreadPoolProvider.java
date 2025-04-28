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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ThreadPoolProvider {
    private static final Thread.UncaughtExceptionHandler HANDLER = (t, e) ->
            log.error("Uncaught exception in thread {}", t.getName(), e);

    @Getter
    private final ExecutorService asyncTasksPool;
    @Getter
    private final ForkJoinPool forkJoinPool;
    private final int asyncProcessingPoolCoreSize;
    private final int asyncProcessingPoolMaxSize;
    private final int bulkProcessingPoolSize;

    /**
     * Constructor.
     *
     * @param asyncProcessingPoolCoreSize - core size of asyncTasksPool,
     * @param asyncProcessingPoolMaxSize - max size of asyncTasksPool,
     * @param bulkProcessingPoolSize - size of forkJoinPool.
     */
    @Autowired
    public ThreadPoolProvider(@Value("${async.processing.pool.core.size}") int asyncProcessingPoolCoreSize,
                              @Value("${async.processing.pool.max.size}") int asyncProcessingPoolMaxSize,
                              @Value("${bulk.processing.forkJoinPool.size}") int bulkProcessingPoolSize) {
        this.asyncProcessingPoolCoreSize = asyncProcessingPoolCoreSize;
        this.asyncProcessingPoolMaxSize = asyncProcessingPoolMaxSize;
        this.bulkProcessingPoolSize = bulkProcessingPoolSize;
        this.asyncTasksPool = initAsyncPool();
        this.forkJoinPool = initForkJoinPool();
    }

    private ExecutorService initAsyncPool() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("asyncTasksPool-%d")
                .setDaemon(false)
                .setPriority(Thread.NORM_PRIORITY)
                .setUncaughtExceptionHandler(HANDLER)
                .build();
        return new ThreadPoolExecutor(
                asyncProcessingPoolCoreSize,
                asyncProcessingPoolMaxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
    }

    private ForkJoinPool initForkJoinPool() {
        return new ForkJoinPool(bulkProcessingPoolSize,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory, HANDLER,false);
    }
}

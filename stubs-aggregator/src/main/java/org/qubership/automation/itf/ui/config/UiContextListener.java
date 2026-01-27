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

package org.qubership.automation.itf.ui.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.qubership.automation.itf.CacheCleanerService;
import org.qubership.automation.itf.core.stub.fast.FastResponseConfigsHolder;
import org.qubership.automation.itf.core.util.config.ApplicationConfig;
import org.qubership.automation.itf.core.util.eds.ExternalDataManagementService;
import org.qubership.automation.itf.core.util.eds.model.FileInfo;
import org.qubership.automation.itf.core.util.eds.service.EdsContentType;
import org.qubership.automation.itf.core.util.eds.service.EdsMetaInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.AbstractJmsListeningContainer;
import org.springframework.stereotype.Component;

import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UiContextListener {

    protected ApplicationContext myContext;
    private final TriggerContext triggerContext;
    private final ExternalDataManagementService externalDataManagementService;
    private final CacheCleanerService cacheCleanerService;
    private AtomicBoolean isFirstContextRefreshEvent = new AtomicBoolean(true);
    @Value("${local.storage.directory}")
    private String localStorageDirectory;

    @Autowired
    private UiContextListener(ApplicationContext myContext,
                              TriggerContext triggerContext,
                              ExternalDataManagementService externalDataManagementService,
                              CacheCleanerService cacheCleanerService) {
        this.myContext = myContext;
        this.triggerContext = triggerContext;
        this.externalDataManagementService = externalDataManagementService;
        this.cacheCleanerService = cacheCleanerService;
    }

    /**
     * App init handler.
     */
    @EventListener
    public void init(ContextRefreshedEvent event) throws InterruptedException {
        if (event.getSource().equals(myContext) && isFirstContextRefreshEvent.get()) {
            contextInitialized();
        }
    }

    @PreDestroy
    public void destroy() {
        triggerContext.destroy();
    }

    private void contextInitialized() {
        isFirstContextRefreshEvent.set(false);

        try {
            loadDataFromExternalStorage(System.nanoTime());
        } catch (IOException | MongoException ex) {
            log.error("Error while loading files from External Data Storage. Please note: further SOAP triggers "
                    + "activation and/or fast-stubs processing may be incorrect", ex);
        }

        try {
            initFastResponseConfigsHolder();
        } catch (SecurityException ex) {
            log.error("Error while initializing of Fast Response Configs Holder", ex);
        }

        try {
            bulkActivateTriggers();
        } catch (InterruptedException ex) {
            log.error("Error while triggers activation", ex);
        }

        startingJmsListenerContainer();

        cacheCleanerService.startWorker();
        log.info("Stub service initialization completed");
    }

    private void bulkActivateTriggers() throws InterruptedException {
        boolean startTransportTriggersAtStartup
                = Boolean.parseBoolean(ApplicationConfig.env.getProperty("start.transport.triggers.at.startup"));
        if (startTransportTriggersAtStartup) {
            boolean isSyncActivation
                    = Boolean.parseBoolean(ApplicationConfig.env.getProperty("triggers.activation.sync"));
            if (isSyncActivation) {
                triggerContext.activateTriggers().join();
            }
            int delay = getProperty("triggers.activation.delay", 60000, 0, 180000);
            int timeout = getProperty("triggers.activation.timeout", 15000, 10000, 60000);
            int attempts = getProperty("triggers.activation.attempts", 15, 0, 120);
            retryTriggersActivation(delay, timeout, attempts);
        }
    }

    private void startingJmsListenerContainer() {
        log.info("Getting JmsListenerContainerFactories...");
        DefaultJmsListenerContainerFactory factory = (DefaultJmsListenerContainerFactory)
                myContext.getBean("stubsTopicJmsListenerContainerFactory");
        factory.setAutoStartup(true);
        factory = (DefaultJmsListenerContainerFactory) myContext.getBean("stubsQueueJmsListenerContainerFactory");
        factory.setAutoStartup(true);
        log.info("JmsListenerContainerFactories: setAutoStartup is set to true.");
        log.info("Getting JmsListenerEndpointRegistry...");
        JmsListenerEndpointRegistry registry = myContext.getBean(JmsListenerEndpointRegistry.class);
        log.info("Setting 'autoStartup' to true for all JMS Listeners...");
        registry.getListenerContainers().forEach(listenerContainer ->
                ((AbstractJmsListeningContainer) listenerContainer).setAutoStartup(true));
        log.info("All JMS Listeners are ready. Starting jmsListenerEndpointRegistry...");
        registry.start();
        log.info("JmsListenerEndpointRegistry is started.");
    }

    private void retryTriggersActivation(int delay, int timeout, int attempts) {
        Thread thread = new Thread(() -> {
            if (!triggerContext.isListForActivationReceived()) {
                log.info("Triggers were not activated yet, starting activation process...");
                if (delay > 0) {
                    try {
                        log.info("Triggers activation: wait {} sec. before start...", delay / 1000);
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                int attemptsElapsed = 0;
                while (true) {
                    try {
                        log.info("Triggers activation: attempt #{} of {}...", ++attemptsElapsed, attempts);
                        triggerContext.activateTriggers().join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (triggerContext.isListForActivationReceived()) {
                        break;
                    } else {
                        if (attemptsElapsed < attempts) {
                            log.info("Triggers activation: wait {} sec. before retry...", timeout / 1000);
                            try {
                                Thread.sleep(timeout);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            log.warn("Triggers activation attempts ({}) are over!", attempts);
                            break;
                        }
                    }
                }
            }
        }, "RetryTriggersActivationThread");
        thread.start();
    }

    private void loadDataFromExternalStorage(long startTime) throws IOException, MongoException {
        log.info("Loading files from external storage...");
        Map<String, Object> params = new HashMap<>();
        params.put(EdsMetaInfo.CONTENT_TYPE.getStringValue(), EdsContentType.WSDL_XSD.getStringValue());

        Set<FileInfo> wsdlXsdFiles = externalDataManagementService.getExternalStorageService()
                .getFilesInfoByMetadataMapParams(params);
        externalDataManagementService.getFileManagementService().save(wsdlXsdFiles);

        Set<FileInfo> keystoreFiles
                = externalDataManagementService.getExternalStorageService().getKeyStoreFileInfo();
        externalDataManagementService.getFileManagementService().save(keystoreFiles);

        params.put(EdsMetaInfo.CONTENT_TYPE.getStringValue(), EdsContentType.FAST_STUB.getStringValue());
        Set<FileInfo> fastStubFiles = externalDataManagementService.getExternalStorageService()
                .getFilesInfoByMetadataMapParams(params);
        externalDataManagementService.getFileManagementService().save(fastStubFiles);

        String message = String.format("Files count: %d with type '%s', %d with type '%s', %d with type '%s'",
                wsdlXsdFiles.size(), EdsContentType.WSDL_XSD.getStringValue(),
                keystoreFiles.size(), EdsContentType.KEYSTORE.getStringValue(),
                fastStubFiles.size(), EdsContentType.FAST_STUB.getStringValue());
        long elapsedTime = System.nanoTime() - startTime;
        log.info("Loading files [{}] from external storage completed in {} (s).", message,
                String.format("%.3f", (double) elapsedTime / 1000000000.0));
    }

    private int getProperty(String name, int defaultValue, int minValue, int maxValue) {
        int value;
        try {
            value = Integer.parseInt(Objects.requireNonNull(ApplicationConfig.env.getProperty(name)));
        } catch (NumberFormatException | NullPointerException nfe) {
            log.error("Empty or incorrect value of '{}' config property; default value {} is used", name, defaultValue);
            value = defaultValue;
        }
        return value >= minValue && value <= maxValue ? value : defaultValue;
    }

    /**
     * Init of Fast Stub Config Holder.
     */
    public void initFastResponseConfigsHolder() throws SecurityException {
        String stubConfigsDirPath = localStorageDirectory + "/" + EdsContentType.FAST_STUB.getStringValue();
        File stubConfigsDir = new File(stubConfigsDirPath);
        if (!stubConfigsDir.exists() || !stubConfigsDir.isDirectory()) {
            log.warn("Folder {} not found. Fast stubs configurations will not be loaded.", stubConfigsDirPath);
            return;
        }
        File[] listConfigFiles = stubConfigsDir.listFiles();
        if (listConfigFiles == null) {
            log.warn("Folder {}: list of files is null", stubConfigsDirPath);
            return;
        }
        for (File projectDir : listConfigFiles) {
            if (projectDir.exists() && projectDir.isDirectory()) {
                File[] listProjectFiles = projectDir.listFiles();
                if (listProjectFiles != null) {
                    for (File file : listProjectFiles) {
                        if (file.exists() && !file.isDirectory()) {
                            FastResponseConfigsHolder.INSTANCE.loadFromFile(projectDir.getName(), file);
                        }
                    }
                } else {
                    log.warn("Folder {}: list of project files is null", projectDir.getPath());
                }
            }
        }
    }

}

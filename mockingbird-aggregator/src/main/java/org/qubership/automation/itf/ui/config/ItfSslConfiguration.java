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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.eds.ExternalDataManagementService;
import org.qubership.automation.itf.core.util.eds.service.GridFsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "embedded.https.enabled", havingValue = "true")
public class ItfSslConfiguration {

    @Value("${keystore.password}")
    private String keystorePassword;

    @Autowired
    private ExternalDataManagementService externalDataManagementService;

    /**
     * This method get instance of TrustManagerFactory.
     * 
     * @return instance of {@link TrustManagerFactory} for keystore.
     */
    @Bean
    public TrustManagerFactory trustManagerFactory(KeyStore keyStore) {
        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            return trustManagerFactory;
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            log.error("Can not create TrustManagerFactory.", e);
            throw new RuntimeException("Can not create TrustManagerFactory.", e);
        }
    }

    /**
     * This method get instance of KeyManagerFactory.
     *
     * @return instance of {@link KeyManagerFactory} for keystore.
     */
    @Bean
    public KeyManagerFactory keyManagerFactory(KeyStore keyStore) {
        try {
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
            return keyManagerFactory;
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            log.error("Can not create KeyManagerFactory.", e);
            throw new RuntimeException("Can not create KeyManagerFactory.", e);
        }
    }

    /**
     * Loads jks keystore file from path and creates the {@link KeyStore} instance from it.
     *
     * @return instance of {@link KeyStore}.
     */
    @Bean
    public KeyStore keyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        getKeystoreFileFromExternalStorage();
        String keyStoreFile = Config.getConfig().getString("local.storage.directory")
                .concat("/keystore/keystore.jks");
        KeyStore keyStore = KeyStore.getInstance("jks");
        try (InputStream keystoreFileInputStream = new FileInputStream(keyStoreFile)) {
            keyStore.load(keystoreFileInputStream, keystorePassword.toCharArray());
            return keyStore;
        } catch (FileNotFoundException e) {
            log.error("File with certificates not found.", e);
            throw new RuntimeException("File with certificates not found.", e);
        }
    }

    private void getKeystoreFileFromExternalStorage() {
        try {
            if (externalDataManagementService.getExternalStorageService() instanceof GridFsService) {
                externalDataManagementService.getFileManagementService().save(
                        externalDataManagementService.getExternalStorageService().getKeyStoreFileInfo());
            }
        } catch (IOException e) {
            log.error("Error loading files of keystore type from GridFs.", e);
        }
    }
}

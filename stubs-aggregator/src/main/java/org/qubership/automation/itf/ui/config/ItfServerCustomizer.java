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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import io.undertow.Undertow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(value = "embedded.https.enabled", havingValue = "true")
public class ItfServerCustomizer implements WebServerFactoryCustomizer<UndertowServletWebServerFactory> {

    @Value("${embedded.tls.1.2.server.port}")
    private int tls12ServerPort;
    @Value("${embedded.tls.1.3.server.port}")
    private int tls13ServerPort;
    @Value("${embedded.ssl.server.port}")
    private int sslServerPort;
    @Value("${extra.project.ports.tls.1.2}")
    private String[] extraProjectPortsTls12;
    @Value("${extra.project.ports.tls.1.3}")
    private String[] extraProjectPortsTls13;
    @Value("${extra.project.ports.ssl}")
    private String[] extraProjectPortsSsl;

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String CURRENT_HOST = "0.0.0.0";

    private final TrustManagerFactory trustManagerFactory;
    private final KeyManagerFactory keyManagerFactory;

    @Autowired
    public ItfServerCustomizer(TrustManagerFactory trustManagerFactory, KeyManagerFactory keyManagerFactory) {
        this.trustManagerFactory = trustManagerFactory;
        this.keyManagerFactory = keyManagerFactory;
    }

    @Override
    public void customize(UndertowServletWebServerFactory factory) {
        factory.addBuilderCustomizers(builder -> {
            log.info("Undertow WebServer customizing is started...");
            processPortsList("TLSv1.2", builder, CURRENT_HOST, tls12ServerPort, extraProjectPortsTls12);
            processPortsList("TLSv1.3", builder, CURRENT_HOST, tls13ServerPort, extraProjectPortsTls13);
            processPortsList("SSL", builder, CURRENT_HOST, sslServerPort, extraProjectPortsSsl);
            log.info("Undertow WebServer customizing is completed.");
        });
    }

    private void processPortsList(String protocol, Undertow.Builder builder, String host, int port,
                                  String[] extraPorts) {
        createSslContext(protocol)
                .ifPresent(tlsContext -> {
                        builder.addHttpsListener(port, host, tlsContext);
                        for (String item : extraPorts) {
                            if (StringUtils.isEmpty(item) || StringUtils.isBlank(item)) {
                                log.warn("Null or empty port number for {} protocol, skipped", protocol);
                                continue;
                            }
                            try {
                                int portItem = Integer.parseInt(item.trim());
                                if (portItem > 0) {
                                    builder.addHttpsListener(portItem, host, tlsContext);
                                } else {
                                    log.error("Wrong port number for {} protocol: '{}'", protocol, item);
                                }
                            } catch (NumberFormatException numberFormatException) {
                                log.error("Exception while parsing of port number for {} protocol: '{}'",
                                        protocol, item, numberFormatException);
                            }
                        }
                });
    }

    private Optional<SSLContext> createSslContext(String protocol) {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance(protocol);
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Could not create SSLContext for {} protocol.", protocol, e);
        }
        return Optional.ofNullable(sslContext);
    }
}

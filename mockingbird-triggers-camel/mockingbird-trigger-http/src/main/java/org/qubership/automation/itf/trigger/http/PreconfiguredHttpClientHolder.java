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

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

public class PreconfiguredHttpClientHolder {
    private static final CloseableHttpClient HTTPCLIENT = configureClient();
    private static final int HTTP_CLIENT_TIMEOUT_VALUE = 300000;

    public static CloseableHttpClient get() {
        return HTTPCLIENT;
    }

    private static CloseableHttpClient configureClient() {
        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            TrustAllStrategy trustStrategy = new TrustAllStrategy();
            builder.loadTrustMaterial(null, trustStrategy);
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                    builder.build(), new NoopHostnameVerifier()
            );
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(HTTP_CLIENT_TIMEOUT_VALUE)
                    .setConnectionRequestTimeout(HTTP_CLIENT_TIMEOUT_VALUE)
                    .setSocketTimeout(HTTP_CLIENT_TIMEOUT_VALUE).build();
            return HttpClients.custom()
                    .setSSLSocketFactory(sslConnectionSocketFactory)
                    .setDefaultRequestConfig(config)
                    .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalStateException("Http client is not initialized", e);
        }
    }
}

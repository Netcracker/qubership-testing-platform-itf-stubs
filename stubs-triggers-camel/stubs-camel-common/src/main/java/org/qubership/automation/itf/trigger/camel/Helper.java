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

package org.qubership.automation.itf.trigger.camel;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.core.util.config.Config;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.collect.Maps;

public class Helper {

    @Value("${lock.provider.check.interval:100}")
    private static int lockProviderCheckInterval;

    @Value("${lock.provider.check.maxInterval:800}")
    private static int lockProviderCheckMaxInterval;

    @Value("${lock.provider.check.multiplier:1.2}")
    private static float lockProviderCheckMultiplier;

    /**
     * Fill Camel route URL parameters from properties map, for not empty keys/values.
     */
    public static String setExtraProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return StringUtils.EMPTY;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, Object> item : properties.entrySet()) {
            if (StringUtils.isBlank(item.getKey()) || item.getValue() == null) {
                continue;
            }
            String key = item.getKey().trim();
            if (!key.isEmpty()) {
                stringBuilder.append("&").append(key).append("=").append(processValue(item.getValue()));
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Fill extra properties map from properties map, for not empty keys/values.
     */
    public static Map<String, Object> setExtraPropertiesMap(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> extraProps = Maps.newHashMapWithExpectedSize(properties.size());
        for (Map.Entry<String, Object> item : properties.entrySet()) {
            if (StringUtils.isBlank(item.getKey()) || item.getValue() == null) {
                continue;
            }
            String key = item.getKey().trim();
            if (!key.isEmpty()) {
                extraProps.put(key, item.getValue());
            }
        }
        return extraProps;
    }

    public static String getBrokerMessageSelectorValue() {
        return Config.getConfig().getRunningHostname();
    }

    public static int getLockProviderCheckInterval() {
        return lockProviderCheckInterval;
    }

    public static int getLockProviderCheckMaxInterval() {
        return lockProviderCheckMaxInterval;
    }

    public static float getLockProviderCheckMultiplier() {
        return lockProviderCheckMultiplier;
    }

    public static boolean isTrue(Boolean value) {
        return value != null && value;
    }

    private static String processValue(Object objValue) {
        try {
            return URLEncoder.encode((String) objValue, JvmSettings.CHARSET_NAME);
        } catch (UnsupportedEncodingException e) {
            return (String) objValue;
        }
    }
}

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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JvmSettings {
    public static final Charset CHARSET;
    public static final String CHARSET_NAME;

    static {
        String fileEncoding = System.getProperty("file.encoding", "UTF-8");
        Charset jvmCharset;
        try {
            jvmCharset = Charset.forName(fileEncoding);
        } catch (Exception ex) {
            log.warn("Illegal/unsupported 'file.encoding' JVM property: {}; 'UTF-8' is set as default", fileEncoding);
            jvmCharset = StandardCharsets.UTF_8;
        }
        CHARSET = jvmCharset;
        CHARSET_NAME = CHARSET.name();
    }
}

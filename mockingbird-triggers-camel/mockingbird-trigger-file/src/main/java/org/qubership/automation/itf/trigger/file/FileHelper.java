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

package org.qubership.automation.itf.trigger.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.qubership.automation.itf.JvmSettings;

public class FileHelper {

    /**
     * Build uri string for {@link org.apache.camel.builder.RouteBuilder} to create route.
     *
     * @param type            type of File trigger (file/sftp/ftp)
     * @param host            remote host\ip
     * @param path            remote path
     * @param username        username
     * @param password        password can be empty if using ssh key
     * @param extraProperties additional properties which user configures on UI
     * @return uri string
     * @throws IOException if creating temp file for ssh key is failed
     */
    public static String buildUri(String type, String host, String path, String username, String password,
                                  Object sshKeyObj, Map<String, Object> extraProperties) throws IOException {
        boolean isSftp = "sftp".equals(type);
        String sshKey = FileHelper.getSshKey(isSftp, sshKeyObj);
        FileHelper.stopIfRequiredPropertiesIsEmpty(path, type, isSftp, sshKey, password, username);
        boolean passIsBlank = Objects.isNull(password) || isNull(password);
        return type
                + "://"
                + appendIfHas(username, "@")
                + appendIfHas(host, "/")
                + path
                + appendIfHas("?password=", password)
                + (isSftp ? passIsBlank ? '?' : '&' : Strings.EMPTY)
                + (isSftp ? "useUserKnownHostsFile=false" : Strings.EMPTY)
                + (isSftp ? "&preferredAuthentications=publickey,password" : Strings.EMPTY)
                + (isSftp && isNotNull(sshKey)
                    ? "&privateKeyFile=" + createTempPemFile(sshKey).getPath() : Strings.EMPTY)
                + FileHelper.setExtraProperties(extraProperties, !isSftp && passIsBlank);
    }

    private static File createTempPemFile(String sshKey) throws IOException {
        File tmpfile = File.createTempFile(String.valueOf(System.currentTimeMillis()), ".pem");
        tmpfile.deleteOnExit();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tmpfile), JvmSettings.CHARSET))) {
            writer.write(sshKey);
        }
        return tmpfile;
    }

    public static boolean isNull(String s) {
        return StringUtils.isBlank(s) || "null".equals(s);
    }

    public static boolean isNotNull(String s) {
        return !isNull(s);
    }

    /**
     * Build uri string options.
     *
     * @param properties  - extra endpoint properties from transports configs
     * @param firstOption - if true '?' will be added to uri string before first uri option, else '&'
     * @return string uri options like ?key=value&key1=value1 or &key=value&key1=value1 depends on the firstOption
     */
    public static String setExtraProperties(Map<String, Object> properties, boolean firstOption) {
        if (properties == null || properties.isEmpty()) {
            return StringUtils.EMPTY;
        }
        StringBuilder stringBuilder = new StringBuilder();
        boolean isFirst = firstOption;
        for (Map.Entry<String, Object> item : properties.entrySet()) {
            if (StringUtils.isBlank(item.getKey()) || item.getValue() == null) {
                continue;
            }
            String key = item.getKey().trim();
            if (!key.isEmpty()) {
                stringBuilder.append(isFirst ? '?' : '&').append(key).append('=').append(processValue(item.getValue()));
                isFirst = false;
            }
        }
        return stringBuilder.toString();
    }

    private static String processValue(Object objValue) {
        try {
            return URLEncoder.encode((String) objValue, JvmSettings.CHARSET_NAME);
        } catch (UnsupportedEncodingException e) {
            return (String) objValue;
        }
    }

    private static String appendIfHas(String... s) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String s1 : s) {
            if (isNull(s1)) {
                return StringUtils.EMPTY;
            }
            stringBuilder.append(s1);
        }
        return stringBuilder.toString();
    }

    /**
     * Get ssh key, depending on protocol.
     *
     * @param isSftp - true for sftp transport, false otherwise
     * @param sshKeyObj - ssh key value
     * @return String representation of ssh key - for sftp, null otherwise
     */
    public static String getSshKey(boolean isSftp, Object sshKeyObj) {
        //noinspection unchecked
        return isSftp && Objects.nonNull(sshKeyObj)
                && !((List<String>) sshKeyObj).isEmpty()
                ? String.join("\n", ((List<String>) sshKeyObj))
                : null;
    }

    /**
     * Check if required properties are set. Throw exceptions if not set.
     *
     * @param remotePath remote path
     * @param type transport type
     * @param isSftp true for sftp, false otherwise
     * @param sshKey ssh key value
     * @param password password
     * @param username user name
     */
    public static void stopIfRequiredPropertiesIsEmpty(String remotePath, String type, boolean isSftp, String sshKey,
                                                       String password, String username) {
        if (isNull(remotePath) || isNull(type)) {
            throw new IllegalArgumentException("Path/type can't be empty");
        }
        if (isSftp && Strings.isEmpty(sshKey) && Strings.isEmpty(password)) {
            throw new IllegalArgumentException("Password/ssh_key can't be empty! Please fill one of them");
        }
        if (isSftp && Strings.isEmpty(username)) {
            throw new IllegalArgumentException("Username can't be empty!");
        }
    }
}

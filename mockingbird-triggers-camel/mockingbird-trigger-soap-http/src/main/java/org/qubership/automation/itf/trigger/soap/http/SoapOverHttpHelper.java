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

package org.qubership.automation.itf.trigger.soap.http;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.BusFactory;
import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.WSDLManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoapOverHttpHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoapOverHttpHelper.class);

    /**
     * TODO Add JavaDoc.
     */
    public static Bus prepareBusContext(Object trigger) {
        Thread.currentThread().setContextClassLoader(trigger.getClass().getClassLoader());

        Bus defaultBus = BusFactory.getThreadDefaultBus();
        if (Objects.isNull(defaultBus.getExtension(WSDLManager.class))) {
            try {
                defaultBus.setExtension(new WSDLManagerImpl(), WSDLManager.class);
            } catch (BusException e) {
                LOGGER.error("WSDLManager doesn't created ", e);
            }
        }
        return defaultBus;
    }

    /*  Params:
     *       - pathString: path to file (WSDL or XSD) - not empty (checked before)
     *           Value variants:
     *               1) relative path ==> the absolute path will be calculated,
     *               2) absolute path
     *               3) URL-to-file
     *       - checkFileExists - if true AND pathString is path (relative or absolute), then check if file exists
     *       - errorMessage - Error message prefix, like "WSDL file not found at path: "
     *   Return: String URL representation
     * */
    /**
     * TODO Add JavaDoc.
     */
    public static String getAndCheckPath(String pathString, boolean checkFileExists, String errorMessage)
            throws MalformedURLException, FileNotFoundException {
        try {
            URL url = new URL(pathString);
            return url.toString();
        } catch (MalformedURLException ex) {
            // May be the value is not URL but a path (relative or absolute)?
            Path path = Paths.get(pathString);
            String absolutePath = path.toAbsolutePath().toString();
            if (checkFileExists) {
                File file = new File(absolutePath);
                if (!file.exists()) {
                    throw new FileNotFoundException(errorMessage + " not found at path: " + absolutePath);
                }
                return file.toURI().toURL().toString();
            } else {
                return absolutePath;
            }
        }
    }
}

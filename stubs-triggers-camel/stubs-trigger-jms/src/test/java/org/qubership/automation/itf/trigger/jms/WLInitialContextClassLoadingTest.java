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

package org.qubership.automation.itf.trigger.jms;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Properties;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.qubership.automation.itf.core.util.loader.ChildFirstURLClassLoader;
import org.qubership.automation.itf.core.util.loader.base.AbstractLoader;

/**
 * Fails when the WebLogic client jar cannot initialize its JNDI context next to the HK2 of the application.
 *
 * <p>The jar bundles HK2 2.x with {@code javax.inject}, and this module's classpath carries HK2 3.x with
 * {@code jakarta.inject}. The jar is not in any public repository, so the test runs only when the system property
 * {@value #JAR_PROPERTY} holds the path to {@code wlthint3client.jar}, for example
 * {@code mvn test -pl stubs-triggers-camel/stubs-trigger-jms -Dtest=WLInitialContextClassLoadingTest}
 * {@code -Dwlthint3client.jar=C:/temp/wlthint3client.jar}.
 * No WebLogic server is needed: the test expects the connection to a port nobody listens on to be refused with
 * {@link CommunicationException}, which the client reports only after its own HK2 has started. If HK2 fails to start,
 * the test fails with {@link ExceptionInInitializerError} instead.</p>
 */
@EnabledIfSystemProperty(named = WLInitialContextClassLoadingTest.JAR_PROPERTY, matches = ".+")
class WLInitialContextClassLoadingTest {

    static final String JAR_PROPERTY = "wlthint3client.jar";

    @Test
    void weblogicClientStartsItsOwnHk2AndFailsOnlyOnConnection() throws Exception {
        URL clientJar = Path.of(System.getProperty(JAR_PROPERTY)).toUri().toURL();
        int unusedPort = findUnusedPort();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (ChildFirstURLClassLoader loader = new ChildFirstURLClassLoader(
                new URL[] {clientJar},
                getClass().getClassLoader(), new TransportLoaderStub().childFirstPrefixes())) {
            Thread.currentThread().setContextClassLoader(loader);
            Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
            env.put(Context.PROVIDER_URL, "t3://localhost:" + unusedPort);

            assertThrows(CommunicationException.class, () -> new InitialContext(env));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Exposes the prefixes that {@link AbstractLoader} gives the class loaders of transport libraries. */
    private static class TransportLoaderStub extends AbstractLoader<Object> {

        List<String> childFirstPrefixes() {
            return getChildFirstPrefixes();
        }

        @Override
        protected Class<Object> getGenericType() {
            return Object.class;
        }

        @Override
        protected void validateClasses(Set<Class<? extends Object>> classes) {
        }

        @Override
        public Object getInstanceClass(String className, Object... paramForConstructor) {
            return null;
        }

        @Override
        public Class<? extends Object> getClass(String typeName) {
            return null;
        }
    }
}

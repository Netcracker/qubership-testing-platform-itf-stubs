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

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.qubership.automation.itf.core.util.helper.Reflection;

public class InitialContextBuilder {
    public InitialContextBuilder() {
    }

    private void putSafe(Properties properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }

    public InitialContext createContext() throws NamingException {
        Properties env = new Properties();
/*        putSafe(env, Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.fscontext.RefFSContextFactory");
        putSafe(env, Context.SECURITY_PRINCIPAL, StringUtils.EMPTY);
        putSafe(env, Context.SECURITY_CREDENTIALS, StringUtils.EMPTY);
        String[] providers = Reflection.toArray(String.class, "file:D:/iqsp_qasvc004_new");*/
        putSafe(env, Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
        putSafe(env, Context.SECURITY_PRINCIPAL, "sysadm");
        putSafe(env, Context.SECURITY_CREDENTIALS, "qubership");
        String[] providers = Reflection.toArray(String.class, "t3://dev-test-service-address:9876");
        if (providers == null || providers.length < 1) {
            throw new IllegalStateException("Provider URL is not defined");
        }
        if (providers[0].isEmpty()) {
            throw new IllegalStateException("Provider URL is empty. Please check Server config (Environment tab).");
        }
        //pick first provider url
        putSafe(env, Context.PROVIDER_URL, providers[0]);
        return new InitialContext(env);
    }
}

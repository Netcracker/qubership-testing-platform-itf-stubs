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

package org.qubership.automation.itf.utils.loader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.qubership.automation.itf.core.util.loader.base.AbstractLoader;
import org.qubership.automation.itf.trigger.camel.Trigger;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Component
public class TriggerClassLoader extends AbstractLoader<Trigger> {

    /**
     * Constructor.
     */
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "Checked; it's suspicious, but let's skip now")
    public TriggerClassLoader() {
        LIB = "/lib";
        PATH_PATTERN = "(mockingbird-trigger+[\\w-]*)";
    }

    @Override
    protected Class<Trigger> getGenericType() {
        return Trigger.class;
    }

    @Override
    protected void validateClasses(Set<Class<? extends Trigger>> classes) {
        if (!classes.iterator().hasNext()) {
            throw new IllegalArgumentException("No class is found");
        }
    }

    @Override
    public Trigger getInstanceClass(String className, Object... paramForConstructor) throws ClassNotFoundException {
        try {
            return getClass(className).getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException
                 | NoSuchMethodException | InvocationTargetException e) {
            throw new ClassNotFoundException("Classloader isn't found for class", e);
        }
    }

    /**
     * Get constructor by class.
     */
    public Constructor<? extends Trigger> getConstructorByClass(String className, Class<?>... paramForConstructor)
            throws ClassNotFoundException {
        try {
            return getClass(className).getConstructor(paramForConstructor);
        } catch (NoSuchMethodException e) {
            throw new ClassNotFoundException("Constructor isn't found for class", e);
        }
    }

    @Override
    public Class<? extends Trigger> getClass(String typeName) {
        try {
            return getClassLoaderHolder().computeIfAbsent(typeName, className -> {
                throw new IllegalArgumentException("Classloader not found for class " + typeName);
            }).loadClass(typeName).asSubclass(Trigger.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class with name " + typeName + " isn't found in classloader", e);
        }
    }
}

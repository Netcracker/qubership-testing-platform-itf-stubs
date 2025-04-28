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

package org.qubership.automation.itf.trigger.template.velocity;

import static org.qubership.automation.itf.core.util.constants.InstanceSettingsConstants.VELOCITY_CONFIG;

import java.io.StringWriter;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.directive.Directive;
import org.apache.velocity.tools.ToolManager;
import org.qubership.automation.itf.core.model.common.Storable;
import org.qubership.automation.itf.core.model.jpa.context.JsonContext;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.engine.TemplateEngine;
import org.qubership.automation.itf.core.util.helper.ClassResolver;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VelocityTemplateEngine implements TemplateEngine {

    private VelocityEngine engine;
    private ToolManager toolManager;

    /**
     * Constructor.
     */
    public VelocityTemplateEngine() {
        String velocityConfig = Config.getConfig().getString(VELOCITY_CONFIG);

        if (!Strings.isNullOrEmpty(velocityConfig)) {
            log.info("Init Apache Velocity engine with settings {}", velocityConfig);
            engine = new VelocityEngine(velocityConfig);
        } else {
            log.info("Init Apache Velocity engine with default settings");
            engine = new VelocityEngine();
        }
        engine.setProperty("velocimacro.permissions.allow.inline.to.replace.global", "true");
        engine.setProperty("console.logsystem.max.level", "WARN");
        engine.setProperty("runtime.log.logsystem.max.level", "WARN");
        engine.init();
        for (Class directiveClass : ClassResolver.getInstance().getSubtypesOf(Directive.class)) {
            engine.loadDirective(directiveClass.getName());
        }
        toolManager = new ToolManager();
        toolManager.setVelocityEngine(engine);
    }

    /**
     * Parse someString against context.
     *
     * @param someString - string to parse,
     * @param context - JsonContext of variables,
     * @return string result of parsing.
     */
    public String process(String someString, JsonContext context) {
        if (StringUtils.isBlank(someString)) {
            return StringUtils.EMPTY;
        }
        Context velocityContext = toolManager.createContext();
        return processing(someString, context, velocityContext);
    }

    @Override
    public String process(Storable owner, String someString, JsonContext context) {
        return process(someString, context);
    }

    @Override
    public String process(Map<String, Storable> storables, String someString, JsonContext context) {
        return process(someString, context);
    }

    @Override
    public String process(Storable owner, String someString, JsonContext context, String coords) {
        //TODO: Revise (and may be implement) itf-executor changes relating 'coords' parameter
        return process(someString, context);
    }

    @Override
    public String process(Map<String, Storable> storables, String someString, JsonContext context, String coords) {
        //TODO: Revise (and may be implement) itf-executor changes relating 'coords' parameter
        return process(someString, context);
    }

    private String processing(String someString, JsonContext context, Context velocityContext) {
        if (StringUtils.isBlank(someString)) {
            return StringUtils.EMPTY;
        }
        log.debug("Processing string with Velocity...");
        log.trace("String to process: {}", someString);
        for (Object o : context.entrySet()) {
            Map.Entry entry = (Map.Entry) o;
            velocityContext.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        StringWriter stringWriter = new StringWriter(someString.length());
        try {
            engine.evaluate(velocityContext, stringWriter, LOG_TAG, someString);
        } catch (Exception e) {
            // Alternative behavior is possible: do NOT throw an exception. Log Error and continue processing
            throw new VelocityException(String.format("Error occurred while processing template '%s'. %s",
                    LOG_TAG, e.getMessage()), e);
        }
        String string = stringWriter.toString();
        log.debug("String processed");
        log.trace("Result string is: {}", string);
        return string;
    }
}

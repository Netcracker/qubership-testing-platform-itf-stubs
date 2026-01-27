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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.directive.Directive;
import org.apache.velocity.tools.ToolManager;
import org.apache.velocity.tools.config.Data;
import org.apache.velocity.tools.config.FactoryConfiguration;
import org.apache.velocity.tools.config.ToolConfiguration;
import org.apache.velocity.tools.config.ToolboxConfiguration;
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
        /*
            Explicit init of Velocity Tools 2.0 is implemented,
            instead of bundled tools.xml files parsing
            (after commons-beanutils upgrade to 1.9.4).
         */
        FactoryConfiguration factoryConfiguration = makeGenericFactoryConfig();
        factoryConfiguration.addConfiguration(makeStrutsFactoryConfig());
        factoryConfiguration.addConfiguration(makeViewFactoryConfig());

        toolManager = new ToolManager();
        toolManager.getToolboxFactory().configure(factoryConfiguration);
        toolManager.setVelocityEngine(engine);
    }

    private static Data fillData(String type, String key, Object value) {
        Data data = new Data();
        data.setType(type);
        data.setKey(key);
        data.setValue(value);
        return data;
    }

    private static List<ToolConfiguration> makeToolsList(String... args) {
        List<ToolConfiguration> list = new ArrayList<>();
        for(String arg : args) {
            ToolConfiguration cfg = new ToolConfiguration();
            cfg.setClassname(arg);
            list.add(cfg);
        }
        return list;
    }

    private static FactoryConfiguration makeGenericFactoryConfig() {
        FactoryConfiguration factoryConfiguration = new FactoryConfiguration();
        factoryConfiguration.addData(fillData("number", "TOOLS_VERSION", "2.0"));
        factoryConfiguration.addData(fillData("boolean", "GENERIC_TOOLS_AVAILABLE", "true"));

        ToolboxConfiguration applicationToolboxConfiguration = new ToolboxConfiguration();
        applicationToolboxConfiguration.setScope("application");
        applicationToolboxConfiguration.setTools(makeToolsList(
                "org.apache.velocity.tools.generic.AlternatorTool",
                "org.apache.velocity.tools.generic.ClassTool",
                "org.apache.velocity.tools.generic.ComparisonDateTool",
                "org.apache.velocity.tools.generic.ConversionTool",
                "org.apache.velocity.tools.generic.DisplayTool",
                "org.apache.velocity.tools.generic.EscapeTool",
                "org.apache.velocity.tools.generic.FieldTool",
                "org.apache.velocity.tools.generic.MathTool",
                "org.apache.velocity.tools.generic.NumberTool",
                "org.apache.velocity.tools.generic.ResourceTool",
                "org.apache.velocity.tools.generic.SortTool",
                "org.apache.velocity.tools.generic.XmlTool"));
        factoryConfiguration.addToolbox(applicationToolboxConfiguration);

        ToolboxConfiguration requestToolboxConfiguration = new ToolboxConfiguration();
        requestToolboxConfiguration.setScope("request");
        requestToolboxConfiguration.setTools(makeToolsList(
                "org.apache.velocity.tools.generic.ContextTool",
                "org.apache.velocity.tools.generic.LinkTool",
                "org.apache.velocity.tools.generic.LoopTool",
                "org.apache.velocity.tools.generic.RenderTool"));
        factoryConfiguration.addToolbox(requestToolboxConfiguration);
        return factoryConfiguration;
    }

    private static FactoryConfiguration makeStrutsFactoryConfig() {
        FactoryConfiguration factoryConfiguration = new FactoryConfiguration();
        factoryConfiguration.addData(fillData("boolean", "STRUTS_TOOLS_AVAILABLE", "true"));

        ToolboxConfiguration requestToolboxConfiguration = new ToolboxConfiguration();
        requestToolboxConfiguration.setScope("request");
        requestToolboxConfiguration.setTools(makeToolsList(
                "org.apache.velocity.tools.struts.ActionMessagesTool",
                "org.apache.velocity.tools.struts.ErrorsTool",
                "org.apache.velocity.tools.struts.FormTool",
                "org.apache.velocity.tools.struts.MessageTool",
                "org.apache.velocity.tools.struts.StrutsLinkTool",
                "org.apache.velocity.tools.struts.TilesTool",
                "org.apache.velocity.tools.struts.ValidatorTool"));
        factoryConfiguration.addToolbox(requestToolboxConfiguration);
        return factoryConfiguration;
    }

    private static FactoryConfiguration makeViewFactoryConfig() {
        FactoryConfiguration factoryConfiguration = new FactoryConfiguration();
        factoryConfiguration.addData(fillData("boolean", "VIEW_TOOLS_AVAILABLE", "true"));

        ToolboxConfiguration requestToolboxConfiguration = new ToolboxConfiguration();
        requestToolboxConfiguration.setScope("request");
        requestToolboxConfiguration.setTools(makeToolsList(
                "org.apache.velocity.tools.view.CookieTool",
                "org.apache.velocity.tools.view.ImportTool",
                "org.apache.velocity.tools.view.IncludeTool",
                "org.apache.velocity.tools.view.LinkTool",
                "org.apache.velocity.tools.view.PagerTool",
                "org.apache.velocity.tools.view.ParameterTool",
                "org.apache.velocity.tools.view.ViewContextTool",
                "org.apache.velocity.tools.generic.ResourceTool"));
        factoryConfiguration.addToolbox(requestToolboxConfiguration);

        ToolboxConfiguration sessionToolboxConfiguration = new ToolboxConfiguration();
        sessionToolboxConfiguration.setScope("session");
        sessionToolboxConfiguration.setProperty("createSession", "false");
        sessionToolboxConfiguration.setTools(makeToolsList(
                "org.apache.velocity.tools.view.BrowserTool"));
        factoryConfiguration.addToolbox(sessionToolboxConfiguration);
        return factoryConfiguration;
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

package org.qubership.automation.itf.trigger.camel.route;

import org.qubership.automation.itf.core.util.config.Config;

public class SessionStarterFactory {

    private static final SessionStarter INSTANCE;

    static {
        boolean testingMode = Boolean.parseBoolean(
                Config.getConfig().getStringOrDefault("stubs.testing.mode.enabled", "false")
        );
        INSTANCE = testingMode ? new TestSessionStarter() : new ProductionSessionStarter();
    }

    public static SessionStarter get() {
        return INSTANCE;
    }
}

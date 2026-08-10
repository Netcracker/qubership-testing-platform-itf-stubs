package org.qubership.automation.itf.trigger.camel.route;

import org.apache.camel.Exchange;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.transport.service.LockProvider;
import org.qubership.automation.itf.core.util.transport.service.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestSessionStarter implements SessionStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSessionStarter.class);

    @Override
    public void startSession(Exchange exchange,
                             String transportClassName,
                             ConnectionProperties transportConfig,
                             StorableDescriptor triggerConfig,
                             String sessionId,
                             Message message) {
        addResponseToHolder(sessionId);
        LOGGER.warn("Testing mode: responding with hardcoded message");
    }

    /**
     * Add response message to Holder.
     * Please note: used in case stubs.testing.mode.enabled=true only,
     * to provide testability of the service alone (without itf-executor service).
     * Primarily, for local development and GitHub CI.
     *
     * @param sessionId String session ID to provide response for.
     */
    private void addResponseToHolder(String sessionId) {
        Message message = new Message("Test response");
        SessionHandler.INSTANCE.addMessage(sessionId, message);
        LockProvider.INSTANCE.notify(sessionId);
    }

}

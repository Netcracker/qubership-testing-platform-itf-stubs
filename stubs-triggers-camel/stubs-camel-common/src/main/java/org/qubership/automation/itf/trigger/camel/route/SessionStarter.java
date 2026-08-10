package org.qubership.automation.itf.trigger.camel.route;

import org.apache.camel.Exchange;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;

public interface SessionStarter {
    void startSession(Exchange exchange,
                      String transportClassName,
                      ConnectionProperties transportConfig,
                      StorableDescriptor triggerConfig,
                      String sessionId,
                      Message message) throws Exception;
}

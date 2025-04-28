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

package org.qubership.automation.itf.trigger.smpp;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.trigger.smpp.inbound.SmppTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.type.LoggingOptions;
import com.cloudhopper.smpp.type.SmppChannelException;

public class SmppServerSimulator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmppServerSimulator.class);
    private DefaultSmppServer smppServer;
    SmppTrigger smppTrigger;
    SmppServerHandler serverHandler;
    ConnectionProperties connectionProperties;

    /**
     * Smpp Server Simulator constructor.
     */
    public SmppServerSimulator(ConnectionProperties triggerConnectionProperties,
                               SmppTrigger trigger) throws SmppChannelException {
        smppTrigger = trigger;
        connectionProperties = triggerConnectionProperties;

        SmppServerConfiguration configuration = new SmppServerConfiguration();
        configuration.setPort(Integer.parseInt((String)connectionProperties
                .getOrDefault(Constants.PORT, "36888")));
        configuration.setDefaultRequestExpiryTimeout(Integer.parseInt((String)connectionProperties
                .getOrDefault(Constants.EXPIRY_TIMEOUT, "60000")));
        configuration.setDefaultWindowSize(Integer.parseInt((String)connectionProperties
                .getOrDefault(Constants.WINDOW_SIZE, "5")));
        configuration.setDefaultSessionCountersEnabled(Boolean.parseBoolean((String)connectionProperties
                .getOrDefault(Constants.DEFAULT_SESSION_COUNTERS_ENABLED, "false")));
        configuration.setJmxEnabled(Boolean.parseBoolean((String)connectionProperties
                .getOrDefault(Constants.JMX_ENABLED, "false")));
        configuration.setSystemId((String)connectionProperties
                .getOrDefault(Constants.SYSTEM_ID, "uim"));

        initServerHandler();
        ScheduledExecutorService monitorService = Executors.newScheduledThreadPool(1, new MyThreadFactory());
        smppServer = new DefaultSmppServer(configuration, serverHandler,
                Executors.newCachedThreadPool(), monitorService);
        smppServer.start();
        LOGGER.info("Server started at {}:{}", configuration.getHost(), configuration.getPort());
    }

    /**
     * Stop smpp server, then destroy.
     */
    public void stop() {
        if (smppServer != null) {
            if (smppServer.isStarted()) {
                smppServer.stop();
            }
            smppServer.destroy();
        }
    }

    private void initServerHandler() {
        serverHandler = new SmppServerHandler() {
            public void sessionBindRequested(Long along, SmppSessionConfiguration smppSessionConfiguration,
                                             BaseBind baseBind) {
                LoggingOptions loggingOptions = new LoggingOptions();
                loggingOptions.setLogPdu(false);
                loggingOptions.setLogBytes(false);
                smppSessionConfiguration.setLoggingOptions(loggingOptions);

                LOGGER.info("Bind request with system.id = {} successfully received. Sending response.",
                        baseBind.getSystemId());
                smppSessionConfiguration.setName("Smpp.Client." + smppSessionConfiguration.getSystemId());
            }

            public void sessionCreated(Long along, SmppServerSession smppServerSession, BaseBindResp baseBindResp) {
                LoggingOptions loggingOptions = new LoggingOptions();
                loggingOptions.setLogPdu(false);
                loggingOptions.setLogBytes(false);
                smppServerSession.getConfiguration().setLoggingOptions(loggingOptions);

                LOGGER.info("Session with system.id = {} successfully created.",
                        smppServerSession.getConfiguration().getSystemId());
                smppServerSession.serverReady(
                    new ItfSmppSessionHandler(smppServerSession.getConfiguration(), smppTrigger, connectionProperties)
                );
            }

            public void sessionDestroyed(Long along, SmppServerSession smppServerSession) {
                smppServerSession.destroy();
                LOGGER.info("Session with system.id = {} successfully destroyed.",
                        smppServerSession.getConfiguration().getSystemId());
            }
        };
    }

    private static class MyThreadFactory implements ThreadFactory {
        AtomicInteger sequence = new AtomicInteger(0);

        public Thread newThread(@NotNull Runnable runnable) {
            Thread t = new Thread(runnable);
            t.setName("SmppServerSessionWindowMonitorPool-" + sequence.getAndIncrement());
            return t;
        }
    }
}

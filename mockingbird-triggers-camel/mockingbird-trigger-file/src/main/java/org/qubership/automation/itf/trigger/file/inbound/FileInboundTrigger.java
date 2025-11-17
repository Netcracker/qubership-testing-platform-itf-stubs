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

package org.qubership.automation.itf.trigger.file.inbound;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.SimpleBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.constants.PropertyConstants;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.AbstractTriggerImpl;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.qubership.automation.itf.trigger.file.FileHelper;

import com.google.common.collect.Maps;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileInboundTrigger extends AbstractTriggerImpl {

    private static final String FILE_INBOUND_CLASS_NAME =
            "org.qubership.automation.itf.transport.file.inbound.FileInbound";
    private CamelContext context;

    public FileInboundTrigger(StorableDescriptor triggerConfigurationDescriptor,
                              ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    private static boolean checkSftpConnection(String ip,
                                               String type,
                                               ConnectionProperties connectionProperties) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        String username = connectionProperties.get(PropertyConstants.File.PRINCIPAL).toString();
        String credentials = connectionProperties.get(PropertyConstants.File.CREDENTIALS).toString();
        try {
            session = jsch.getSession(username, ip, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setPassword(credentials);
            session.connect();
            Channel channel = session.openChannel(type);
            channel.connect();
            channel.disconnect();
            return Boolean.TRUE;
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private static boolean checkFtpConnection(String ip,
                                              String type,
                                              ConnectionProperties connectionProperties,
                                              boolean checkLogin) throws Exception {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(ip);
            int reply = ftpClient.getReplyCode();
            boolean isOk = FTPReply.isPositiveCompletion(reply);
            if (isOk && checkLogin) {
                String username = connectionProperties.get(PropertyConstants.File.PRINCIPAL).toString();
                String credentials = connectionProperties.get(PropertyConstants.File.CREDENTIALS).toString();
                return ftpClient.login(username, credentials);
            }
            return isOk;
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (Exception e) {
                    log.debug("Error while disconnecting of test FTP connection to {}: {}", ip, e.getMessage());
                }
            }
        }
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        String type = this.getConnectionProperties().get(PropertyConstants.File.TYPE).toString();
        if (StringUtils.isEmpty(type)) {
            throw new RuntimeException("Trigger type of the trigger is not set");
        }
        String ip = this.getConnectionProperties().get(PropertyConstants.File.HOST).toString();
        boolean isConnectionTestOk;
        switch (type) {
            case "sftp" :
                isConnectionTestOk = testSftpConnection(ip, type, this.getConnectionProperties());
                break;
            case "ftp" :
                isConnectionTestOk = testFtpConnection(ip, type, this.getConnectionProperties());
                break;
            case "file" :
                isConnectionTestOk = true;
                break;
            default :
                throw new RuntimeException("Unknown trigger type '" + type + "' of the trigger");
        }
        if (isConnectionTestOk) {
            startSpecificTrigger();
        } else {
            throw new RuntimeException("Error while activating of " + type + " trigger to " + ip
                    + " (connection test failed)");
        }
    }

    private void startSpecificTrigger() throws Exception {
        context = new DefaultCamelContext();
        context.addRoutes(createRoutes());
        context.start();
    }

    @Override
    protected void deactivateSpecificTrigger() throws Exception {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        if (context != null) {
            context.stop();
            context = null;
        }
    }

    @Override
    protected void applyTriggerProperties(ConnectionProperties connectionProperties) {
        setConnectionProperties(connectionProperties);
    }

    ItfAbstractRouteBuilder createRoutes() {
        return new ItfAbstractRouteBuilder() {
            @Override
            public Map<String, Object> getAdditionalProperties(Exchange exchange) {
                return Maps.newHashMap();
            }

            @Override
            public List<String> getExcludeHeadersList() {
                return null;
            }

            @Override
            public void configure() throws Exception {
                UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
                ConnectionProperties properties = getConnectionProperties();
                String type = properties.obtain(PropertyConstants.File.TYPE);
                String host = properties.obtain(PropertyConstants.File.HOST);
                String path = properties.obtain(PropertyConstants.File.PATH);
                String username = properties.obtain(PropertyConstants.File.PRINCIPAL);
                String password = properties.obtain(PropertyConstants.File.CREDENTIALS);
                Object sshKeyPropertiesValue = properties.get(PropertyConstants.File.SSH_KEY);
                Map<String, Object> extraProperties = properties.obtain(PropertyConstants.Commons.ENDPOINT_PROPERTIES);
                String uri = FileHelper.buildUri(type, host, path, username, password, sshKeyPropertiesValue,
                        extraProperties);
                log.debug("URI for {} trigger was built", type);
                from(uri)
                        .routeDescription(projectUuid.toString())
                        .group(TransportType.FILE_INBOUND.name())
                        .idempotentConsumer(SimpleBuilder.simple("${in.body.lastModified}"),
                                new MemoryIdempotentRepository())
                        .process(exchange -> {
                            String sessionId = UUID.randomUUID().toString();
                            MetricsAggregateService.putCommonMetrics(projectUuid, sessionId);
                            log.info("Project: {}. SessionId: {}. Request is received by endpoint: {}",
                                    projectUuid, sessionId, uri);
                            try {
                                startSession(exchange, FILE_INBOUND_CLASS_NAME, properties,
                                        getTriggerConfigurationDescriptor(), sessionId);
                                MetricsAggregateService.incrementIncomingRequestToProject(
                                        projectUuid, TransportType.FILE_INBOUND, true);
                            } catch (Exception e) {
                                MetricsAggregateService.incrementIncomingRequestToProject(
                                        projectUuid, TransportType.FILE_INBOUND, false);
                                throw e;
                            }
                        });
            }
        };
    }

    /**
     * TODO Add JavaDoc.
     */
    public boolean checkIfServerAvailable(Map<String, Boolean> availableServers) {
        Object ip = this.getConnectionProperties().get(PropertyConstants.File.HOST);
        String type = this.getConnectionProperties().get(PropertyConstants.File.TYPE).toString();
        if (ip == null) {
            // Empty ip value is Ok for file transport, but a misconfiguration for ftp/sftp transports
            if (type.equals("file")) {
                return true;
            } else {
                log.debug("Misconfigured {} transport: an empty ip-address!", type);
                return false;
            }
        }
        return availableServers.computeIfAbsent(type + "://" + ip,
                key -> testConnection((String) ip, type, this.getConnectionProperties()));
    }

    private boolean testConnection(String ip, String type, ConnectionProperties connectionProperties) {
        switch (type) {
            case "sftp":
                return testSftpConnection(ip, type, connectionProperties);
            case "ftp":
                return testFtpConnection(ip, type, connectionProperties);
            case "file":
            default:
                return true;
        }
    }

    private boolean testSftpConnection(String ip, String type, ConnectionProperties connectionProperties) {
        try {
            return checkSftpConnection(ip, type, connectionProperties);
        } catch (Exception ex) {
            log.debug("Error while testing SFTP connection to {}: {}", ip, ex.getMessage());
            return Boolean.FALSE;
        }
    }

    private boolean testFtpConnection(String ip, String type, ConnectionProperties connectionProperties) {
        try {
            return checkFtpConnection(ip, type, connectionProperties, false);
        } catch (Exception ex) {
            log.debug("Error while testing FTP connection to {}: {}", ip, ex.getMessage());
            return Boolean.FALSE;
        }
    }
}

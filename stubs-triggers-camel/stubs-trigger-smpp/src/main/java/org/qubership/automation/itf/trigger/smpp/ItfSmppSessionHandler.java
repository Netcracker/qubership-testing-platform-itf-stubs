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

import static org.qubership.automation.itf.trigger.smpp.Constants.CHARSET_NAME;
import static org.qubership.automation.itf.trigger.smpp.Constants.COMMAND_STATUS;
import static org.qubership.automation.itf.trigger.smpp.Constants.DEFAULT_CHARSET;
import static org.qubership.automation.itf.trigger.smpp.Constants.MESSAGE_ID;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.mdc.MdcField;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.smpp.inbound.SmppTrigger;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.tlv.Tlv;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ItfSmppSessionHandler extends DefaultSmppSessionHandler {
    private SmppSessionConfiguration configuration;
    private SmppTrigger smppTrigger;
    private String charsetName;

    /**
     * TODO Add JavaDoc.
     */
    public ItfSmppSessionHandler(SmppSessionConfiguration configuration,
                                 SmppTrigger trigger,
                                 ConnectionProperties triggerConnectionProperties) {
        this.configuration = configuration;
        this.smppTrigger = trigger;
        this.charsetName = (String) triggerConnectionProperties.getOrDefault(CHARSET_NAME, DEFAULT_CHARSET);
    }

    @Override
    public PduResponse firePduRequestReceived(PduRequest pduRequest) {
        PduResponse pduResponse;
        OffsetDateTime started = OffsetDateTime.now();
        UUID currentProject = null;
        try {
            if (this.smppTrigger == null) {
                // Server is alone, without ITF SmppTrigger
                pduResponse = defaultProcessing(pduRequest);
            } else {
                // Server is activated via SmppTrigger, so ITF should process a request
                currentProject = smppTrigger.getTriggerConfigurationDescriptor().getProjectUuid();
                MdcUtils.put(MdcField.PROJECT_ID.toString(), currentProject);
                pduResponse = itfProcessing(pduRequest);
                MetricsAggregateService.incrementIncomingRequestToProject(
                        currentProject, TransportType.SMPP_INBOUND,true);
            }
            return pduResponse;
        } catch (Exception e) {
            if (currentProject != null) {
                MetricsAggregateService.incrementIncomingRequestToProject(
                        currentProject, TransportType.SMPP_INBOUND, false);
            }
            throw e;
        } finally {
            String currentEndPoint = String.format("tcp://%s:%s/%s", configuration.getHost(),
                    configuration.getPort(), configuration.getAddressRange().getAddress());
            if (currentProject != null) {
                MetricsAggregateService.recordIncomingRequestDuration(
                        currentProject, TransportType.SMPP_INBOUND,
                        currentEndPoint, Duration.between(started, OffsetDateTime.now()));
            }
        }
    }

    private PduResponse defaultProcessing(PduRequest pduRequest) {
        if (pduRequest.isRequest()) {
            if (pduRequest.getClass() == SubmitSm.class) {
                SubmitSm sm = (SubmitSm) pduRequest;
                SubmitSmResp response = (SubmitSmResp) pduRequest.createResponse();
                response.setMessageId("MessageID");

                String message = CharsetUtil.decode(sm.getShortMessage(), DEFAULT_CHARSET);
                log.debug("[Client] = {}\n [Message] = {},\n [Phone Number] = {},\n [Source Phone Number] = {}",
                        configuration.getSystemId(),
                        message,
                        sm.getDestAddress(),
                        sm.getSourceAddress());
                return response;
            } else if (pduRequest.getClass() == DeliverSm.class) {
                DeliverSm dlr = (DeliverSm) pduRequest;
                log.debug("Got DELIVER_SM, Msg id={}, Status={}",
                        dlr.getOptionalParameter(SmppConstants.TAG_RECEIPTED_MSG_ID),
                        dlr.getOptionalParameter(SmppConstants.TAG_MSG_STATE));
                return pduRequest.createResponse();
            }
        } else {
            // Temporary, to find a source of heavy load of prod (500 reqs/min)
            log.warn("Don't know how to process pduRequest message {}", pduRequest);
        }
        return pduRequest.createResponse();
    }

    private PduResponse itfProcessing(PduRequest pduRequest) {
        if (pduRequest.isRequest()) {
            if (pduRequest.getClass() == SubmitSm.class) {
                SubmitSm sm = (SubmitSm) pduRequest;
                log.info("SubmitSm message is received: [Client] = {}, [Phone Number] = {}, [Source Phone Number] = {}",
                        configuration.getSystemId(), sm.getDestAddress(), sm.getSourceAddress());
                Message responseMessage;
                try {
                    responseMessage = this.smppTrigger.produceMessageToItf(submitSm2Message(sm));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SubmitSmResp response = (SubmitSmResp) pduRequest.createResponse();
                fillSubmitSmResponse(response, responseMessage);
                return response;
            } else if (pduRequest.getClass() == DeliverSm.class) {
                DeliverSm dlr = (DeliverSm) pduRequest;
                log.debug("DeliverSm message is received: Msg id = {}, Status = {}",
                        dlr.getOptionalParameter(SmppConstants.TAG_RECEIPTED_MSG_ID),
                        dlr.getOptionalParameter(SmppConstants.TAG_MSG_STATE));
                return pduRequest.createResponse();
            }
        }
        return pduRequest.createResponse();
    }

    private Message submitSm2Message(SubmitSm submitSmRequest) {
        Message message = new Message(CharsetUtil.decode(submitSmRequest.getShortMessage(), charsetName));
        message.getHeaders().put("DestAddress", submitSmRequest.getDestAddress().toString());
        message.getHeaders().put("DestAddress_Address", submitSmRequest.getDestAddress().getAddress());
        message.getHeaders().put("DestAddress_Npi", submitSmRequest.getDestAddress().getNpi());
        message.getHeaders().put("DestAddress_Ton", submitSmRequest.getDestAddress().getTon());
        message.getHeaders().put("SourceAddress", submitSmRequest.getSourceAddress().toString());
        message.getHeaders().put("SourceAddress_Address", submitSmRequest.getSourceAddress().getAddress());
        message.getHeaders().put("SourceAddress_Npi", submitSmRequest.getSourceAddress().getNpi());
        message.getHeaders().put("SourceAddress_Ton", submitSmRequest.getSourceAddress().getTon());
        message.getHeaders().put("CommandStatus", submitSmRequest.getCommandStatus());
        if (submitSmRequest.getOptionalParameters() != null) {
            for (Tlv tlv : submitSmRequest.getOptionalParameters()) {
                message.getHeaders().put("OptionalParameters_" + tlv.getTagName(),
                        CharsetUtil.decode(tlv.getValue(), charsetName));
            }
        }
        return message;
    }

    private void fillSubmitSmResponse(SubmitSmResp response, Message responseMessage) {
        Map<String, Object> props = responseMessage.getConnectionProperties();
        response.setResultMessage(responseMessage.getText());
        response.setMessageId((String)(props.getOrDefault(MESSAGE_ID, StringUtils.EMPTY)));
        response.setCommandStatus(Integer.parseInt((String)(props.getOrDefault(COMMAND_STATUS, "0"))));
    }
}

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

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.junit.Ignore;
import org.junit.Test;

import static org.qubership.automation.itf.trigger.smpp.Constants.DEFAULT_CHARSET;

public class SmppServerTest {
    @Ignore
    @Test
    public void testSendSubmitSm() throws Exception {
        SmppClient smppClient = new DefaultSmppClient();
        SmppSessionConfiguration smppSessionConfiguration = new SmppSessionConfiguration(SmppBindType.TRANSCEIVER, "TEST CLIENT", "password");
        // Connect to local SMPP server:
        smppSessionConfiguration.setHost("localhost");
        smppSessionConfiguration.setPort(38668); // 36888

        SmppSession smppSession = smppClient.bind(smppSessionConfiguration,
                new ItfSmppSessionHandler(smppSessionConfiguration, null, new ConnectionProperties()));
        Thread.sleep(500);
        SubmitSm submitSm = createSubmitSm("Test", "79111234567", "Hello friend!", DEFAULT_CHARSET);
        submitSm.setRegisteredDelivery((byte)1);
        System.out.println("Try to send message");

        SubmitSmResp response = smppSession.submit(submitSm, 11000);
        System.out.println("Server RESPONSE  = " + response.getResultMessage());
        System.out.println("Server RESPONSE  = " + response);
        System.out.println("Message sent");
        System.out.println("Destroy session");

        smppSession.close();
        smppSession.destroy();

        System.out.println("Destroy client");

        smppClient.destroy();

        System.out.println("Bye!");
    }

    public static SubmitSm createSubmitSm(String src, String dst, String text, String charset) throws SmppInvalidArgumentException {
        SubmitSm sm = new SubmitSm();

        // For alpha numeric will use: TON=5, NPI=0
        sm.setSourceAddress(new Address((byte)5, (byte)0, src));

        // For national numbers will use: TON=1, NPI=1
        sm.setDestAddress(new Address((byte)1, (byte)1, dst));

        // Set datacoding to UCS-2
        sm.setDataCoding((byte)8);

        // Encode text
        sm.setShortMessage(CharsetUtil.encode(text, charset));

        Tlv scInterfaceVersion = new Tlv(SmppConstants.TAG_SC_INTERFACE_VERSION, new byte[] {Byte.parseByte("4")});
        sm.addOptionalParameter(scInterfaceVersion);

        return sm;
    }
}

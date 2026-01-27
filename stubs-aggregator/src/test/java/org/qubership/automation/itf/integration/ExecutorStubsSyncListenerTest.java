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

package org.qubership.automation.itf.integration;

import java.math.BigInteger;

import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.qubership.automation.itf.activation.StubJmsListeners;
import org.qubership.automation.itf.integration.config.jms.connection.StubsIntegrationConfig;
import org.qubership.automation.itf.communication.StubsIntegrationMessageSender;
import org.qubership.automation.itf.core.model.communication.StubUser;
import org.qubership.automation.itf.core.model.communication.message.TriggerStatusMessage;
import org.qubership.automation.itf.core.util.constants.TriggerState;

@Ignore("Only for manual execution for check listener")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {StubsIntegrationConfig.class, StubsIntegrationMessageSender.class})
public class ExecutorStubsSyncListenerTest {

    @Autowired
    StubJmsListeners listener;

    @Autowired
    StubsIntegrationMessageSender sender;

    @Test
    public void testCreateListener() throws InterruptedException {
        TriggerStatusMessage message = new TriggerStatusMessage(TriggerStatusMessage.ObjectType.TRIGGER,
                new BigInteger("123456789"),
                TriggerState.ACTIVE.toString(),
                "description",
                new StubUser("TestUser"),
                "sessionID");

        sender.send(message, UUID.randomUUID());

        System.out.println("Starting listener ..." );
        Thread.sleep(300*1000);
        System.out.println("Done.");


    }
}

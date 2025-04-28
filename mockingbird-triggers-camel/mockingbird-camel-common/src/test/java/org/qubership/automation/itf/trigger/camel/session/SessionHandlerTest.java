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

package org.qubership.automation.itf.trigger.camel.session;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.util.transport.service.SessionHandler;

public class SessionHandlerTest {

    @BeforeMethod
    public void addMessageBeforeGet() {
        Message message = new Message();
        message.setText("ZERO");
        SessionHandler.INSTANCE.addMessage("0", message);
    }

    @Test
    public void getMessage() throws Exception {
        Message message = SessionHandler.INSTANCE.getMessage("0");
        assertEquals("ZERO", message.getText());
    }

    @Test
    public void addMessage() throws Exception {
        Message message = new Message();
        SessionHandler.INSTANCE.addMessage("1", message);
        Message message1 = SessionHandler.INSTANCE.getMessage("1");
        assertEquals(message, message1);
    }

}

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

package org.qubership.automation.itf.trigger.cli.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CliMessageBuilderTest {
    @Test
    void testMatcherFoundWithFilledResult() {
        CliMessageBuilder messageBuilder = new CliMessageBuilder("t$", false);
        messageBuilder.append("test\ntest");
        CliMessageBuilder.BuilderResult matches = messageBuilder.retrieveResult();
        assertTrue(matches.isMatches());
        assertEquals("test\ntest", matches.getResult());
    }

    @Test
    void testMatcherNotFoundWithEmptyResult() {
        CliMessageBuilder messageBuilder = new CliMessageBuilder("^a", false);
        messageBuilder.append("test\ntest");
        CliMessageBuilder.BuilderResult matches = messageBuilder.retrieveResult();
        assertFalse(matches.isMatches());
        assertNull(matches.getResult());
    }

    @Test
    void testMatcherFoundAfterAppend() {
        CliMessageBuilder messageBuilder = new CliMessageBuilder("\r\n", false);
        messageBuilder.append("test\ntest");
        CliMessageBuilder.BuilderResult matches = messageBuilder.retrieveResult();
        assertFalse(matches.isMatches());
        assertNull(matches.getResult());

        messageBuilder.append("\r\n");
        matches = messageBuilder.retrieveResult();
        assertTrue(matches.isMatches());
        assertEquals("test\ntest\r\n", matches.getResult());
    }

    @Test
    void testMatcherFoundTwoResultAfterAppend() {
        CliMessageBuilder messageBuilder = new CliMessageBuilder("\n", false);
        CliMessageBuilder.BuilderResult matches;
        messageBuilder.append("test\ntest");
        messageBuilder.append("\r\n");
        matches = messageBuilder.retrieveResult();
        assertTrue(matches.isMatches());
        assertEquals("test\n", matches.getResult());
        matches = messageBuilder.retrieveResult();
        assertTrue(matches.isMatches());
        assertEquals("test\r\n", matches.getResult());
    }

}

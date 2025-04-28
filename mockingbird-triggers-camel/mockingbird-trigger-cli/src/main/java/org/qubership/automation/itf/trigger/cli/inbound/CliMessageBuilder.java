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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;

public class CliMessageBuilder {
    private final StringBuilder builder = new StringBuilder();
    private final Pattern pattern;
    @Getter
    private final boolean isAllowedEmpty;
    @Getter
    private final String delimiter;

    /**
     * Prepare and compile pattern based on delimiter value.
     * An empty delimiter means the new-line character. So, we must extract the entire string.
     * Otherwise, we must extract as little characters as possible (+? quantifier)!
     * It was an attempt (for ATPII-43036)
     * to change constructor such way:
     *  - Instead of simple adding of delimiter to pattern, it is enclosed into Pattern.quote().
     *      - it seems more correct, because, without it, special regexp characters would break pattern compiling,
     *      for example, '[' delimiter.
     *      - And, one more reason, especially for '.' delimiter: current implementation would extract entire string
     *      in case there is '.' inside, what is incorrect.
     * But dev testing failed, so commit was reverted. May be, later we come back to the problem.
     */
    public CliMessageBuilder(String delimiter, boolean isAllowedEmpty) {
        String regexPattern = isAllowedEmpty ? ".*" : ".+";
        this.delimiter = delimiter;
        this.isAllowedEmpty = isAllowedEmpty;
        this.pattern = Pattern.compile(
                delimiter.isEmpty() ? regexPattern : regexPattern + "?" + delimiter,
                Pattern.DOTALL);
    }

    /**
     * Returns result of matching.
     */
    public BuilderResult retrieveResult() {
        return retrieveResult(pattern);
    }

    /**
     * Returns result of matching.
     */
    public BuilderResult retrieveResult(Pattern pattern) {
        Matcher matcher = pattern.matcher(builder.toString());
        if (matcher.find()) {
            String group = matcher.group();
            builder.delete(0, builder.toString().indexOf(group) + group.length());
            return new BuilderResult(true, group);
        }
        return new BuilderResult(false, null);
    }

    public void append(String message) {
        this.builder.append(message);
    }

    public static class BuilderResult {
        private boolean isMatches;
        @Getter
        private String result;

        public BuilderResult(boolean isMatches, String result) {
            this.isMatches = isMatches;
            this.result = result;
        }

        public boolean isMatches() {
            return isMatches;
        }

    }
}

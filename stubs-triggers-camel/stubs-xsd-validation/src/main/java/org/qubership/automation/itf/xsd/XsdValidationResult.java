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

package org.qubership.automation.itf.xsd;

import java.util.Arrays;

import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang3.StringUtils;

public class XsdValidationResult {

    private Exception exception;
    private String message;
    private boolean isFailed;
    private String[] schemaDocuments;

    public XsdValidationResult() {
    }

    public XsdValidationResult(String message) {
        this.message = message;
    }

    public Exception getException() {
        return exception;
    }

    public XsdValidationResult setException(Exception error) {
        this.exception = error;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public XsdValidationResult setMessage(String message) {
        this.message = message;
        return this;
    }

    public boolean isFailed() {
        return isFailed;
    }

    public XsdValidationResult setFailed(boolean failed) {
        isFailed = failed;
        return this;
    }

    public String[] getSchemaDocuments() {
        return this.schemaDocuments == null ? null : Arrays.copyOf(this.schemaDocuments, this.schemaDocuments.length);
    }

    public XsdValidationResult setSchemaDocuments(String[] schemaDocuments) {
        this.schemaDocuments = Arrays.copyOf(schemaDocuments, schemaDocuments.length);
        return this;
    }

    /**
     * TODO Add JavaDoc.
     */
    public XsdValidationResult setSchemaDocuments(StreamSource[] schemaDocuments) {
        this.schemaDocuments = new String[schemaDocuments.length];
        for (int i = 0; i < schemaDocuments.length; i++) {
            this.schemaDocuments[i] = schemaDocuments[i].getSystemId();
        }
        return this;
    }

    @Override
    public String toString() {
        return "XsdValidationResult{"
                + (isFailed
                        ? exception == null
                        ? "No errors, may be not validated yet"
                        : "error='" + exception.getMessage() + '\''
                        : "Validation is passed successfully")
                + "; schemaDocuments='"
                + (schemaDocuments == null ? StringUtils.EMPTY : Arrays.toString(schemaDocuments))
                + "'; message='" + message + "'}";
    }
}

/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.action.xml;


/**
 * Defines an exception resulting from the failure of configuring an XML "screen" operation.
 *
 * <p>Such exceptions often arise due to a malformed XPath expressions when creating a javax.xml.xpath.XPathExpression,
 * which produces a causal exception type of java.xml.xpath.XPathExpressionException.</p>
 *
 * <p>In addition to the standard reason and cause of the exception, this class defines:</p>
 * <ul>
 *     <li>index:  The index in the list of the failing result operation, or -1 if not set.</li>
 *     <li>argument index:  The argument index in the XPath expression that failed if it could be determined, or -1 if
 * not set.</li>
 * </ul>
 */
public class XmlScreenOperationConfigurationException extends RuntimeException {

    int index;
    int argIndex;

    /**
     * Creates an exception with reason 'reason' and cause 'e'.
     *
     * <p>Sets the index to -1 and the argument index to -1.</p>
     *
     * @param reason the reason for the exception
     * @param e the cause of the exception
     */
    public XmlScreenOperationConfigurationException(String reason, Throwable e) {
        super(reason, e);
        init();
    }

    /**
     * Creates an exception with reason 'reason'.
     *
     * <p>Sets the cause of the exception to 'null' and index to -1, and the argument index to -1.</p>
     *
     * @param reason the reason for the exception
     */
    public XmlScreenOperationConfigurationException(String reason) {
        super(reason);
        init();
    }

    /**
     * Creates a new exception with the given parameters, setting the cause to 'null'.
     *
     * @param reason the reason for the exception
     * @param index index in the list of the failing result operation
     * @param argIndex argument index in the result operation that failed
     */
    public XmlScreenOperationConfigurationException(String reason, int index, int argIndex) {
        super(reason);
        this.index = index;
        this.argIndex = argIndex;
    }

    /**
     * Initializes the member variables to default uninitialized values.
     */
    private void init() {
        this.index = -1;
        this.argIndex = -1;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }

    public void setArgIndex(int argIndex) {
        this.argIndex = argIndex;
    }

    public int getArgIndex() {
        return this.argIndex;
    }
}

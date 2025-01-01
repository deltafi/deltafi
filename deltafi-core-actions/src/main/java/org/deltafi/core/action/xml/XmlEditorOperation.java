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


import lombok.Getter;
import lombok.Setter;

/**
 * Defines a generic operation for editing XML.
 *
 * <p>Operation types are defined as below.  A subclassing operation should define a valid operation type (e.g., not
 * OperationType.NOT_INITIALIZED).</p>
 * <ul>
 *     <li>MODIFY: an operation type intended to modify content.  Produces a TransformResult containing content that may
 *     or may not be modified. If an error occurs, then an ErrorResult should be returned.</li>
 *     <li>SCREEN: an operation type intended to screen content (e.g., allowing it to pass or not) but does not modify
 *     content.  Produces a FilterResult or ErrorResult, else the content is unmodified and a TransformResult is
 *     returned. If an error occurs, then an ErrorResult should be returned.</li>
 *     <li>NOT_INITIALIZED: the operation type is not initialized</li>
 * </ul>
 *
 * <p>For consistency, subclasses should be invoked by defining an 'apply' method with arguments sufficient and
 * necessary to support the required operation.</p>
 *
 */
@Getter
@Setter
public abstract class XmlEditorOperation {

    public enum OperationType {
        MODIFY("modify"),
        SCREEN("screen"),
        NOT_INITIALIZED("not initialized");

        public final String label;

        OperationType(String label) {
            this.label = label;
        }
    }

    private OperationType operationType;

    /**
     * Creates a new XmlEditorOperation with an uninitialized operation type (e.g., OperationType.NOT_INITIALIZED).
     *
     */
    public XmlEditorOperation() {
        operationType = OperationType.NOT_INITIALIZED;
    }

    /**
     * Creates a new XmlEditorOperation with the specified operation type.
     *
     * @param operationType the operation type
     */
    public XmlEditorOperation(OperationType operationType) {
        this.operationType = operationType;
    }
}

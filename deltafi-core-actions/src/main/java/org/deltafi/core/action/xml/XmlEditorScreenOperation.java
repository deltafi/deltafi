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


import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;


/**
 * Defines a generic "screen" operation.  A screen operation should produce a FilterResult or ErrorResult, else
 * optionally indicate that no action should be taken and the content should be returned unmodified in a
 * TransformResult. If an error occurs, then an ErrorResult should be returned.
 *
 * <p>In the course of editing XML content, some operations may wish to filter or error the content rather than modify
 * it.  This class provides a generic concept for filtering and/or erroring content.</p>
 *
 * <p>Implementing subclasses must override the 'apply' method, where it is expected to at least return a FilterResult
 * or an ErrorResult.</p>
 */
public abstract class XmlEditorScreenOperation extends XmlEditorOperation {

    /**
     * Initializes an empty transform operation.
     */
    public XmlEditorScreenOperation() {
        super(OperationType.SCREEN);
    }

    /**
     * Implementing subclasses should override this method to apply specific logic, where the return type is expected to
     * be a FilterResult or ErrorResult. A 'null' return type may indicate that no action should be taken on the
     * content.
     *
     * <p>The implementing class should document possible return types, meaning of 'null', and exceptions.</p>
     *
     * @param context the ActionContext for the XML content
     * @param xmlContent the XML content
     * @return expected to return a FilterResult or ErrorResult
     */
    public abstract TransformResultType apply(ActionContext context, String xmlContent) throws Exception;

}

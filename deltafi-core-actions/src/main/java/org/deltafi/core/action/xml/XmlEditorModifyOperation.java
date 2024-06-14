/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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


import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Defines a transform operation for modifying XML.
 *
 * <p>A javax.xml.transform.Transformer is set either with a constructor or the 'setTransformer' method.  The
 * transformer is applied to XML content with the 'apply' method.</p>
 *
 */
public class XmlEditorModifyOperation extends XmlEditorOperation {

    Transformer transformer;

    /**
     * Initializes an empty transform operation.
     */
    public XmlEditorModifyOperation() {
        super(OperationType.MODIFY);
    }

    /**
     * Initializes a transform operation with a transform.
     *
     * @param transformer the transformer to apply
     */
    public XmlEditorModifyOperation(Transformer transformer) {
        this();
        this.transformer = transformer;
    }

    /**
     * Sets the transformer.
     *
     * @param transformer the transformer to apply
     */
    public void setTransformer(Transformer transformer) {
        this.transformer = transformer;
    }

    /**
     * Applies the transform to the XML content and returns a String result.
     *
     * @param xmlContent the XML content to transform
     * @return a String result of the transformed XML
     * @throws TransformerException if an error occurred during the transform
     */
    public String apply(String xmlContent) throws TransformerException {
        Source xmlSource = new StreamSource(new StringReader(xmlContent));
        StringWriter writer = new StringWriter();
        StreamResult transformedXml = new StreamResult(writer);
        transformer.transform(xmlSource, transformedXml);
        return writer.toString();
    }

}

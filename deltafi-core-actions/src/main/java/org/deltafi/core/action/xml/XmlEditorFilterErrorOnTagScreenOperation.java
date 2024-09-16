/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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


import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a screen operation to filter or error content based on the absence or presence of XML tags, or indicate
 * that no action should be taken on the content.
 *
 * <p>Implements the following commands. See documentation
 * <a href="https://docs.deltafi.org/#/core-actions/xml-editor">on the Internet</a> or on your
 * <a href="http://local.deltafi.org/docs/#/core-actions/xml-editor">local DeltaFi</a>.
 * <ul>
 *     <li>filterOnTag|errorOnTag &lt;search xpath&gt; "&lt;message&gt;"</li>
 *     <li>filterOnTag|errorOnTag not &lt;search xpath&gt; "&lt;message&gt;"</li>
 *     <li>filterOnTag|errorOnTag and|nand|or|nor|xor|xnor &lt;search xpath 1&gt; ... &lt;search xpath n&gt;
 *     "&lt;message&gt;"</li>
 * </ul>
 */
public class XmlEditorFilterErrorOnTagScreenOperation extends XmlEditorScreenOperation {

    public enum Command {
        FILTER("filter"),
        ERROR("error"),
        NOT_INITIALIZED("not initialized");

        public final String label;

        private Command(String label) {
            this.label = label;
        }
    }

    public enum Operator {
        NONE("none"),
        AND("and"),
        NAND("nand"),
        OR("or"),
        XOR("xor"),
        NOR("nor"),
        XNOR("xnor"),
        NOT("not"),
        NOT_INITIALIZED("not initialized");

        public final String label;

        private Operator(String label) {
            this.label = label;
        }
    }

    private Command command;
    private Operator operator;
    private List<XPathExpression> xpathQueryList;
    private String message;

    /**
     * Creates a new, uninitialized XmlEditorFilterErrorOnTagScreenOperation.
     */
    public XmlEditorFilterErrorOnTagScreenOperation() {
        command = Command.NOT_INITIALIZED;
        operator = Operator.NOT_INITIALIZED;
        xpathQueryList = new ArrayList<>();
        message = "";
    }

    /**
     * Creates a new XmlEditorFilterErrorOnTagScreenOperation, initialized the given parameters.
     *
     * @param command the command to set, either Command.FILTER to filter content or Command.ERROR to error content
     * @param operator the operator to set
     * @param xpathQueryList the list of one or more XPath queries to define
     * @param message the message to set for any filter or error action
     */
    public XmlEditorFilterErrorOnTagScreenOperation(Command command, Operator operator,
                                                    List<XPathExpression> xpathQueryList, String message) {
        super();
        this.command = command;
        this.operator = operator;
        this.xpathQueryList = xpathQueryList;
        this.message = message;
    }

    /**
     * Invokes the screen operation, configured at instantiation, on the XML content 'xmlContent'.  Filtered or errored
     * content results use the given action context 'context'.
     *
     * <p>Returns a FilterResult or ErrorResult if the 'xmlContent' should be filtered or errored, else returns 'null'
     * to indicate that no action should be taken on the 'xmlContent'.</p>
     *
     * @param context the ActionContext for the XML content
     * @param xmlContent the XML content
     * @return a FilterResult or ErrorResult, depending on the configured command, if the content should be filtered
     * else 'null' to indicate that no action should be taken on the content
     * @throws XPathExpressionException an exception occurred during the evaluation of an XPath
     * @throws ParserConfigurationException an exception occurred in the construction of an XML document from the XML
     * content
     * @throws IOException an exception occurred in the construction of an XML document from the XML content
     * @throws SAXException an exception occurred in the construction of an XML document from the XML content
     */
    @Override
    public TransformResultType apply(ActionContext context, String xmlContent)
            throws XPathExpressionException,ParserConfigurationException, IOException, SAXException {

        Document xmlDocument = getXmlDocument(xmlContent);
        TransformResultType result = null;

        boolean actOnResult = switch (operator) {
            case Operator.NONE -> applyWithOperatorNone(xmlDocument);
            case Operator.NOT  -> applyWithOperatorNot(xmlDocument);
            case Operator.AND  -> applyWithOperatorAnd(xmlDocument);
            case Operator.NAND -> applyWithOperatorNand(xmlDocument);
            case Operator.OR   -> applyWithOperatorOr(xmlDocument);
            case Operator.NOR  -> applyWithOperatorNor(xmlDocument);
            case Operator.XOR  -> applyWithOperatorXor(xmlDocument);
            case Operator.XNOR -> applyWithOperatorXnor(xmlDocument);
            default            -> false;
        };

        if (actOnResult) {
            result = createResult(context);
        }

        return result;
    }

    /**
     * Creates and returns either a FilterResult or ErrorResult, based on the member variable command, using the
     * ActionContext 'context'.  Populates the member variable 'message' as the message for the returned result.
     *
     * @param context the ActionContext
     * @return a FilterResult or ErrorResult
     */
    private TransformResultType createResult(ActionContext context) {

        TransformResultType result;

        if (command == Command.FILTER) {
            result = new FilterResult(context, message);
        } else {
            // must be Command.ERROR
            result = new ErrorResult(context, message);
        }

        return result;
    }

    /**
     * Convert the String XML content 'xmlContent' to an org.w3c.dom.Document.
     *
     * @param xmlContent the XML content as a String
     * @return an org.w3c.dom.Document representation of the input 'xmlContent'
     * @throws ParserConfigurationException an exception occurred in the construction of an XML document from the XML
     * content
     * @throws IOException an exception occurred in the construction of an XML document from the XML content
     * @throws SAXException an exception occurred in the construction of an XML document from the XML content
     */
    private Document getXmlDocument(String xmlContent) throws ParserConfigurationException, IOException, SAXException {
        InputSource source = new InputSource(new StringReader(xmlContent));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(source);
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.NONE'.
     *
     * <p>The 'Operator.NONE' uses a single XPath expression and returns 'true' if the XPath query is true (e.g. the
     * XPath is found) else returns 'false' (e.g. the XPath is not found).</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException an exception occurred during the evaluation of an XPath
     */
    private boolean applyWithOperatorNone(Document xmlDocument) throws XPathExpressionException {

        boolean actOnResult = false;

        XPathExpression xpath = xpathQueryList.getFirst();

        NodeList nodeList = (NodeList) xpath.evaluate(xmlDocument, XPathConstants.NODESET);

        if (nodeList.getLength() > 0) {
            actOnResult = true;
        }

        return actOnResult;
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.NOT'.
     *
     * <p>The 'Operator.NOT' uses a single XPath expression and returns 'true' if the XPath query is true (e.g. the
     * XPath is found) else returns 'false' (e.g. the XPath is not found).</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException if an XPath expression exception occurs
     */
    private boolean applyWithOperatorNot(Document xmlDocument) throws XPathExpressionException {
        return !(applyWithOperatorNone(xmlDocument));
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.AND'.
     *
     * <p>The 'Operator.AND' uses two or more XPath expressions and returns 'true' if all XPath queries are true (e.g.
     * the XPaths are found) else returns 'false' (e.g. one or more XPaths was not found).</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException if an XPath expression exception occurs
     */
    private boolean applyWithOperatorAnd(Document xmlDocument) throws XPathExpressionException {

        boolean actOnResult = true;

        NodeList nodeList;

        for (XPathExpression xpath : xpathQueryList) {
            nodeList = (NodeList) xpath.evaluate(xmlDocument, XPathConstants.NODESET);
            if (nodeList.getLength() == 0) {
                actOnResult = false;
                break;
            }
        }

        return actOnResult;
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.NAND'.
     *
     * <p>The 'Operator.NAND' uses two or more XPath expressions and returns 'false' if all XPath queries are true (e.g.
     * the XPaths are found) else returns 'false' (e.g. one or more XPaths was found).  The operation is an
     * inverted "and" (e.g., inverted Operator.AND) function.</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException if an XPath expression exception occurs
     */
    private boolean applyWithOperatorNand(Document xmlDocument) throws XPathExpressionException {
        return !applyWithOperatorAnd(xmlDocument);
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.OR'.
     *
     * <p>The 'Operator.OR' uses two or more XPath expressions and returns 'true' if at least one XPath query is true
     * (e.g. an XPath is found) else returns 'false' (e.g. no XPaths were not found).  Implements an "inclusive or"
     * logic operation.</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException if an XPath expression exception occurs
     */
    private boolean applyWithOperatorOr(Document xmlDocument) throws XPathExpressionException {

        boolean actOnResult = false;

        NodeList nodeList;

        for (XPathExpression xpath : xpathQueryList) {
            nodeList = (NodeList) xpath.evaluate(xmlDocument, XPathConstants.NODESET);
            if (nodeList.getLength() > 0) {
                actOnResult = true;
                break;
            }
        }

        return actOnResult;
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.NOR'.
     *
     * <p>The 'Operator.NOR' uses two or more XPath expressions and returns 'true' if no XPath query is true
     * (e.g. no XPath is found) else returns 'false' (e.g. one or more XPaths were found).  Implements an inverted
     * "inclusive or" logic operation.</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException if an XPath expression exception occurs
     */
    private boolean applyWithOperatorNor(Document xmlDocument) throws XPathExpressionException {
        return !applyWithOperatorOr(xmlDocument);
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.XOR'.
     *
     * <p>The 'Operator.XOR' uses two or more XPath expressions and returns 'true' if at least one XPath query is true
     * (e.g. an XPath is found) and at least one XPath query returns 'false' (e.g. an XPath was not found), else returns
     * 'false' meaning no XPaths were found or all XPaths were found.  Implements an "exclusive or" logic operation.</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException if an XPath expression exception occurs
     */
    private boolean applyWithOperatorXor(Document xmlDocument) throws XPathExpressionException {

        boolean xpathFound = false;
        boolean xpathNotFound = false;

        NodeList nodeList;

        for (XPathExpression xpath : xpathQueryList) {
            nodeList = (NodeList) xpath.evaluate(xmlDocument, XPathConstants.NODESET);
            if (nodeList.getLength() == 0) {
                xpathNotFound = true;
                if (xpathFound) {
                    break;
                }
            } else {
                xpathFound = true;
                if (xpathNotFound) {
                    break;
                }
            }
        }

        return (xpathFound && xpathNotFound);
    }

    /**
     * Returns boolean 'true' to act on the result (e.g., filter or error) and 'false' otherwise by applying the
     * XPath expressions in the member variable 'xpathQueryList' and using the operator 'Operator.XNOR'.
     *
     * <p>The 'Operator.XNOR' uses two or more XPath expressions and returns 'true' if all XPath queries are true
     * (e.g. all XPaths are found) or all XPath queries return 'false' (e.g. no XPaths were found), else returns
     * 'false' meaning that at least one XPath query was found and at least XPath query was not found.  Implements an
     * inverted "exclusive or" logic operation.</p>
     *
     * @param xmlDocument the XML document to inspect
     * @return 'true' to act on the result (e.g., filter or error) and 'false' otherwise
     * @throws XPathExpressionException if an XPath expression exception occurs
     */
    private boolean applyWithOperatorXnor(Document xmlDocument) throws XPathExpressionException {
        return !applyWithOperatorXor(xmlDocument);
    }

}

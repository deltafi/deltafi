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

import org.apache.tika.utils.StringUtils;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Stream;


// a data container to use when parsing String command expressions, where a single next tokenized item is wanted
record NextExpressionElement(String next, String remainder) { }

// a data container to use when parsing String command expressions, where 'n' next tokenized items are wanted
record NextExpressionElements(List<String> nextList, String remainder) { }

/**
 * Provides simple XML editing/grooming functionality.  The XmlEditor can be used to modify XML content or filter/error
 * XML content based on the presence or absence of tags in the XML content.
 *
 * <p><a href="https://docs.deltafi.org/#/core-actions/xml-editor">See documentation</a> for features and
 * usage.</p>
 *
 */
@Component
public class XmlEditor extends TransformAction<XmlEditorParameters> {

    private static final String XSLT_START =
            "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">";

    private static final String XSLT_END = "</xsl:stylesheet>";

    private static final String XSLT_IDENTITY_TEMPLATE = """
            <xsl:template match="@* | node()">
                  <xsl:copy>
                        <xsl:apply-templates select="@* | node()" />
                  </xsl:copy>
            </xsl:template>""";

    private static final String XSLT_NEWLINE = """
            <xsl:template match="/">
                <xsl:text>&#xa;</xsl:text>
                <xsl:apply-templates/>
            </xsl:template>""";

    private static final String MODIFY_OPERATION_RENAME_TAG_XSLT =
            XSLT_START +
            """
                <xsl:template match="%s">
                    <%s>
                        <xsl:apply-templates select="@* | node()" />
                    </%s>
                </xsl:template>
            """ +
            XSLT_IDENTITY_TEMPLATE +
            XSLT_NEWLINE +
            XSLT_END;

    private static final String MODIFY_OPERATION_REMOVE_TAG_XSLT =
            XSLT_START +
            XSLT_IDENTITY_TEMPLATE +
            """
                <xsl:template match="%s"/>
            """ +
            XSLT_NEWLINE +
            XSLT_END;

    private static final String MODIFY_OPERATION_REPLACE_TAG_XSLT =
            XSLT_START +
            XSLT_IDENTITY_TEMPLATE +
            """
                <xsl:template match="%s">
                    %s
                </xsl:template>
            """ +
            XSLT_NEWLINE +
            XSLT_END;

    private static final String MODIFY_OPERATION_REPLACE_TAG_CONTENT_XSLT =
            XSLT_START +
            XSLT_IDENTITY_TEMPLATE +
            """
                <xsl:template match="%s">
                    <xsl:copy>
                        <xsl:apply-templates select="@*" />
                        <xsl:text>%s</xsl:text>
                    </xsl:copy>
                </xsl:template>
            """ +
            XSLT_NEWLINE +
            XSLT_END;

    private static final String MODIFY_OPERATION_APPEND_CHILD_XSLT =
            XSLT_START +
            XSLT_IDENTITY_TEMPLATE +
            """
                <xsl:template match="%s">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:copy-of select="node()"/>
                        %s<xsl:text>&#xa;</xsl:text>
                    </xsl:copy>
                </xsl:template>
            """ +
            XSLT_NEWLINE +
            XSLT_END;


    private static final String MODIFY_OPERATION_PREPEND_CHILD_XSLT =
            XSLT_START +
                    XSLT_IDENTITY_TEMPLATE +
                    """
                        <xsl:template match="%s">
                            <xsl:copy>
                                <xsl:copy-of select="@*"/>
                                <xsl:text>&#xa;</xsl:text>%s
                                <xsl:copy-of select="node()"/>
                            </xsl:copy>
                        </xsl:template>
                    """ +
                    XSLT_NEWLINE +
                    XSLT_END;


    // regex for a String command expression to split on (1) comma with 0 or more spaces or (2) 1 or more spaces
    private static final String ARGUMENT_SEPARATOR_REGEX_STRING = "(,\\s*)|( )+";

    private static final String COMMAND_RENAME_TAG          = "renameTag";
    private static final String COMMAND_REMOVE_TAG          = "removeTag";
    private static final String COMMAND_REPLACE_TAG         = "replaceTag";
    private static final String COMMAND_REPLACE_TAG_CONTENT = "replaceTagContent";
    private static final String COMMAND_APPEND_CHILD        = "appendChild";
    private static final String COMMAND_PREPEND_CHILD       = "prependChild";
    private static final String COMMAND_FILTER_ON_TAG       = "filterOnTag";
    private static final String COMMAND_ERROR_ON_TAG        = "errorOnTag";

    private static final String OPERATOR_AND   = "and";
    private static final String OPERATOR_NAND  = "nand";
    private static final String OPERATOR_OR    = "or";
    private static final String OPERATOR_XOR   = "xor";
    private static final String OPERATOR_NOR   = "nor";
    private static final String OPERATOR_XNOR  = "xnor";
    private static final String OPERATOR_NOT   = "not";

    private static final String ERROR_MSG_CREATING_XSLT = "Error creating XSLT transformer";

    private static final String ERROR_MSG_CREATING_XPATH = "Error creating XPath expression";

    private static final int SCREEN_MESSAGE_LENGTH_MINIMUM = 3;

    private static final int MAX_CACHE_SIZE = 1000;

    private final Map<String, Transformer> transformerCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Transformer> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    /**
     * Instantiates a new XmlEditor.
     */
    public XmlEditor() {
        super("Transforms XML content.");
    }

    /**
     * Invokes the XmlEditor transformation.
     *
     * <p>Depending on the configured parameters in 'params', returns:</p>
     * <ul>
     *     <li>a TransformResult where the XML content may be modified if a configured command identified content to
     *     be modified (else content may be returned unmodified)</li>
     *     <li>a FilterResult or ErrorResult if a configured command identified content to be filtered or errored</li>
     *     <li>an ErrorResult if an error occurred</li>
     * </ul>
     *
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param input Action input from the DeltaFile
     * @return a TransformResult, FilterResult, or ErrorResult depending on the configured command or an ErrorResult if
     * an error occurred
     */
    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull XmlEditorParameters params,
                                         @NotNull TransformInput input) {

        /* Parses, tokenizes, and validates the command expressions from 'params'.
         *
         * Retrieve the list of editing commands from XmlEditorParameters 'params' object, where each command is a
         * String.  Return a list of parsed, tokenized, and validated commands, where each command has been converted
         * from a string to a tokenized list of strings (command and any arguments).
         */
        List<List<String>> commandExpressionList;

        try {
            // get parsed and validated command expressions
            commandExpressionList = getCommandExpressionList(params.getXmlEditingCommands());
        } catch (XmlCommandExpressionParseException e) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Error parsing XML command expression at index ").append(e.getIndex());
            if (e.getCommandExpression() != null) {
                stringBuilder.append(" with command '").append(e.getCommandExpression()).append("'");
                if (e.getArgIndex() > -1) {
                    stringBuilder.append(" at argument index ").append(e.getArgIndex());
                }
            }
            return new ErrorResult(context, stringBuilder.toString(), e);
        }

        /* For each tokenized command expression in 'commandExpressionList', create an XmlEditorOperation.
         *
         * An XmlEditorOperation wraps different XML operations, such as:
         *    - modify: uses an XmlEditorModifyOperation to return content, optionally modified
         *    - screen: uses an XmlEditorScreenOperation to indicate a FilterResult or ErrorResult instead of a
         *      TransformResult
         */

        List<XmlEditorOperation> operationList;

        try {
            operationList = getOperationList(commandExpressionList);
        } catch (XmlScreenOperationConfigurationException e) {
            return new ErrorResult(context, "Error creating XPath expression from XML command expression"
                    + " at index " + e.getIndex()
                    + " with command '" + commandExpressionList.get(e.getIndex()).getFirst() + "'"
                    + " at argument index " + e.getArgIndex(), e);
        } catch (XmlModifyOperationConfigurationException e) {
            return new ErrorResult(context, "Error creating XSLT from XML command expression at index "
                    + e.getIndex()
                    + " with command '" + commandExpressionList.get(e.getIndex()).getFirst() + "'", e);
        }

        /* Apply each XmlEditorOperation in 'operationList' to the content, if that content should be transformed.
         *
         * Based on the type of XmlEditorOperation, the outcome could be modified/unmodified content for a
         * TransformResult or a FilterResult or ErrorResult.
         */

        /* The result that will be populated and returned, if:
         *    - an XmlEditorScreenOperation does not cause the return of a FilterResult or ErrorResult
         *    - no exception occurs, which causes the return of an ErrorResult
         */
        TransformResult result = new TransformResult(context);

        TransformResultType resultOperation;

        for (int i = 0; i < input.getContent().size(); i++) {

            ActionContent content = input.getContent().get(i);

            if (!params.contentMatches(content.getName(), content.getMediaType(), i)) {
                result.addContent(content);
                continue;
            }

            String xmlContent = content.loadString();

            XmlEditorOperation operation;

            for (int operationIndex = 0; operationIndex < operationList.size(); operationIndex++) {

                operation = operationList.get(operationIndex);

                if (operation.getOperationType() == XmlEditorOperation.OperationType.MODIFY) {
                    try {
                        xmlContent = ((XmlEditorModifyOperation) operation).apply(xmlContent);
                    } catch (Exception e) {
                        return new ErrorResult(context,
                                "Error applying 'modify' operation to content at index " + i
                                        + " using XML command expression at index " + operationIndex
                                        + " with command '"
                                        + commandExpressionList.get(operationIndex).getFirst() + "'", e);
                    }
                } else if (operation.getOperationType() == XmlEditorOperation.OperationType.SCREEN) {

                    try {
                        resultOperation = ((XmlEditorScreenOperation) operation).apply(context, xmlContent);
                    } catch (Exception e) {
                        return new ErrorResult(context,
                                "Error applying 'screen' operation to content at index " + i
                                        + " using XML command expression at index " + operationIndex
                                        + " with command '"
                                        + commandExpressionList.get(operationIndex).getFirst() + "'", e);
                    }

                    if (resultOperation != null) {
                        return resultOperation;
                    }

                }
            }

            result.addContent(ActionContent.saveContent(context, xmlContent, content.getName(),
                    MediaType.APPLICATION_XML));
        }

        return result;
    }

    /**
     * Returns a list of parsed and validated command expressions from the raw list of command expressions in
     * 'unparsedCommandExpressionList'.
     *
     * @param unparsedCommandExpressionList list of unparsed command expressions
     * @return list of parsed and validated command expressions
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<List<String>> getCommandExpressionList(List<String> unparsedCommandExpressionList)
            throws XmlCommandExpressionParseException {

        List<List<String>> commandExpressionList = new ArrayList<>();

        List<String> expression;

        int index;
        String expressionString;

        for (index = 0; index < unparsedCommandExpressionList.size(); index++ ) {

            expressionString = unparsedCommandExpressionList.get(index);

            try {
                expression = parseCommandExpression(expressionString);
            } catch (XmlCommandExpressionParseException e) {
                e.setIndex(index);
                throw e;
            }

            commandExpressionList.add(expression);
        }

        return commandExpressionList;
    }

    /**
     * Returns a single parsed and validated command expression from the raw input command expression
     * 'expressionString'.
     *
     * @param expressionString unparsed command expression
     * @return parsed and validated command expression
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseCommandExpression(String expressionString) throws XmlCommandExpressionParseException {

        if (expressionString == null) {
            // this condition should not occur
            throw new XmlCommandExpressionParseException("Command expression was null");
        }

        expressionString = expressionString.trim();

        if (StringUtils.isEmpty(expressionString)) {
            throw new XmlCommandExpressionParseException("Command expression was empty");
        }

        List<String> expressionList;

        NextExpressionElement nextExpressionElement = getNextExpressionElement(expressionString);

        String command = nextExpressionElement.next();
        String remainder = nextExpressionElement.remainder();

        return switch (command) {
            case COMMAND_RENAME_TAG          -> expressionList = parseRenameTag(remainder);
            case COMMAND_REMOVE_TAG          -> expressionList = parseRemoveTag(remainder);
            case COMMAND_REPLACE_TAG         -> expressionList = parseReplaceTag(remainder);
            case COMMAND_REPLACE_TAG_CONTENT -> expressionList = parseReplaceTagContent(remainder);
            case COMMAND_APPEND_CHILD        -> expressionList = parseAppendChild(remainder);
            case COMMAND_PREPEND_CHILD       -> expressionList = parsePrependChild(remainder);
            case COMMAND_FILTER_ON_TAG       -> expressionList = parseFilterOnTag(remainder);
            case COMMAND_ERROR_ON_TAG        -> expressionList = parseErrorOnTag(remainder);
            default -> throw new XmlCommandExpressionParseException("Command expression with command '" + command
                    + "' was not recognized", -1, command, -1);
        };

        //return expressionList;
    }

    /**
     * Returns a parsed and validated command expression for the command 'renameTag'.
     *
     * <p>The 'renameTag' command takes the following format:</p>
     * <ul>
     *     <li>renameTag &lt;search pattern&gt; &lt;new tag name&gt;</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'renameTag'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseRenameTag(String expressionRemainder) throws XmlCommandExpressionParseException {

        List<String> fullExpressionList = new ArrayList<>(List.of(COMMAND_RENAME_TAG));

        if (!StringUtils.isEmpty(expressionRemainder)) {
            fullExpressionList = Stream.concat(fullExpressionList.stream(),
                    getAllExpressionElements(expressionRemainder).stream()).toList();
        }

        performGenericExpressionValidation(fullExpressionList, fullExpressionList.getFirst(), 2);

        return fullExpressionList;
    }

    /**
     * Returns a parsed and validated command expression for the command 'removeTag'.
     *
     * <p>The 'removeTag' command takes the following format:</p>
     * <ul>
     *     <li>removeTag &lt;search pattern&gt;</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'removeTag'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseRemoveTag(String expressionRemainder) throws XmlCommandExpressionParseException {

        List<String> fullExpressionList = new ArrayList<>(List.of(COMMAND_REMOVE_TAG));

        if (!StringUtils.isEmpty(expressionRemainder)) {
            fullExpressionList = Stream.concat(fullExpressionList.stream(),
                    getAllExpressionElements(expressionRemainder).stream()).toList();
        }

        performGenericExpressionValidation(fullExpressionList, fullExpressionList.getFirst(), 1);

        return fullExpressionList;
    }

    /**
     * Returns a parsed and validated command expression for the command 'replaceTag'.
     *
     * <p>The 'replaceTag' command takes the following format:</p>
     * <ul>
     *     <li>replaceTag &lt;search pattern&gt; &lt;new content&gt;</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'replaceTag'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseReplaceTag(String expressionRemainder) throws XmlCommandExpressionParseException {

        NextExpressionElement nextExpressionElement = getNextExpressionElement(expressionRemainder);

        List<String> fullExpressionList = new LinkedList<>();
        fullExpressionList.add(COMMAND_REPLACE_TAG);

        // Expect exactly two arguments after the command, and parse two arguments if non-empty.  Do not tokenize the
        // entire expression because the second expected argument with the content with which to replace the tag may
        // contain separator characters.

        if (!StringUtils.isEmpty(nextExpressionElement.next())) {
            fullExpressionList.add(nextExpressionElement.next());
        }

        if (!StringUtils.isEmpty(nextExpressionElement.remainder())) {
            fullExpressionList.add(nextExpressionElement.remainder());
        }

        performGenericExpressionValidation(fullExpressionList, fullExpressionList.getFirst(), 2);

        return fullExpressionList;
    }

    /**
     * Returns a parsed and validated command expression for the command 'replaceTagContent'.
     *
     * <p>The 'replaceTagContent' command takes the following format:</p>
     * <ul>
     *     <li>replaceTagContent &lt;search pattern&gt; &lt;new content&gt;</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'replaceTagContent'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseReplaceTagContent(String expressionRemainder) throws XmlCommandExpressionParseException {

        NextExpressionElement nextExpressionElement = getNextExpressionElement(expressionRemainder);

        List<String> fullExpressionList = new LinkedList<>();
        fullExpressionList.add(COMMAND_REPLACE_TAG_CONTENT);

        // Expect exactly two arguments after the command, and parse two arguments if non-empty.  Do not tokenize the
        // entire expression because the second expected argument with the content with which to replace the tag may
        // contain separator characters.

        if (!StringUtils.isEmpty(nextExpressionElement.next())) {
            fullExpressionList.add(nextExpressionElement.next());
        }

        if (!StringUtils.isEmpty(nextExpressionElement.remainder())) {
            fullExpressionList.add(nextExpressionElement.remainder());
        }

        performGenericExpressionValidation(fullExpressionList, fullExpressionList.getFirst(), 2);

        return fullExpressionList;
    }

    /**
     * Returns a parsed and validated command expression for the command 'appendChild'.
     *
     * <p>The 'appendChild' command takes the following format:</p>
     * <ul>
     *     <li>appendChild &lt;search pattern&gt; &lt;new child&gt;</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'appendChild'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseAppendChild(String expressionRemainder) throws XmlCommandExpressionParseException {

        NextExpressionElement nextExpressionElement = getNextExpressionElement(expressionRemainder);

        List<String> fullExpressionList = new LinkedList<>();
        fullExpressionList.add(COMMAND_APPEND_CHILD);

        // Expect exactly two arguments after the command, and parse two arguments if non-empty.  Do not tokenize the
        // entire expression because the second expected argument with the content with which to replace the tag may
        // contain separator characters.

        if (!StringUtils.isEmpty(nextExpressionElement.next())) {
            fullExpressionList.add(nextExpressionElement.next());
        }

        if (!StringUtils.isEmpty(nextExpressionElement.remainder())) {
            fullExpressionList.add(nextExpressionElement.remainder());
        }

        performGenericExpressionValidation(fullExpressionList, fullExpressionList.getFirst(), 2);

        return fullExpressionList;
    }

    /**
     * Returns a parsed and validated command expression for the command 'prependChild'.
     *
     * <p>The 'prependChild' command takes the following format:</p>
     * <ul>
     *     <li>prependChild &lt;search pattern&gt; &lt;new child&gt;</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'prependChild'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parsePrependChild(String expressionRemainder) throws XmlCommandExpressionParseException {

        NextExpressionElement nextExpressionElement = getNextExpressionElement(expressionRemainder);

        List<String> fullExpressionList = new LinkedList<>();
        fullExpressionList.add(COMMAND_PREPEND_CHILD);

        // Expect exactly two arguments after the command, and parse two arguments if non-empty.  Do not tokenize the
        // entire expression because the second expected argument with the content with which to replace the tag may
        // contain separator characters.

        if (!StringUtils.isEmpty(nextExpressionElement.next())) {
            fullExpressionList.add(nextExpressionElement.next());
        }

        if (!StringUtils.isEmpty(nextExpressionElement.remainder())) {
            fullExpressionList.add(nextExpressionElement.remainder());
        }

        performGenericExpressionValidation(fullExpressionList, fullExpressionList.getFirst(), 2);

        return fullExpressionList;
    }

    /**
     * Returns a parsed and validated command expression for the command 'filterOnTag'.
     *
     * <p>The 'filterOnTag' command takes the following format:</p>
     * <ul>
     *     <li>filterOnTag &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag not &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag and|nand|or|xor|nor|xnor &lt;search xpath 1&gt; ... &lt;search xpath 1&gt;
     * "&lt;message&gt;"</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'filterOnTag'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseFilterOnTag(String expressionRemainder) throws XmlCommandExpressionParseException {
        List<String> fullExpressionList = new ArrayList<>(List.of(COMMAND_FILTER_ON_TAG));
        return parseFilterErrorOnTag(fullExpressionList, expressionRemainder);
    }

    /**
     * Returns a parsed and validated command expression for the command 'errorOnTag'.
     *
     * <p>The 'errorOnTag' command takes the following format:</p>
     * <ul>
     *     <li>errorOnTag &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>errorOnTag not &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>errorOnTag and|nand|or|xor|nor|xnor &lt;search xpath 1&gt; ... &lt;search xpath 1&gt;
     * "&lt;message&gt;"</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'errorOnTag'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseErrorOnTag(String expressionRemainder) throws XmlCommandExpressionParseException {
        List<String> fullExpressionList = new ArrayList<>(List.of(COMMAND_ERROR_ON_TAG));
        return parseFilterErrorOnTag(fullExpressionList, expressionRemainder);
    }

    /**
     * Returns a parsed and validated command expression for the command 'filterOnTag' or command 'errorOnTag'.
     *
     * <p>The commands may take the following format:</p>
     * <ul>
     *     <li>filterOnTag|errorOnTag &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag|errorOnTag not &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag|errorOnTag and|nand|or|xor|nor|xnor &lt;search xpath 1&gt; ... &lt;search xpath 1&gt;
     * "&lt;message&gt;"</li>
     * </ul>
     *
     * @param expressionRemainder the remainder of the expression after the command
     * @return a parsed and validated command expression for the command 'filterOnTag' or 'errorOnTag'
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private List<String> parseFilterErrorOnTag(List<String> fullExpressionList, String expressionRemainder)
            throws XmlCommandExpressionParseException {

        if (StringUtils.isEmpty(expressionRemainder)) {
            throw new XmlCommandExpressionParseException("Command expression for command '"
                    + fullExpressionList.getFirst() + "' expected 2 arguments but had no arguments");
        }

        int msgStartIndex = expressionRemainder.indexOf("\"");
        if (msgStartIndex == -1) {
            String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' expected last" +
                    " argument to be a message in quotes but did not find a quote";
            throw new XmlCommandExpressionParseException(errMsg);
        }

        int msgEndIndex = expressionRemainder.indexOf("\"", msgStartIndex + 1);
        if (msgEndIndex == -1) {
            String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' expected last" +
                    " argument to be a message in quotes but did not find second quote";
            throw new XmlCommandExpressionParseException(errMsg);
        }

        int msgLength = ((msgEndIndex - 1) - (msgStartIndex + 1)) + 1; // account for quotes
        if (msgLength < SCREEN_MESSAGE_LENGTH_MINIMUM) {
            String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' expected message" +
                    " to be at least " + SCREEN_MESSAGE_LENGTH_MINIMUM + " characters but was only " + msgLength +
                    " characters";
            throw new XmlCommandExpressionParseException(errMsg);
        }

        if (msgEndIndex != expressionRemainder.length() - 1) {
            String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' expected last" +
                    " argument to be a message in quotes but found at least one trailing character";
            throw new XmlCommandExpressionParseException(errMsg);
        }

        // remove the quotes from the message.  reminder that the end index is exclusive.
        String message = expressionRemainder.substring(msgStartIndex + 1, msgEndIndex);

        // get substring from start to but not including the first quote.  reminder that the end index is exclusive.
        String expressionWithoutMessage = expressionRemainder.substring(0, msgStartIndex);

        if (!StringUtils.isEmpty(expressionRemainder)) {
            fullExpressionList.addAll(getAllExpressionElements(expressionWithoutMessage));
        }

        int numArgs = fullExpressionList.size() - 1;  // does not include the message argument

        if (numArgs == 0) {
            String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' expected 2 or" +
                    " more arguments but had 1 argument";
            throw new XmlCommandExpressionParseException(errMsg);
        } else if (numArgs > 1) {

            String operator = fullExpressionList.get(1).toLowerCase(Locale.ROOT);
            fullExpressionList.set(1, operator);

            if (operator.equals(OPERATOR_NOT) ) {
                if (numArgs != 2) {
                    String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' using" +
                            " operator 'not' expected 2 argument but had " + (numArgs + 1) + " arguments";
                    throw new XmlCommandExpressionParseException(errMsg);
                }
            } else if (!(operator.equals(OPERATOR_AND)
                    || operator.equals(OPERATOR_NAND)
                    || operator.equals(OPERATOR_OR)
                    || operator.equals(OPERATOR_XOR)
                    || operator.equals(OPERATOR_NOR)
                    || operator.equals(OPERATOR_XNOR))) {
                String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' specified " +
                        (numArgs + 1) + " arguments but did not define an operator";
                throw new XmlCommandExpressionParseException(errMsg);
            } else if (numArgs == 2) {
                String errMsg = "Command expression for command '" + fullExpressionList.getFirst() + "' specified " +
                        (numArgs + 1) + " arguments but requires at least 4 arguments";
                throw new XmlCommandExpressionParseException(errMsg);
            }

        }

        fullExpressionList.add(message);

        return fullExpressionList;
    }

    /**
     * Performs generic validation the command expression 'commandExpression'.  Throws an
     * XmlCommandExpressionParseException if the command expression is invalid else takes no action.
     *
     * @param commandExpression command expression to evaluate
     * @param expectedCommand the expected command
     * @param expectedNumArgs the expected number of arguments
     * @throws XmlCommandExpressionParseException if a parsing exception occurs
     */
    private void performGenericExpressionValidation(List<String> commandExpression, String expectedCommand,
                                          int expectedNumArgs) throws XmlCommandExpressionParseException {
        if (commandExpression.size() != expectedNumArgs + 1) {
            throw new XmlCommandExpressionParseException("Command expression for command '" + expectedCommand
                    + "' expected " + expectedNumArgs + " arguments but had " + (commandExpression.size()-1)
                    + " arguments");
        }
    }

    /**
     * Returns a record containing the result of getting the (single) next token from the command expression
     * 'expression'.
     *
     * @param expression the command expression to evaluate
     * @return a record containing the result of getting the (single) next token from the command expression
     */
    private NextExpressionElement getNextExpressionElement(String expression) {

        String next = null;
        String remainder = null;

        if (!StringUtils.isEmpty(expression)) {
            String[] expressionArray = expression.split(ARGUMENT_SEPARATOR_REGEX_STRING, 2);

            next = expressionArray[0];

            if (expressionArray.length == 2) {
                remainder = expressionArray[1];
            }
        }

        return new NextExpressionElement(next, remainder);
    }

    /**
     * Returns a record containing the result of getting 'num' number next token from the command expression
     * 'expression'.
     *
     * @param expression the command expression to evaluate
     * @return a record containing the result of getting the next token(s) from the command expression
     */
    private NextExpressionElements getNextExpressionElements(String expression, int num) {

        List<String> next = new LinkedList<>();
        String remainder = null;

        if (!StringUtils.isEmpty(expression)) {
            String[] expressionArray = expression.split(ARGUMENT_SEPARATOR_REGEX_STRING, num + 1);

            int length = expressionArray.length;

            if (length == 1) {
                next.add(expressionArray[0]);
            } else {
                next.addAll(Arrays.asList(expressionArray).subList(0, expressionArray.length - 1));
                remainder = expressionArray[length - 1];
            }
        }

        return new NextExpressionElements(next, remainder);
    }

    /**
     * Returns a record containing the result of getting all the next tokens from the command expression 'expression'.
     *
     * @param expression the command expression to evaluate
     * @return a record containing the result of getting all the next tokens from the command expression
     */
    private List<String> getAllExpressionElements(String expression) {
        if (!StringUtils.isEmpty(expression)) {
            return Arrays.asList(expression.split(ARGUMENT_SEPARATOR_REGEX_STRING));
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Creates and returns a list of XML editor operations from the command expressions in the input
     * 'commandExpressionList'.
     *
     * <p>The command expressions in 'commandExpressionList' must be parsed and validated.</p>
     *
     * @param commandExpressionList list of command expressions
     * @return list of XML editor operations from the input command expressions
     * @throws XmlModifyOperationConfigurationException if an exception occurs while building a transform operation,
     * typically resulting from an XSLT error
     * @throws XmlScreenOperationConfigurationException if an exception occurs while building a result operation,
     * typically resulting from an XPath error
     */
    private List<XmlEditorOperation> getOperationList(List<List<String>> commandExpressionList)
            throws XmlModifyOperationConfigurationException, XmlScreenOperationConfigurationException {

        List<XmlEditorOperation> operationList = new ArrayList<>();

        XmlEditorOperation operation;

        List<String> expression;

        for (int index = 0; index < commandExpressionList.size(); index++) {

            operation = null;

            expression = commandExpressionList.get(index);

            String command = expression.getFirst();

            try {
                switch (command) {
                    case COMMAND_RENAME_TAG ->
                            operation = getModifyOperationRenameTag(commandExpressionList.get(index));
                    case COMMAND_REMOVE_TAG ->
                            operation = getModifyOperationRemoveTag(commandExpressionList.get(index));
                    case COMMAND_REPLACE_TAG ->
                            operation = getModifyOperationReplaceTag(commandExpressionList.get(index));
                    case COMMAND_REPLACE_TAG_CONTENT ->
                            operation = getModifyOperationReplaceTagContent(commandExpressionList.get(index));
                    case COMMAND_APPEND_CHILD ->
                            operation = getModifyOperationAppendChild(commandExpressionList.get(index));
                    case COMMAND_PREPEND_CHILD ->
                            operation = getModifyOperationPrependChild(commandExpressionList.get(index));
                    case COMMAND_FILTER_ON_TAG ->
                            operation = getScreenOperationFilterOnTag(commandExpressionList.get(index));
                    case COMMAND_ERROR_ON_TAG ->
                            operation = getScreenOperationErrorOnTag(commandExpressionList.get(index));
                    // expressions are validated, so there will not be any unrecognized commands
                }
            } catch (XmlModifyOperationConfigurationException e) {
                e.setIndex(index);
                throw e;
            } catch (XmlScreenOperationConfigurationException e) {
                e.setIndex(index);
                throw e;
            }

            operationList.add(operation);
        }

        return operationList;
    }

    /**
     * Builds and returns an XmlEditorModifyOperation that implements the input command expression for 'renameTag'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'renameTag'.</p>
     *
     * <p>The 'renameTag' command takes the following format, where spaces separate the elements of the input list:</p>
     * <ul>
     *     <li>renameTag &lt;search pattern&gt; &lt;new tag name&gt;</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'renameTag'
     * @return an XmlEditorModifyOperation that implements the input command expression
     * @throws XmlModifyOperationConfigurationException if an exception occurs while building a transform operation,
     * typically resulting from an XSLT error
     */
    private XmlEditorModifyOperation getModifyOperationRenameTag(List<String> expression)
            throws XmlModifyOperationConfigurationException {

        String pattern = expression.get(1);
        String newName = expression.get(2);
        String xslt = MODIFY_OPERATION_RENAME_TAG_XSLT.formatted(pattern, newName, newName);

        Transformer transformer;

        try {
            transformer = getTransformer(xslt);
        } catch (TransformerConfigurationException e) {
            throw new XmlModifyOperationConfigurationException(ERROR_MSG_CREATING_XSLT);
        }

        return new XmlEditorModifyOperation(transformer);
    }

    /**
     * Builds and returns an XmlEditorModifyOperation that implements the input command expression for 'removeTag'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'removeTag'.</p>
     *
     * <p>The 'removeTag' command takes the following format, where spaces separate the elements of the input list:</p>
     * <ul>
     *     <li>removeTag &lt;search pattern&gt;</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'removeTag'
     * @return an XmlEditorModifyOperation that implements the input command expression
     * @throws XmlModifyOperationConfigurationException if an exception occurs while building a transform operation,
     * typically resulting from an XSLT error
     */
    private XmlEditorModifyOperation getModifyOperationRemoveTag(List<String> expression)
            throws XmlModifyOperationConfigurationException {

        String pattern = expression.get(1);
        String xslt = MODIFY_OPERATION_REMOVE_TAG_XSLT.formatted(pattern);

        Transformer transformer;

        try {
            transformer = getTransformer(xslt);
        } catch (TransformerConfigurationException e) {
            throw new XmlModifyOperationConfigurationException(ERROR_MSG_CREATING_XSLT);
        }

        return new XmlEditorModifyOperation(transformer);
    }

    /**
     * Builds and returns an XmlEditorModifyOperation that implements the input command expression for 'replaceTag'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'replaceTag'.</p>
     *
     * <p>The 'replaceTag' command takes the following format, where spaces separate the elements of the input list:</p>
     * <ul>
     *     <li>replaceTag &lt;search pattern&gt; &lt;new content&gt;</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'replaceTag'
     * @return an XmlEditorModifyOperation that implements the input command expression
     * @throws XmlModifyOperationConfigurationException if an exception occurs while building a transform operation,
     * typically resulting from an XSLT error
     */
    private XmlEditorModifyOperation getModifyOperationReplaceTag(List<String> expression)
            throws XmlModifyOperationConfigurationException {

        String pattern = expression.get(1);
        String newContent = expression.get(2);
        String xslt = MODIFY_OPERATION_REPLACE_TAG_XSLT.formatted(pattern, newContent);

        Transformer transformer;

        try {
            transformer = getTransformer(xslt);
        } catch (TransformerConfigurationException e) {
            throw new XmlModifyOperationConfigurationException(ERROR_MSG_CREATING_XSLT);
        }

        return new XmlEditorModifyOperation(transformer);
    }

    /**
     * Builds and returns an XmlEditorModifyOperation that implements the input command expression for
     * 'replaceTagContent'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'replaceTagContent'.</p>
     *
     * <p>The 'replaceTagContent' command takes the following format, where spaces separate the elements of the input
     * list:</p>
     * <ul>
     *     <li>replaceTagContent &lt;search pattern&gt; &lt;new content&gt;</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'replaceTagContent'
     * @return an XmlEditorModifyOperation that implements the input command expression
     * @throws XmlModifyOperationConfigurationException if an exception occurs while building a transform operation,
     * typically resulting from an XSLT error
     */
    private XmlEditorModifyOperation getModifyOperationReplaceTagContent(List<String> expression)
            throws XmlModifyOperationConfigurationException {

        String pattern = expression.get(1);
        String newContent = expression.get(2);
        String xslt = MODIFY_OPERATION_REPLACE_TAG_CONTENT_XSLT.formatted(pattern, newContent);

        Transformer transformer;

        try {
            transformer = getTransformer(xslt);
        } catch (TransformerConfigurationException e) {
            throw new XmlModifyOperationConfigurationException(ERROR_MSG_CREATING_XSLT);
        }

        return new XmlEditorModifyOperation(transformer);
    }

    /**
     * Builds and returns an XmlEditorModifyOperation that implements the input command expression for 'appendChild'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'appendChild'.</p>
     *
     * <p>The 'appendChild' command takes the following format, where spaces separate the elements of the input list:</p>
     * <ul>
     *     <li>appendChild &lt;search pattern&gt; &lt;new child&gt;</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'appendChild'
     * @return an XmlEditorModifyOperation that implements the input command expression
     * @throws XmlModifyOperationConfigurationException if an exception occurs while building a transform operation,
     * typically resulting from an XSLT error
     */
    private XmlEditorModifyOperation getModifyOperationAppendChild(List<String> expression)
            throws XmlModifyOperationConfigurationException {

        String pattern = expression.get(1);
        String newContent = expression.get(2);
        String xslt = MODIFY_OPERATION_APPEND_CHILD_XSLT.formatted(pattern, newContent);

        Transformer transformer;

        try {
            transformer = getTransformer(xslt);
        } catch (TransformerConfigurationException e) {
            throw new XmlModifyOperationConfigurationException(ERROR_MSG_CREATING_XSLT);
        }

        return new XmlEditorModifyOperation(transformer);
    }

    /**
     * Builds and returns an XmlEditorModifyOperation that implements the input command expression for
     * 'prependChild'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'prependChild'.</p>
     *
     * <p>The 'prependChild' command takes the following format, where spaces separate the elements of the input list:</p>
     * <ul>
     *     <li>prependChild &lt;search pattern&gt; &lt;new child&gt;</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'prependChild'
     * @return an XmlEditorModifyOperation that implements the input command expression
     * @throws XmlModifyOperationConfigurationException if an exception occurs while building a transform operation,
     * typically resulting from an XSLT error
     */
    private XmlEditorModifyOperation getModifyOperationPrependChild(List<String> expression)
            throws XmlModifyOperationConfigurationException {

        String pattern = expression.get(1);
        String newContent = expression.get(2);
        String xslt = MODIFY_OPERATION_PREPEND_CHILD_XSLT.formatted(pattern, newContent);

        Transformer transformer;

        try {
            transformer = getTransformer(xslt);
        } catch (TransformerConfigurationException e) {
            throw new XmlModifyOperationConfigurationException(ERROR_MSG_CREATING_XSLT);
        }

        return new XmlEditorModifyOperation(transformer);
    }

    /**
     * Builds and returns an XmlEditorScreenOperation that implements the input command expression for 'filterOnTag'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'filterOnTag'.</p>
     *
     * <p>The 'filterOnTag' command takes the following format, where spaces separate the elements of the input list:</p>
     * <ul>
     *     <li>filterOnTag &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag not &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag and|nand|or|xor|nor|xnor &lt;search xpath 1&gt; ... &lt;search xpath 1&gt;
     * "&lt;message&gt;"</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'filterOnTag'
     * @return an XmlEditorScreenOperation that implements the input command expression
     * @throws XmlScreenOperationConfigurationException if an exception occurs while building a result operation,
     * typically resulting from an XPath error
     */
    private XmlEditorScreenOperation getScreenOperationFilterOnTag(List<String> expression)
            throws XmlScreenOperationConfigurationException {
        return getScreenOperationFilterErrorOnTag(expression);
    }

    /**
     * Builds and returns an XmlEditorScreenOperation that implements the input command expression for 'errorOnTag'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for 'errorOnTag'.</p>
     *
     * <p>The 'errorOnTag' command takes the following format, where spaces separate the elements of the input list:</p>
     * <ul>
     *     <li>errorOnTag &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>errorOnTag not &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>errorOnTag and|nand|or|xor|nor|xnor &lt;search xpath 1&gt; ... &lt;search xpath 1&gt;
     * "&lt;message&gt;"</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'errorOnTag'
     * @return an XmlEditorScreenOperation that implements the input command expression
     * @throws XmlScreenOperationConfigurationException if an exception occurs while building a result operation,
     * typically resulting from an XPath error
     */
    private XmlEditorScreenOperation getScreenOperationErrorOnTag(List<String> expression)
            throws XmlScreenOperationConfigurationException {
        return getScreenOperationFilterErrorOnTag(expression);
    }

    /**
     * Builds and returns an XmlEditorScreenOperation that implements the input command expression for 'filterOnTag' or
     * 'errorOnTag'.
     *
     * <p>The input command expression 'expression' must be a parsed and validated command for either 'filterOnTag' or
     * 'errorOnTag'.</p>
     *
     * <p>The 'filterOnTag'/'errorOnTag' commands takes the following format, where spaces separate the elements of the
     * input list:</p>
     * <ul>
     *     <li>filterOnTag|errorOnTag &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag|errorOnTag not &lt;search xpath&gt; "&lt;message&gt;"</li>
     *     <li>filterOnTag|errorOnTag and|nand|or|xor|nor|xnor &lt;search xpath 1&gt; ... &lt;search xpath 1&gt;
     * "&lt;message&gt;"</li>
     * </ul>
     *
     * @param expression the parsed and validated command expression for 'filterOnTag' or 'errorOnTag'
     * @return an XmlEditorScreenOperation that implements the input command expression
     * @throws XmlScreenOperationConfigurationException if an exception occurs while building a result operation,
     * typically resulting from an XPath error
     */
    private XmlEditorScreenOperation getScreenOperationFilterErrorOnTag(List<String> expression)
            throws XmlScreenOperationConfigurationException {

        String commandString = expression.getFirst();
        XmlEditorFilterErrorOnTagScreenOperation.Command command;

        if (commandString.equals("filterOnTag")) {
            command = XmlEditorFilterErrorOnTagScreenOperation.Command.FILTER;
        } else {
            // must be "errorOnTag"
            command = XmlEditorFilterErrorOnTagScreenOperation.Command.ERROR;
        }


        List<String> tagQueryList = new ArrayList<>();
        int numArgs = expression.size() - 1;  // the command is the first element, and the rest are args
        XmlEditorFilterErrorOnTagScreenOperation.Operator operator;

        String message = expression.getLast();

        if (numArgs == 2) {
            operator = XmlEditorFilterErrorOnTagScreenOperation.Operator.NONE;
            tagQueryList.add(expression.get(1));
        } else if (numArgs == 3) {
            operator = XmlEditorFilterErrorOnTagScreenOperation.Operator.NOT;
            tagQueryList.add(expression.get(2));
        } else {
            String operatorString = expression.get(1);

            operator = switch (operatorString) {
                case OPERATOR_AND  -> XmlEditorFilterErrorOnTagScreenOperation.Operator.AND;
                case OPERATOR_NAND -> XmlEditorFilterErrorOnTagScreenOperation.Operator.NAND;
                case OPERATOR_OR   -> XmlEditorFilterErrorOnTagScreenOperation.Operator.OR;
                case OPERATOR_XOR  -> XmlEditorFilterErrorOnTagScreenOperation.Operator.XOR;
                case OPERATOR_NOR  -> XmlEditorFilterErrorOnTagScreenOperation.Operator.NOR;
                case OPERATOR_XNOR -> XmlEditorFilterErrorOnTagScreenOperation.Operator.XNOR;
                default            -> XmlEditorFilterErrorOnTagScreenOperation.Operator.NOT_INITIALIZED;
            };

            tagQueryList = expression.subList(2, expression.size() - 1);
        }


        List<XPathExpression> xpathQueryList = new ArrayList<>();
        XPathExpression xpathExpression;

        int index;

        for (index = 0; index < tagQueryList.size(); index++) {

            try {
                xpathExpression = getXPathExpression(tagQueryList.get(index));
            } catch (XPathExpressionException e) {
                XmlScreenOperationConfigurationException xe =
                        new XmlScreenOperationConfigurationException(ERROR_MSG_CREATING_XPATH);
                xe.setArgIndex(index);
                throw xe;
            }

            xpathQueryList.add(xpathExpression);

        }

        return new XmlEditorFilterErrorOnTagScreenOperation(command, operator, xpathQueryList, message);

    }

    /**
     * Creates and returns a javax.xml.xpath.XPathExpression for the String 'xpathExpression', which is expected to be
     * a valid XPath expression.
     *
     * @param xpathExpression String for which to create an XPathExpression, expected to be a valid XPath expression
     * @return an XPathExpression as defined by the input 'xpathExpression'
     * @throws XPathExpressionException if an exception occurred when building the XPathExpression
     */
    private XPathExpression getXPathExpression(String xpathExpression) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        return xpath.compile(xpathExpression);
    }

    /**
     * Creates and returns a javax.xml.transform.Transformer for the String 'xslt', which is expected to be valid XSLT.
     *
     * @param xslt String for which to create a Transformer, expected to be valid XSLT
     * @return a Transformer as defined by the input 'xslt'
     * @throws TransformerConfigurationException if an exception occurred when building the Transformer
     */
    private Transformer getTransformer(String xslt) throws TransformerConfigurationException {

        Transformer transformer;

        try {
            transformer = transformerCache.computeIfAbsent(xslt, key -> {
                TransformerFactory factory = TransformerFactory.newInstance();
                Source xsltSource = new StreamSource(new StringReader(xslt));
                try {
                    return factory.newTransformer(xsltSource);
                } catch (TransformerConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new TransformerConfigurationException(e.getMessage());
        }

        return transformer;
    }

}

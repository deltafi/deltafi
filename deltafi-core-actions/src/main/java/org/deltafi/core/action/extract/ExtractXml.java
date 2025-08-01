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
package org.deltafi.core.action.extract;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

@Component
public class ExtractXml extends TransformAction<ExtractXmlParameters> {
    private static final int MAX_CACHE_SIZE = 1000;
    private static final XPath X_PATH = XPathFactory.newInstance().newXPath();

    // use an LRU cache to avoid recomputing the xpath expression each time but control memory usage
    private final Map<String, XPathExpression> X_PATH_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, XPathExpression> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    public ExtractXml() {
        super(ActionOptions.builder()
                .description("Extracts values from XML content and writes them to metadata or annotations.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(ExtractJsonParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .passthrough(true)
                        .metadataSummary("""
                                If extractTarget is METADATA, XPath expressions from xpathToKeysMap keys are used
                                to extract values from the input content and write them to metadata keys which are the
                                corresponding xpathToKeysMap values.
                                
                                Values extracted from multiple contents are handled according to handleMultipleKeys.""")
                        .annotationsSummary("""
                                If extractTarget is ANNOTATIONS, XPath expressions from xpathToKeysMap keys are
                                used to extract values from the input content and write them to annotation keys which
                                are the corresponding xpathToKeysMap values.
                                
                                Values extracted from multiple contents are handled according to handleMultipleKeys.""")
                        .build())
                .errors("On failure to parse any content from XML",
                        "On failure to evaluate any XPath expression in xpathToKeysMap keys",
                        "On errorOnKeyNotFound set to true and no values can be extracted from input content for any xpathToKeysMap key")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ExtractXmlParameters params,
            @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        Map<String, List<String>> valuesMap = new HashMap<>();
        for (int i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.content(i);

            if (!params.contentSelected(i, content)) {
                result.addContent(content);
                continue;
            }

            if (params.isRetainExistingContent()) {
                result.addContent(content);
            }


            Document document;
            try {
                document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new StringReader(content.loadString())));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                return new ErrorResult(context, "Unable to read XML content", "Failed to read " + content.getName(), e);
            }

            for (Map.Entry<String, String> entry : params.getXpathToKeysMap().entrySet()) {
                NodeList nodes;
                try {
                    XPathExpression expression = getXPathExpression(entry.getKey());
                    nodes = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
                } catch (Exception e) {
                    return new ErrorResult(context, "Unable to evaluate XPATH expression", "Failed to evaluate " + entry.getKey(), e);
                }

                List<String> values = new ArrayList<>();
                for (int j = 0; j < nodes.getLength(); j++) {
                    values.add(nodes.item(j).getTextContent());
                }
                valuesMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(values);
            }
        }

        for (Map.Entry<String, String> entry : params.getXpathToKeysMap().entrySet()) {
            String xpathExp = entry.getKey();
            String mappedKey = entry.getValue();
            List<String> values = valuesMap.getOrDefault(xpathExp, Collections.emptyList());

            if (values.isEmpty()) {
                if (params.isErrorOnKeyNotFound()) {
                    return new ErrorResult(context, "Key not found: " + xpathExp);
                }
                continue;
            }

            String value = switch (params.getHandleMultipleKeys()) {
                case FIRST -> values.getFirst();
                case LAST -> values.getLast();
                case DISTINCT -> String.join(params.getAllKeysDelimiter(), values.stream().distinct().toList());
                default -> String.join(params.getAllKeysDelimiter(), values);
            };

            if (params.getExtractTarget() == ExtractTarget.METADATA) {
                result.addMetadata(mappedKey, value);
            } else {
                result.addAnnotation(mappedKey, value);
            }
        }

        return result;
    }

    private XPathExpression getXPathExpression(String xpath) {
        return X_PATH_CACHE.computeIfAbsent(xpath, key -> {
            try {
                return X_PATH.compile(xpath);
            } catch (XPathExpressionException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

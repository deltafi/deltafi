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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.ExtractXmlAnnotationsParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.xml.xpath.*;
import java.util.*;

@Component
public class ExtractXmlToAnnotation extends TransformAction<ExtractXmlAnnotationsParameters> {
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

    public ExtractXmlToAnnotation() {
        super("Extract XML keys based on XPath from domain(s), and write them as annotations");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ExtractXmlAnnotationsParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        // TODO: reimplement this
        /*
        Map<String, List<String>> valuesMap = new HashMap<>();
        domains = domains.stream()
                .filter(d -> params.getMediaTypes().stream()
                        .anyMatch(allowedType -> matchesPattern(d.getMediaType(), allowedType)))
                .toList();

        for (Domain domain : domains) {
            Document document;
            try {
                document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new StringReader(domain.getValue())));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                return new ErrorResult(context, "Unable to read XML domain", "Failed to read " + domain.getName(), e);
            }

            for (Map.Entry<String, String> entry : params.getXpathToMetadataKeysMap().entrySet()) {
                NodeList nodes;
                try {
                    XPathExpression expression = getXPathExpression(entry.getKey());
                    nodes = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
                } catch (Exception e) {
                    return new ErrorResult(context, "Unable to evaluate XPATH expression", "Failed to evaluate " + entry.getKey(), e);
                }

                List<String> values = new ArrayList<>();
                for (int i = 0; i < nodes.getLength(); i++) {
                    values.add(nodes.item(i).getTextContent());
                }
                valuesMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(values);
            }
        }

        for (Map.Entry<String, String> entry : params.getXpathToMetadataKeysMap().entrySet()) {
            String xpathExp = entry.getKey();
            String metadataKey = entry.getValue();
            List<String> values = valuesMap.getOrDefault(xpathExp, Collections.emptyList());

            if (values.isEmpty()) {
                if (params.isErrorOnKeyNotFound()) {
                    return new ErrorResult(context, "Key not found: " + xpathExp);
                } else {
                    continue;
                }
            }

            String value = switch (params.getHandleMultipleKeys()) {
                case FIRST -> values.get(0);
                case LAST -> values.get(values.size() - 1);
                case DISTINCT -> String.join(params.getAllKeysDelimiter(),
                        values.stream().distinct().toList());
                default -> String.join(params.getAllKeysDelimiter(), values);
            };

            result.addAnnotation(metadataKey, value);
        }*/

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

    private boolean matchesPattern(String value, String pattern) {
        return value.matches(pattern.replace("*", ".*"));
    }
}

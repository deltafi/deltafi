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

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.XsltParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class XsltTransformAction extends TransformAction<XsltParameters> {
    private static final int MAX_CACHE_SIZE = 1000;

    private final Map<String, Transformer> transformerCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Transformer> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    public XsltTransformAction() {
        super("Apply XML transformation using XSLT");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull XsltParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        Transformer transformer;

        try {
            transformer = getTransformer(params.getXslt());
        } catch (Exception e) {
            return new ErrorResult(context, "Error parsing XSLT", e);
        }

        for (int i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.getContent().get(i);

            if (shouldTransform(content, i, params)) {
                String xml = content.loadString();
                String newXml;
                try {
                    Source xmlSource = new StreamSource(new StringReader(xml));
                    StringWriter writer = new StringWriter();
                    StreamResult transformedXml = new StreamResult(writer);
                    transformer.transform(xmlSource, transformedXml);
                    newXml = writer.toString();
                } catch (Exception e) {
                    return new ErrorResult(context, "Error transforming content at index " + i, e);
                }

                result.addContent(ActionContent.saveContent(context, newXml, content.getName(), MediaType.APPLICATION_XML));
            } else {
                result.addContent(content);
            }
        }

        return result;
    }

    private Transformer getTransformer(String xslt) {
        return transformerCache.computeIfAbsent(xslt, key -> {
            TransformerFactory factory = TransformerFactory.newInstance();
            Source xsltSource = new StreamSource(new StringReader(xslt));
            try {
                return factory.newTransformer(xsltSource);
            } catch (TransformerConfigurationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean shouldTransform(ActionContent content, int index, XsltParameters params) {
        return (params.getContentIndexes() == null || params.getContentIndexes().isEmpty() || params.getContentIndexes().contains(index)) &&
                (params.getFilePatterns() == null || params.getFilePatterns().isEmpty() || params.getFilePatterns().stream()
                        .anyMatch(pattern -> matchesPattern(content.getName(), pattern))) &&
                (params.getMediaTypes() == null || params.getMediaTypes().isEmpty() || params.getMediaTypes().stream()
                        .anyMatch(allowedType -> matchesPattern(content.getMediaType(), allowedType)));
    }

    private boolean matchesPattern(final String value, final String pattern) {
        String regexPattern = pattern.replace("*", ".*");
        return value.matches(regexPattern);
    }
}

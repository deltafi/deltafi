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
package org.deltafi.core.action.xslt;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.action.ContentSelectingTransformAction;
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
public class XsltTransform extends ContentSelectingTransformAction<XsltParameters> {
    private static final int MAX_CACHE_SIZE = 1000;

    private final Map<String, Transformer> transformerCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Transformer> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    public XsltTransform() {
        super("Transforms XML using XSLT.");
    }


    @Override
    public ActionContent transform(@NotNull ActionContext context, @NotNull XsltParameters params,
            ActionContent content) throws Exception {
        StringWriter writer = new StringWriter();

        Transformer transformer = getTransformer(params.getXslt());
        transformer.transform(new StreamSource(new StringReader(content.loadString())), new StreamResult(writer));

        return ActionContent.saveContent(context, writer.toString(), content.getName(), MediaType.APPLICATION_XML);
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
}

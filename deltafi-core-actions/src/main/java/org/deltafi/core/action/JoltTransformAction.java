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

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.JoltParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JoltTransformAction extends TransformAction<JoltParameters> {
    private static final int MAX_CACHE_SIZE = 1000;

    // use an LRU cache to avoid recomputing the chainr each time but control memory usage
    private final Map<String, Chainr> chainrCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Chainr> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    public JoltTransformAction() {
        super("Apply Jolt transformation to JSON content");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull JoltParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        Chainr chainr;

        try {
            chainr = getChainr(params.getJoltSpec());
        } catch (Exception e) {
            return new ErrorResult(context, "Error parsing Jolt specification", "Could not parse " + params.getJoltSpec() + ": " + e.getMessage());
        }

        for (int i=0; i < input.getContent().size(); i++) {
            ActionContent content = input.getContent().get(i);

            if (shouldTransform(content, i, params)) {
                String json = content.loadString();
                String newJson;
                try {
                    Object inputJson = JsonUtils.jsonToObject(json);
                    Object transformedJson = chainr.transform(inputJson);
                    newJson = JsonUtils.toJsonString(transformedJson);
                } catch (Exception e) {
                    return new ErrorResult(context, "Error transforming content at index " + i, e);
                }

                result.addContent(ActionContent.saveContent(context, newJson, content.getName(), MediaType.APPLICATION_JSON));
            } else {
                result.addContent(content);
            }
        }

        return result;
    }

    private Chainr getChainr(String spec) {
        return chainrCache.computeIfAbsent(spec, key -> Chainr.fromSpec(JsonUtils.jsonToList(spec)));
    }

    private boolean shouldTransform(ActionContent content, int index, JoltParameters params) {
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

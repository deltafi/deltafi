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
package org.deltafi.core.action.jolt;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.core.action.ContentSelectingTransformAction;
import org.deltafi.core.action.convert.ConvertParameters;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.*;

@Component
public class JoltTransform extends ContentSelectingTransformAction<JoltParameters> {
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

    public JoltTransform() {
        super(ActionOptions.builder()
                .description("Transforms JSON content using Jolt.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(ConvertParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Transforms each content using a Jolt transformation with the provided Jolt
                                specification. The transformed content will have the same name as the input content and
                                the media type will be application/json.
                                
                                """ + ConvertParameters.CONTENT_RETENTION_DESCRIPTION)
                        .build())
                .errors("On invalid Jolt specification provided", "On failure to transform any content")
                .build());
    }

    @Override
    protected ActionContent transform(ActionContext context, JoltParameters params, ActionContent content) {
        Chainr chainr = chainrCache.computeIfAbsent(params.getJoltSpec(), key -> Chainr.fromSpec(JsonUtils.jsonToList(key)));

        Object transformedJson = chainr.transform(JsonUtils.jsonToObject(content.loadString()));
        String newJson = JsonUtils.toJsonString(transformedJson);

        return ActionContent.saveContent(context, newJson, content.getName(), MediaType.APPLICATION_JSON);
    }
}

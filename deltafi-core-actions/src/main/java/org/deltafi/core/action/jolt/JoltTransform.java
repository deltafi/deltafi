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
package org.deltafi.core.action.jolt;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.action.ContentSelectingTransformAction;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
        super("Transforms JSON content using Jolt.");
    }

    @Override
    protected ActionContent transform(ActionContext context, JoltParameters params, ActionContent content) {
        Chainr chainr = chainrCache.computeIfAbsent(params.getJoltSpec(), key -> Chainr.fromSpec(JsonUtils.jsonToList(key)));

        Object transformedJson = chainr.transform(JsonUtils.jsonToObject(content.loadString()));
        String newJson = JsonUtils.toJsonString(transformedJson);

        return ActionContent.saveContent(context, newJson, content.getName(), MediaType.APPLICATION_JSON);
    }
}

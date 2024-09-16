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
package org.deltafi.core.action.extract;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExtractJson extends TransformAction<ExtractJsonParameters> {
    private static final Configuration CONFIGURATION = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST)
            .build();

    public ExtractJson() {
        super("Extract values from JSON content and write them to metadata or annotations.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ExtractJsonParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        result.addContent(input.content());

        Map<String, List<String>> extractedValuesMap = new HashMap<>();
        for (int i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.content(i);

            if (!params.contentMatches(content.getName(), content.getMediaType(), i)) {
                continue;
            }

            ReadContext ctx = JsonPath.using(CONFIGURATION).parse(content.loadString());
            for (Map.Entry<String, String> entry : params.getJsonPathToKeysMap().entrySet()) {
                String jsonPath = entry.getKey();
                Object readResult = ctx.read(jsonPath);

                List<String> values = ((List<?>) readResult).stream().filter(Objects::nonNull).map(Object::toString).toList();
                extractedValuesMap.computeIfAbsent(jsonPath, k -> new ArrayList<>()).addAll(values);
            }
        }

        for (Map.Entry<String, String> entry : params.getJsonPathToKeysMap().entrySet()) {
            String jsonPath = entry.getKey();
            String mappedKey = entry.getValue();
            List<String> values = extractedValuesMap.getOrDefault(jsonPath, Collections.emptyList());

            if (values.isEmpty()) {
                if (params.isErrorOnKeyNotFound()) {
                    return new ErrorResult(context, "Key not found: " + jsonPath);
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
}

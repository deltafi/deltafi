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
package org.deltafi.core.action.annotate;

import org.apache.tika.utils.StringUtils;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Annotate extends TransformAction<AnnotateParameters> {
    public Annotate() {
        super("Adds annotations");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull AnnotateParameters params, @NotNull TransformInput input) {
        String error = validateAnnotations(params.annotations);
        if (error != null) {
            return new ErrorResult(context, "Invalid annotations", error);
        }

        TransformResult result = new TransformResult(context);
        result.addContent(input.content());
        result.addAnnotations(params.annotations);
        return result;
    }

    private String validateAnnotations(Map<String, String> annotations) {
        for (Map.Entry<String, String> entry : annotations.entrySet()) {
            if (StringUtils.isBlank(entry.getKey())) {
                return "Contains a blank key";
            }
            if (StringUtils.isBlank(entry.getValue())) {
                return "Key " + entry.getKey() + " contains a blank value";
            }
        }
        return null;
    }
}
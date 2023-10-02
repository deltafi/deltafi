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
package org.deltafi.actionkit.action;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A Result that may include changes to annotations
 */
@EqualsAndHashCode(callSuper = true)
public abstract class AnnotationsResult<T extends Result<T>> extends Result<T> {
    @Getter
    protected final Map<String, String> annotations = new HashMap<>();

    public AnnotationsResult(@NotNull ActionContext context, @NotNull ActionEventType actionEventType) {
        super(context, actionEventType);
    }

    /**
     * Add annotation to this DeltaFile that will be made searchable.
     * Multiple entries can be added by repeatedly calling this method.
     * @param key that will be annotated
     * @param value value for the given key
     */
    public void addAnnotation(@NotNull String key, @NotNull String value) {
        annotations.put(key, value);
    }

    /**
     * Add all the annotations in the given map to this Result. These entries will be searchable.
     * @param metadata map of entries that will be added to the annotations
     */
    public void addAnnotations(@NotNull Map<String, String> metadata) {
        annotations.putAll(metadata);
    }
}

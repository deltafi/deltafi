/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.parameters.annotation;

import org.deltafi.common.constant.DeprecatedSince;

import java.lang.annotation.*;

/**
 * Apply this annotation to a field to generate minLength and maxLength properties for the field in the generated JSON
 * schema. Currently, the maxLength property is used by the user interface to determine if the field should accept
 * single (&lt;= 80) or multi-line (&gt; 80) input.
 * @deprecated Use {{@link org.deltafi.common.json.Size} instead}. Will be removed in 3.0.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
public @interface Size {
    /**
     * Minimum string length for this field in the generated JSON schema.
     * @return the minimum length
     */
    int minLength() default 0;
    /**
     * Maximum string length for this field in the generated JSON schema.
     * @return the maximum length
     */
    int maxLength() default 80;
}
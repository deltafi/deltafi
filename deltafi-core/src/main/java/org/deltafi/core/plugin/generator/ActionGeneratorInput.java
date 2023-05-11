/**
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
package org.deltafi.core.plugin.generator;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionType;

@Data
public class ActionGeneratorInput {
    private String className;
    private String description;
    private ActionType actionType;
    private String parameterClassName;

    private String packageName;
    private String actionsPackageName;
    private String paramsPackageName;
    private String fullClassName;

    public ActionGeneratorInput() {

    }

    public ActionGeneratorInput(String className, String fullClassName) {
        this.className = className;
        this.fullClassName = fullClassName;
    }

    public ActionGeneratorInput(String className, ActionType actionType, String description) {
        this.className = className;
        this.actionType = actionType;
        this.description = description;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
        this.actionsPackageName = packageName + ".actions";
        this.paramsPackageName = packageName + ".parameters";
        this.fullClassName = actionsPackageName + "." + className;
    }

    public void validate() {
        requireNonBlank(className, "The action className must be set");
        requireNonBlank(description, "The action description must be set");

        if (actionType == null) {
            throw new IllegalArgumentException("The actionType must be set");
        }
    }

    void requireNonBlank(String field, String message) {
        if (StringUtils.isBlank(field)) {
            throw new IllegalArgumentException(message);
        }
    }
}

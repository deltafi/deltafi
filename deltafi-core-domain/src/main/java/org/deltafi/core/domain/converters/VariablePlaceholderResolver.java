/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.converters;

import org.deltafi.core.domain.generated.types.Variable;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.*;

public class VariablePlaceholderResolver implements PropertyPlaceholderHelper.PlaceholderResolver {

    // TODO - search property sets if the variable isn't found
    private final List<Variable> variables;
    private final Set<Variable> appliedVariables;

    public VariablePlaceholderResolver(List<Variable> variables) {
        this.variables = Objects.nonNull(variables) ? variables : Collections.emptyList();
        this.appliedVariables = new HashSet<>();
    }

    @Override
    public String resolvePlaceholder(@NotNull String placeholderName) {
        Variable variable = matchingVariable(placeholderName);

        if (Objects.nonNull(variable)) {
            appliedVariables.add(variable);
            return valueFromVariable(variable);
        }

        return null;
    }

    public Set<Variable> getAppliedVariables() {
        return appliedVariables;
    }

    private Variable matchingVariable(@NotNull String placeholderName) {
        return variables.stream()
                .filter(variable -> variable.getName().equals(placeholderName))
                .findFirst().orElse(null);
    }

    private String valueFromVariable(Variable variable) {
        return Objects.isNull(variable.getValue()) ? variable.getDefaultValue() : variable.getValue();
    }
}

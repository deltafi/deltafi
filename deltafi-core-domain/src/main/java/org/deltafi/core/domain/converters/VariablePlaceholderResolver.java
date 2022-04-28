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

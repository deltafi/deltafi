package org.deltafi.core.domain.converters;

import org.assertj.core.api.Assertions;
import org.deltafi.core.domain.generated.types.Variable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;

class VariablePlaceholderResolverTest {

    private static final String PLACEHOLDER = "${key}";
    private static final String SET_VALUE = "setValue";
    private static final String DEFAULT_VALUE = "defaultValue";

    @ParameterizedTest
    @MethodSource("testArgs")
    void resolveTest(String placeholder, List<Variable> variables, String expected) {
        VariablePlaceholderResolver resolver = new VariablePlaceholderResolver(variables);

        String result = resolver.resolvePlaceholder(placeholder);

        Assertions.assertThat(result).isEqualTo(expected);
    }

    private static List<Arguments> testArgs() {
        return List.of(
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(SET_VALUE).defaultValue(DEFAULT_VALUE).build()), SET_VALUE),
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(null).defaultValue(DEFAULT_VALUE).build()), DEFAULT_VALUE),
        Arguments.of(PLACEHOLDER, List.of(Variable.newBuilder().name(PLACEHOLDER).value(null).defaultValue(null).build()), null),
        Arguments.of("${unresolved}", List.of(Variable.newBuilder().name(PLACEHOLDER).value(SET_VALUE).defaultValue(DEFAULT_VALUE).build()), null),
        Arguments.of(PLACEHOLDER, Collections.emptyList(), null),
        Arguments.of(PLACEHOLDER, null, null));
    }

}
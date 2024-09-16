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
package org.deltafi.test.asserters;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.Metric;

import java.util.Map;
import java.util.Objects;

/**
 * The base class that provides common metric checks
 * @param <A> The class that extended this
 * @param <T> The expected result type
 */
public class ResultAssert <A extends AbstractAssert<A, T>, T extends Result<T>>
        extends AbstractAssert<A, T> {

    protected ResultAssert(T t, Class<?> selfType) {
        super(t, selfType);
    }

    /**
     * Verify that the result contains a metric with the given name, value and tags
     * @param name of the expected metric
     * @param value of the expected metric
     * @param tags of the expected metric
     * @return myself
     */
    public A hasMetric(String name, long value, Map<String, String> tags) {
        return hasMetric(new Metric(name, value, tags));
    }

    /**
     * Verify that the result contains the given metric
     * @param metric expected metric
     * @return myself
     */
    public A hasMetric(Metric metric) {
        Assertions.assertThat(actual.getCustomMetrics()).contains(metric);
        return myself;
    }

    /**
     * Verify that the result contains a metric with the given name
     * @param name of the expected metric
     * @return myself
     */
    public A hasMetricNamed(String name) {
        Assertions.assertThat(actual.getCustomMetrics()).anyMatch(m -> Objects.equals(name, m.getName()));
        return myself;
    }

    /**
     * Get the result that is being verified
     * @return the original result with casted to the correct class
     */
    public T getResult() {
        return actual;
    }

}

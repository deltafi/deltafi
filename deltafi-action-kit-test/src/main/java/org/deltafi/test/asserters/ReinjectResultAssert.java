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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ReinjectResult;
import org.deltafi.common.types.ReinjectEvent;

import java.util.function.Predicate;

/**
 * Assertions for ReinjectResults
 */
public class ReinjectResultAssert extends ResultAssert<ReinjectResultAssert, ReinjectResult> {
    public ReinjectResultAssert(ReinjectResult reinjectResult) {
        super(reinjectResult, ReinjectResultAssert.class);
    }

    /**
     * Create a new ReinjectResultAssert with the given result
     * @param reinjectResult to validate
     * @return a new ReinjectResultAssert
     */
    public static ReinjectResultAssert assertThat(ReinjectResult reinjectResult) {
        return new ReinjectResultAssert(reinjectResult);
    }

    /**
     * Verify that at least one ReinjectEvent in the results reinjectEvents list
     * satisfies the given predicate
     * @param reinjectEventMatcher predicate used to find the matching reinjectEvent
     * @return this
     */
    public ReinjectResultAssert hasReinjectEventMatching(Predicate<ReinjectEvent> reinjectEventMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getReinjectEvents()).anyMatch(reinjectEventMatcher);
        return this;
    }

    /**
     * Verify that the ReinjectEvent at the given index of the reinjectEvents list
     * satisfies the given predicate
     * @param index of the ReinjectEvent to check
     * @param reinjectEventMatcher predicate used to validate the ReinjectEvent
     * @return this
     */
    public ReinjectResultAssert hasReinjectEventMatchingAt(int index, Predicate<ReinjectEvent> reinjectEventMatcher) {
        isNotNull();
        if (actual.getReinjectEvents() == null || actual.getReinjectEvents().size() -1 < index) {
            String reinjectSize = actual.getReinjectEvents() == null ? "reinject events list is null" : "reinject events list has size " + actual.getReinjectEvents().size();
            failWithMessage("There is no reinject event at index %s (%s)", index, reinjectSize);
            return this;
        }
        ReinjectEvent reinjectEvent = actual.getReinjectEvents().get(index);
        Assertions.assertThat(reinjectEvent).matches(reinjectEventMatcher);
        return this;
    }

    /**
     * Verify that the reinjectEventList size matches the given size
     * @param size expected size of the reinjectEvent list
     * @return
     */
    public ReinjectResultAssert hasReinjectEventSize(int size) {
        isNotNull();
        Assertions.assertThat(actual.getReinjectEvents()).hasSize(size);
        return this;
    }
}

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
package org.deltafi.core.types;

import org.deltafi.core.generated.types.BackOff;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResumePolicyTest {

    @Test
    void testValid() {
        assertTrue(getValid().validate().isEmpty());
    }

    @Test
    void testMissingAll() {
        ResumePolicy resumePolicy = new ResumePolicy();
        resumePolicy.setId(null);
        assertTrue(resumePolicy.validate().containsAll(List.of(
                ResumePolicy.MISSING_ID,
                ResumePolicy.MISSING_NAME,
                ResumePolicy.MISSING_CRITERIA,
                ResumePolicy.INVALID_MAX_ATTEMPTS,
                ResumePolicy.MISSING_BACKOFF)));
    }

    @Test
    void testValidateBackOff() {
        ResumePolicy invalidDelay = getValid();
        invalidDelay.getBackOff().setDelay(-1);
        assertEquals(invalidDelay.validate(), List.of(ResumePolicy.INVALID_DELAY));

        ResumePolicy invalidRandom = getValid();
        invalidRandom.getBackOff().setRandom(true);
        assertEquals(invalidRandom.validate(), List.of(ResumePolicy.MISSING_MAX_DELAY));

        ResumePolicy randomIsNull = getValid();
        randomIsNull.getBackOff().setRandom(null);
        randomIsNull.getBackOff().setMaxDelay(9999);
        assertEquals(Collections.emptyList(), randomIsNull.validate());

        ResumePolicy validRandom = getValid();
        validRandom.getBackOff().setRandom(true);
        validRandom.getBackOff().setMaxDelay(9999);
        assertEquals(Collections.emptyList(), validRandom.validate());

        ResumePolicy invalidMaxDelay = getValid();
        invalidMaxDelay.getBackOff().setRandom(true);
        invalidMaxDelay.getBackOff().setMaxDelay(-1);
        assertEquals(invalidMaxDelay.validate(), List.of(ResumePolicy.MAX_DELAY_ERROR, ResumePolicy.INVALID_MAX_DELAY));

        ResumePolicy notRandom = getValid();
        notRandom.getBackOff().setRandom(false);
        assertEquals(Collections.emptyList(), notRandom.validate());

        ResumePolicy badMultiplier = getValid();
        badMultiplier.getBackOff().setMultiplier(0);
        assertEquals(badMultiplier.validate(), List.of(ResumePolicy.INVALID_MULTIPLIER));

        ResumePolicy goodMultiplier = getValid();
        goodMultiplier.getBackOff().setMultiplier(1);
        assertEquals(Collections.emptyList(), goodMultiplier.validate());
    }

    @Test
    void testMatch() {
        assertTrue(policy("error", "dataSource", "dataSource.action")
                .isMatch(1, "the error message", "dataSource", "dataSource.action"));
        assertTrue(policy("error", "dataSource", null)
                .isMatch(1, "the error message", "dataSource", "fow.action"));
        assertTrue(policy("error", "dataSource", null)
                .isMatch(1, "the error message", "dataSource", "dataSource.action"));
        assertTrue(policy("error", null, null)
                .isMatch(1, "the error message", "dataSource", "dataSource.action"));

        assertTrue(policy(null, "dataSource", "dataSource.action")
                .isMatch(1, "the error message", "dataSource", "dataSource.action"));
        assertTrue(policy(null, "dataSource", null)
                .isMatch(1, "the error message", "dataSource", "dataSource.action"));
        assertTrue(policy(null, "dataSource", "dataSource.action")
                .isMatch(1, "the error message", "dataSource", "dataSource.action"));
        assertTrue(policy(null, null, "dataSource.action")
                .isMatch(1, "the error message", "dataSource", "dataSource.action"));
    }

    @Test
    void testNoMatch() {
        assertFalse(policy("error", "dataSource", "dataSource.action")
                .isMatch(2, "the error message", "dataSource", "dataSource.action"));
        assertFalse(policy("error", "dataSource", "dataSource.action")
                .isMatch(3, "the error message", "dataSource", "dataSource.action"));

        assertFalse(policy("error", "dataSource", "dataSource.action")
                .isMatch(1, "the Error message", "dataSource", "dataSource.action"));
        assertFalse(policy("error", "dataSource", null)
                .isMatch(1, "the err message", "dataSource", "dataSource.action"));
        assertFalse(policy("error", "dataSource", null)
                .isMatch(1, "", "dataSource", "dataSource.action"));

        assertFalse(policy(null, "dataSource", "dataSource.action")
                .isMatch(1, "the error message", "one", "two"));
        assertFalse(policy(null, "dataSource", "dataSource.action")
                .isMatch(1, "the error message", "other", "dataSource.action"));
        assertFalse(policy(null, null, "dataSource.action")
                .isMatch(1, "the error message", "dataSource", "dataSource.other"));
    }

    @Test
    void testPriority() {
        ResumePolicy shortErrorAndFlow = policy("error", "dataSource", null);
        shortErrorAndFlow.validate();
        assertEquals(100, shortErrorAndFlow.getPriority());

        ResumePolicy allCriteria = policy("aLongErrorMessage", "dataSource", "dataSource.action");
        allCriteria.validate();
        assertEquals(250, allCriteria.getPriority());

        ResumePolicy withoutAction = policy("aLongErrorMessage", "dataSource", null);
        withoutAction.validate();
        assertEquals(150, withoutAction.getPriority());

        ResumePolicy presetPriority = policy("aLongErrorMessage", "dataSource", null);
        presetPriority.setPriority(123);
        presetPriority.validate();
        assertEquals(123, presetPriority.getPriority());
    }

    private ResumePolicy getValid() {
        ResumePolicy policy = new ResumePolicy();
        policy.setId(UUID.randomUUID());
        policy.setName("name");
        policy.setDataSource("dataSource");
        policy.setMaxAttempts(2);

        BackOff backoff = new BackOff();
        backoff.setDelay(100);

        policy.setBackOff(backoff);

        return policy;
    }

    private ResumePolicy policy(String error, String flow, String action) {
        ResumePolicy policy = new ResumePolicy();
        policy.setId(UUID.randomUUID());
        policy.setName("name");
        policy.setMaxAttempts(2);

        if (null != error) policy.setErrorSubstring(error);
        if (null != flow) policy.setDataSource(flow);
        if (null != action) policy.setAction(action);

        BackOff backoff = new BackOff();
        backoff.setDelay(100);

        policy.setBackOff(backoff);
        assertNull(policy.getPriority());
        assertTrue(policy.validate().isEmpty());
        assertTrue(policy.getPriority() > 0);

        return policy;
    }

}

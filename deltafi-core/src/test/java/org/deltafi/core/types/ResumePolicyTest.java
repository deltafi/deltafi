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
package org.deltafi.core.types;

import org.deltafi.core.generated.types.BackOff;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumePolicyTest {

    @Test
    void testValid() {
        assertTrue(getValid().validate().isEmpty());
    }

    @Test
    void testMissingAll() {
        assertTrue(new ResumePolicy().validate().containsAll(List.of(
                ResumePolicy.MISSING_ID,
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
        assertEquals(randomIsNull.validate(), Collections.emptyList());

        ResumePolicy validRandom = getValid();
        validRandom.getBackOff().setRandom(true);
        validRandom.getBackOff().setMaxDelay(9999);
        assertEquals(validRandom.validate(), Collections.emptyList());

        ResumePolicy invalidMaxDelay = getValid();
        invalidMaxDelay.getBackOff().setRandom(true);
        invalidMaxDelay.getBackOff().setMaxDelay(-1);
        assertEquals(invalidMaxDelay.validate(), List.of(ResumePolicy.MAX_DELAY_ERROR, ResumePolicy.INVALID_MAX_DELAY));

        ResumePolicy notRandom = getValid();
        notRandom.getBackOff().setRandom(false);
        assertEquals(notRandom.validate(), Collections.emptyList());

        ResumePolicy badMultiplier = getValid();
        badMultiplier.getBackOff().setMultiplier(0);
        assertEquals(badMultiplier.validate(), List.of(ResumePolicy.INVALID_MULTIPLIER));

        ResumePolicy goodMultiplier = getValid();
        goodMultiplier.getBackOff().setMultiplier(1);
        assertEquals(goodMultiplier.validate(), Collections.emptyList());
    }

    @Test
    void testMatch() {
        assertTrue(policy("error", "flow", "action", "type")
                .isMatch("the error message", "flow", "action", "type"));
        assertTrue(policy("error", "flow", null, "type")
                .isMatch("the error message", "flow", "action", "type"));
        assertTrue(policy("error", "flow", null, null)
                .isMatch("the error message", "flow", "action", "type"));
        assertTrue(policy("error", null, null, null)
                .isMatch("the error message", "flow", "action", "type"));

        assertTrue(policy(null, "flow", "action", null)
                .isMatch("the error message", "flow", "action", "type"));
        assertTrue(policy(null, "flow", null, "type")
                .isMatch("the error message", "flow", "action", "type"));
        assertTrue(policy(null, "flow", "action", "type")
                .isMatch("the error message", "flow", "action", "type"));
        assertTrue(policy(null, null, "action", "type")
                .isMatch("the error message", "flow", "action", "type"));
    }

    @Test
    void testNoMatch() {
        assertFalse(policy("error", "flow", "action", "type")
                .isMatch("the Error message", "flow", "action", "type"));
        assertFalse(policy("error", "flow", null, "type")
                .isMatch("the err message", "flow", "action", "type"));
        assertFalse(policy("error", "flow", null, null)
                .isMatch("", "flow", "action", "type"));

        assertFalse(policy(null, "flow", "action", null)
                .isMatch("the error message", "one", "two", "type"));
        assertFalse(policy(null, "flow", null, "type")
                .isMatch("the error message", "flow", "action", "other"));
        assertFalse(policy(null, "flow", "action", "type")
                .isMatch("the error message", "other", "action", "type"));
        assertFalse(policy(null, null, "action", "type")
                .isMatch("the error message", "flow", "other", "type"));
    }

    private ResumePolicy getValid() {
        ResumePolicy policy = new ResumePolicy();
        policy.setId("id");
        policy.setFlow("name");
        policy.setMaxAttempts(1);

        BackOff backoff = new BackOff();
        backoff.setDelay(100);

        policy.setBackOff(backoff);

        return policy;
    }

    private ResumePolicy policy(String error, String flow, String action, String actionType) {
        ResumePolicy policy = new ResumePolicy();
        policy.setId("id");
        policy.setMaxAttempts(1);

        if (null != error) policy.setErrorSubstring(error);
        if (null != flow) policy.setFlow(flow);
        if (null != action) policy.setAction(action);
        if (null != actionType) policy.setActionType(actionType);

        BackOff backoff = new BackOff();
        backoff.setDelay(100);

        policy.setBackOff(backoff);
        assertTrue(policy.validate().isEmpty());
        return policy;
    }

}

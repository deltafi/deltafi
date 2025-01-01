/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.validation;

import org.deltafi.core.types.DiskSpaceDeletePolicy;
import org.deltafi.core.types.TimedDeletePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletePolicyValidatorTest {

    private static final int MAX_PERCENT = 80;
    private static final Long MIN_BYTES = 9999L;
    private static final UUID POLICY_ID = UUID.randomUUID();
    private static final String POLICY_NAME = "policyName";
    private static final String FLOW = "dataSource";
    private static final String GOOD_DURATION = "PT10M";
    private static final String BAD_DURATION = "GARBAGE";
    private static final String ERROR_MESSAGE = "Timed delete policy " + POLICY_NAME
            + " must specify exactly one of afterCreate or afterComplete and/or minBytes";

    @Test
    void testDiskSpaceDeletePolicy() {
        DiskSpaceDeletePolicy policy = makeDiskSpaceDeletePolicy(false);
        DiskSpaceDeletePolicy withOptional = makeDiskSpaceDeletePolicy(true);
        assertTrue(DeletePolicyValidator.validate(policy).isEmpty());
        assertTrue(DeletePolicyValidator.validate(withOptional).isEmpty());
    }

    @Test
    void testInvalidDiskSpaceDeletePolicy() {
        DiskSpaceDeletePolicy policy = makeDiskSpaceDeletePolicy(false);
        policy.setId(null);
        policy.setName(null);
        policy.setFlow("");
        policy.setMaxPercent(-1);

        List<String> errors = DeletePolicyValidator.validate(policy);
        assertEquals(4, errors.size());
        assertTrue(errors.containsAll(List.of(
                "id is missing",
                "name is missing",
                "dataSource is invalid",
                "maxPercent is invalid")));
    }

    @Test
    void testAfterCreate() {
        TimedDeletePolicy policy = makeAfterCreate(false);
        TimedDeletePolicy withOptional = makeAfterCreate(true);
        assertTrue(DeletePolicyValidator.validate(policy).isEmpty());
        assertTrue(DeletePolicyValidator.validate(withOptional).isEmpty());

        TimedDeletePolicy minBytesOnly = makeAfterCreate(true);
        minBytesOnly.setAfterCreate(null);
        assertTrue(DeletePolicyValidator.validate(minBytesOnly).isEmpty());
    }

    @Test
    void testAfterComplete() {
        TimedDeletePolicy policy = makeAfterComplete(false);
        TimedDeletePolicy withOptional = makeAfterComplete(true);
        assertTrue(DeletePolicyValidator.validate(policy).isEmpty());
        assertTrue(DeletePolicyValidator.validate(withOptional).isEmpty());
    }

    @Test
    void testInvalidTimedDeletePolicy() {
        TimedDeletePolicy policy = makeAfterCreate(false);
        policy.setId(null);
        policy.setName(null);
        policy.setFlow("");
        policy.setAfterCreate(BAD_DURATION);

        List<String> errors = DeletePolicyValidator.validate(policy);
        assertEquals(4, errors.size());
        assertTrue(errors.containsAll(List.of(
                "id is missing",
                "name is missing",
                "dataSource is invalid",
                "Unable to parse duration for afterCreate")));
    }

    @Test
    void testInvalidBothDurationsIncluded() {
        TimedDeletePolicy policy = makeAfterCreate(true);
        policy.setAfterComplete(GOOD_DURATION);
        List<String> errors = DeletePolicyValidator.validate(policy);
        assertEquals(1, errors.size());
        assertEquals(ERROR_MESSAGE, errors.getFirst());
    }

    @Test
    void testInvalidTimedDeleteNoConstraints() {
        TimedDeletePolicy policy = makeAfterCreate(false);
        policy.setAfterCreate(null);
        List<String> errors = DeletePolicyValidator.validate(policy);
        assertEquals(1, errors.size());
        assertEquals(ERROR_MESSAGE, errors.getFirst());
    }

    private DiskSpaceDeletePolicy makeDiskSpaceDeletePolicy(boolean withOptional) {
        DiskSpaceDeletePolicy policy = new DiskSpaceDeletePolicy();
        policy.setId(POLICY_ID);
        policy.setName(POLICY_NAME);
        policy.setMaxPercent(MAX_PERCENT);
        if (withOptional) {
            policy.setFlow(FLOW);
        }
        return policy;
    }

    private TimedDeletePolicy makeAfterCreate(boolean withOptional) {
        return makeTimedDeletePolicy(true, withOptional);
    }

    private TimedDeletePolicy makeAfterComplete(boolean withOptional) {
        return makeTimedDeletePolicy(false, withOptional);
    }

    private TimedDeletePolicy makeTimedDeletePolicy(boolean afterCreate, boolean withOptional) {
        TimedDeletePolicy policy = new TimedDeletePolicy();
        policy.setId(POLICY_ID);
        policy.setName(POLICY_NAME);
        if (afterCreate) {
            policy.setAfterCreate(GOOD_DURATION);
        } else {
            policy.setAfterComplete(GOOD_DURATION);
        }
        if (withOptional) {
            policy.setFlow(FLOW);
            policy.setMinBytes(MIN_BYTES);
        }
        return policy;
    }

}

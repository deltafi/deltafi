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
package org.deltafi.core.domain.api.types;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowAssignmentRuleTest {

    @Test
    void testValid() {
        assertTrue(getValid().validate().isEmpty());
    }

    @Test
    void testMissingAll() {
        assertTrue(new FlowAssignmentRule().validate().containsAll(List.of(
                FlowAssignmentRule.MISSING_RULE_NAME,
                FlowAssignmentRule.MISSING_FLOW_NAME,
                FlowAssignmentRule.INVALID_PRIORITY,
                FlowAssignmentRule.MISSING_CRITERIA)));
    }

    @Test
    void testNoName() {
        assertTrue(getNoName().validate().contains(FlowAssignmentRule.MISSING_RULE_NAME));
        assertEquals(1, getNoName().validate().size());
    }

    @Test
    void testNoFlowName() {
        assertTrue(getNoFlow().validate().contains(FlowAssignmentRule.MISSING_FLOW_NAME));
        assertEquals(1, getNoFlow().validate().size());
    }

    @Test
    void testInvalidPriority() {
        assertTrue(getInvalidPriority().validate().contains(FlowAssignmentRule.INVALID_PRIORITY));
        assertEquals(1, getInvalidPriority().validate().size());
    }

    @Test
    void testNoCriteria() {
        assertTrue(getNoCriteria().validate().contains(FlowAssignmentRule.MISSING_CRITERIA));
        assertEquals(1, getNoCriteria().validate().size());
    }

    @Test
    void testMissingMatchCriteria() {
        assertTrue(getEmptyCriteria().validate().contains(FlowAssignmentRule.MISSING_CRITERIA));
        assertTrue(getEmptyCriteria2().validate().contains(FlowAssignmentRule.MISSING_CRITERIA));
    }

    private FlowAssignmentRule getValid() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("name");
        rule.setFlow("flow");
        rule.setPriority(1);
        rule.setFilenameRegex("regex");
        rule.setRequiredMetadata(List.of(new KeyValue("k", "v")));
        return rule;
    }

    private FlowAssignmentRule getNoName() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setFlow("flow");
        rule.setPriority(1);
        rule.setFilenameRegex("regex");
        rule.setRequiredMetadata(List.of(new KeyValue("k", "v")));
        return rule;
    }

    private FlowAssignmentRule getNoFlow() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("name");
        rule.setPriority(1);
        rule.setFilenameRegex("regex");
        rule.setRequiredMetadata(List.of(new KeyValue("k", "v")));
        return rule;
    }

    private FlowAssignmentRule getInvalidPriority() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("name");
        rule.setFlow("flow");
        rule.setFilenameRegex("regex");
        rule.setRequiredMetadata(List.of(new KeyValue("k", "v")));
        return rule;
    }

    private FlowAssignmentRule getNoCriteria() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("name");
        rule.setFlow("flow");
        rule.setPriority(1);
        return rule;
    }

    private FlowAssignmentRule getEmptyCriteria() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("name");
        rule.setFlow("flow");
        rule.setPriority(1);
        rule.setFilenameRegex("");
        return rule;
    }

    private FlowAssignmentRule getEmptyCriteria2() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("name");
        rule.setFlow("flow");
        rule.setPriority(1);
        rule.setRequiredMetadata(List.of());
        return rule;
    }

}

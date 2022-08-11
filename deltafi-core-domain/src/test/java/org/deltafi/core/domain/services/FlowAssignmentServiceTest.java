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
package org.deltafi.core.domain.services;

import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.domain.types.FlowAssignmentRule;
import org.deltafi.core.domain.repo.FlowAssignmentRuleRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowAssignmentServiceTest {

    private static final String META_FLOW = "metaFlow";
    private static final String REGEX_FLOW = "regexFlow";
    private static final String PASS_DOWN_FLOW = "passDownFlow";
    private static final String BOTH_FLOW = "bothFlow";

    @Mock
    FlowAssignmentRuleRepo flowAssignmentRuleRepo;

    @InjectMocks
    FlowAssignmentService flowAssignmentService;

    @Test
    void testRegexMatch() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertEquals(REGEX_FLOW, flowAssignmentService.findFlow(
                new SourceInfo("abcd", "", List.of())));
    }

    @Test
    void testNoRegexMatch() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertNull(flowAssignmentService.findFlow(
                new SourceInfo("123abc", "", List.of())));
    }

    @Test
    void testNoMetaMatch() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertNull(flowAssignmentService.findFlow(
                new SourceInfo("file", "", List.of(new KeyValue("key1", "val1")))));
    }

    @Test
    void testMetaMatch() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertEquals(META_FLOW, flowAssignmentService.findFlow(
                new SourceInfo("file", "", List.of(
                        new KeyValue("key2", "val2"),
                        new KeyValue("key1", "val1")))));
    }

    @Test
    void testNoMatchesBoth() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertNull(flowAssignmentService.findFlow(
                new SourceInfo("def", "", List.of(new KeyValue("x", "x")))));
    }

    @Test
    void testMatchesBoth() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertEquals(BOTH_FLOW, flowAssignmentService.findFlow(
                new SourceInfo("def", "", List.of(
                        new KeyValue("key3", "val3")))));
    }

    @Test
    void testMatchesNextRule() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertEquals(PASS_DOWN_FLOW, flowAssignmentService.findFlow(
                new SourceInfo("file", "", List.of(
                        new KeyValue("key3", "val3")))));
    }

    @Test
    void testValidSave() {
        assertTrue(flowAssignmentService.save(getRegexRule()).getSuccess());
    }

    @Test
    void testSaveNullName() {
        assertFalse(flowAssignmentService.save(new FlowAssignmentRule()).getSuccess());
    }

    private List<FlowAssignmentRule> getAllRules() {
        List<FlowAssignmentRule> rules = List.of(
                getRegexRule(),
                getMetaRule(),
                getMetaAndRegexRule(),
                getPassDownRule());
        return rules;
    }

    private FlowAssignmentRule getRegexRule() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("regexRule");
        rule.setFlow(REGEX_FLOW);
        rule.setPriority(1);
        rule.setFilenameRegex("^abc.*");
        return rule;
    }

    private FlowAssignmentRule getMetaRule() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("metaRule");
        rule.setFlow(META_FLOW);
        rule.setPriority(2);
        rule.setPriority(FlowAssignmentService.DEFAULT_PRIORITY + 1);
        rule.setRequiredMetadata(List.of(
                new KeyValue("key1", "val1"),
                new KeyValue("key2", "val2")));
        return rule;
    }

    private FlowAssignmentRule getMetaAndRegexRule() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("bothRule");
        rule.setFlow(BOTH_FLOW);
        rule.setPriority(3);
        rule.setPriority(FlowAssignmentService.DEFAULT_PRIORITY + 2);
        rule.setFilenameRegex("def");
        rule.setRequiredMetadata(List.of(
                new KeyValue("key3", "val3")));
        return rule;
    }

    private FlowAssignmentRule getPassDownRule() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setName("passRule");
        rule.setFlow(PASS_DOWN_FLOW);
        rule.setPriority(4);
        rule.setPriority(FlowAssignmentService.DEFAULT_PRIORITY + 3);
        rule.setRequiredMetadata(List.of(
                new KeyValue("key3", "val3")));
        return rule;
    }
}

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
package org.deltafi.core.services;

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.repo.FlowAssignmentRuleRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.FlowAssignmentRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

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
                new SourceInfo("abcd", "", Map.of())));
    }

    @Test
    void testNoRegexMatch() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertNull(flowAssignmentService.findFlow(
                new SourceInfo("123abc", "", Map.of())));
    }

    @Test
    void testNoMetaMatch() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertNull(flowAssignmentService.findFlow(
                new SourceInfo("file", "", Map.of("key1", "val1"))));
    }

    @Test
    void testMetaMatch() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertEquals(META_FLOW, flowAssignmentService.findFlow(
                new SourceInfo("file", "", Map.of("key2", "val2", "key1", "val1"))));
    }

    @Test
    void testNoMatchesBoth() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertNull(flowAssignmentService.findFlow(
                new SourceInfo("def", "", Map.of("x", "x"))));
    }

    @Test
    void testMatchesBoth() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertEquals(BOTH_FLOW, flowAssignmentService.findFlow(
                new SourceInfo("def", "", Map.of("key3", "val3"))));
    }

    @Test
    void testMatchesNextRule() {
        when(flowAssignmentRuleRepo.findByOrderByPriorityAscFlowAsc()).thenReturn(getAllRules());
        flowAssignmentService.refreshCache();
        assertEquals(PASS_DOWN_FLOW, flowAssignmentService.findFlow(
                new SourceInfo("file", "", Map.of("key3", "val3"))));
    }

    @Test
    void testValidSave() {
        assertTrue(flowAssignmentService.save(getRegexRule()).isSuccess());
    }

    @Test
    void testSaveNullName() {
        assertFalse(flowAssignmentService.save(new FlowAssignmentRule()).isSuccess());
    }

    private List<FlowAssignmentRule> getAllRules() {
        return List.of(
                getRegexRule(),
                getMetaRule(),
                getMetaAndRegexRule(),
                getPassDownRule());
    }

    @Test
    void testUpdateSnapshot() {
        List<FlowAssignmentRule> expected = getAllRules();
        SystemSnapshot systemSnapshot = new SystemSnapshot();

        Mockito.when(flowAssignmentRuleRepo.findAll()).thenReturn(expected);

        flowAssignmentService.updateSnapshot(systemSnapshot);

        Assertions.assertThat(systemSnapshot.getFlowAssignmentRules()).isEqualTo(expected);
    }

    @Test
    void testResetFromSnapshot() {
        List<FlowAssignmentRule> expected = getAllRules();
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setFlowAssignmentRules(expected);
        flowAssignmentService.resetFromSnapshot(systemSnapshot, true);

        Mockito.verify(flowAssignmentRuleRepo).deleteAll();
        Mockito.verify(flowAssignmentRuleRepo).saveAll(expected);
    }

    private FlowAssignmentRule getRegexRule() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setId("regexRule");
        rule.setName("regexRule");
        rule.setFlow(REGEX_FLOW);
        rule.setPriority(1);
        rule.setFilenameRegex("^abc.*");
        return rule;
    }

    private FlowAssignmentRule getMetaRule() {
        FlowAssignmentRule rule = new FlowAssignmentRule();
        rule.setId("metaRule");
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
        rule.setId("bothRule");
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
        rule.setId("passRule");
        rule.setName("passRule");
        rule.setFlow(PASS_DOWN_FLOW);
        rule.setPriority(4);
        rule.setPriority(FlowAssignmentService.DEFAULT_PRIORITY + 3);
        rule.setRequiredMetadata(List.of(
                new KeyValue("key3", "val3")));
        return rule;
    }
}

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
// ABOUTME: Unit tests for PluginInstallCheck health check.
// ABOUTME: Verifies correct status codes and messages for plugin installation states.
package org.deltafi.core.monitor.checks;

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.PluginState;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.PluginService.PluginStateSummary;
import org.deltafi.core.types.GroupIdArtifactId;
import org.deltafi.core.types.PluginEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.core.monitor.checks.CheckResult.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginInstallCheckTest {

    @Mock
    private PluginService pluginService;

    private PluginInstallCheck pluginInstallCheck;

    @BeforeEach
    void setUp() {
        pluginInstallCheck = new PluginInstallCheck(pluginService);
    }

    @Test
    void check_allHealthy_returnsGreen() {
        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(3, List.of(), List.of(), List.of(), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_GREEN);
        assertThat(result.message()).isEqualTo("All 3 plugin(s) are installed and healthy.");
    }

    @Test
    void check_noPlugins_returnsGreen() {
        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(0, List.of(), List.of(), List.of(), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_GREEN);
        assertThat(result.message()).isEqualTo("All 0 plugin(s) are installed and healthy.");
    }

    @Test
    void check_onePending_returnsYellow() {
        PluginEntity pendingPlugin = createPlugin("org.deltafi", "my-plugin", PluginState.PENDING);
        pendingPlugin.setImageName("registry.example.com/my-plugin");
        pendingPlugin.setImageTag("1.0.0");

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(2, List.of(pendingPlugin), List.of(), List.of(), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_YELLOW);
        assertThat(result.message()).contains("Plugins In Progress");
        assertThat(result.message()).contains("__registry.example.com/my-plugin:1.0.0__ (pending)");
    }

    @Test
    void check_oneInstalling_returnsYellow() {
        PluginEntity installingPlugin = createPlugin("org.deltafi", "hello-world", PluginState.INSTALLING);
        installingPlugin.setImageName("deltafi/hello-world");
        installingPlugin.setImageTag("2.0.0");
        installingPlugin.setInstallAttempts(1);

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(1, List.of(), List.of(installingPlugin), List.of(), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_YELLOW);
        assertThat(result.message()).contains("Plugins In Progress");
        assertThat(result.message()).contains("__deltafi/hello-world:2.0.0__ (installing)");
        assertThat(result.message()).doesNotContain("Attempt:");
    }

    @Test
    void check_installingWithMultipleAttempts_showsAttemptCount() {
        PluginEntity installingPlugin = createPlugin("org.deltafi", "retry-plugin", PluginState.INSTALLING);
        installingPlugin.setImageName("deltafi/retry-plugin");
        installingPlugin.setImageTag("1.0.0");
        installingPlugin.setInstallAttempts(3);

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(1, List.of(), List.of(installingPlugin), List.of(), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_YELLOW);
        assertThat(result.message()).contains("__deltafi/retry-plugin:1.0.0__ (installing)");
        assertThat(result.message()).contains("Attempt: 3");
    }

    @Test
    void check_oneRemoving_returnsYellow() {
        PluginEntity removingPlugin = createPlugin("org.deltafi", "old-plugin", PluginState.REMOVING);
        removingPlugin.setImageName("deltafi/old-plugin");
        removingPlugin.setImageTag("0.9.0");

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(2, List.of(), List.of(), List.of(), List.of(removingPlugin))
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_YELLOW);
        assertThat(result.message()).contains("Plugins In Progress");
        assertThat(result.message()).contains("__deltafi/old-plugin:0.9.0__ (removing)");
    }

    @Test
    void check_multiplePendingAndInstalling_showsAll() {
        PluginEntity pending1 = createPlugin("org.deltafi", "pending-one", PluginState.PENDING);
        pending1.setImageName("deltafi/pending-one");
        pending1.setImageTag("1.0.0");
        PluginEntity pending2 = createPlugin("com.example", "pending-two", PluginState.PENDING);
        pending2.setImageName("example/pending-two");
        pending2.setImageTag("2.0.0");
        PluginEntity installing = createPlugin("org.deltafi", "installing-one", PluginState.INSTALLING);
        installing.setImageName("deltafi/installing-one");
        installing.setImageTag("3.0.0");
        installing.setInstallAttempts(1);

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(1, List.of(pending1, pending2), List.of(installing), List.of(), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_YELLOW);
        assertThat(result.message()).contains("__deltafi/pending-one:1.0.0__ (pending)");
        assertThat(result.message()).contains("__example/pending-two:2.0.0__ (pending)");
        assertThat(result.message()).contains("__deltafi/installing-one:3.0.0__ (installing)");
    }

    @Test
    void check_oneFailed_returnsRed() {
        PluginEntity failedPlugin = createPlugin("org.deltafi", "broken-plugin", PluginState.FAILED);
        failedPlugin.setImageName("deltafi/broken-plugin");
        failedPlugin.setImageTag("1.0.0");
        failedPlugin.setInstallError("Container failed to start");
        failedPlugin.setInstallAttempts(2);

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(2, List.of(), List.of(), List.of(failedPlugin), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_RED);
        assertThat(result.message()).contains("Failed Plugin Installations");
        assertThat(result.message()).contains("__deltafi/broken-plugin:1.0.0__");
        assertThat(result.message()).contains("Error: Container failed to start");
        assertThat(result.message()).contains("Attempts: 2");
        assertThat(result.message()).contains("deltafi plugin retry");
        assertThat(result.message()).contains("[Plugins](/config/plugins)");
    }

    @Test
    void check_failedWithNoError_showsAttemptsOnly() {
        PluginEntity failedPlugin = createPlugin("org.deltafi", "failed-plugin", PluginState.FAILED);
        failedPlugin.setImageName("deltafi/failed-plugin");
        failedPlugin.setImageTag("1.0.0");
        failedPlugin.setInstallError(null);
        failedPlugin.setInstallAttempts(1);

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(0, List.of(), List.of(), List.of(failedPlugin), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_RED);
        assertThat(result.message()).contains("__deltafi/failed-plugin:1.0.0__");
        assertThat(result.message()).contains("Attempts: 1");
        assertThat(result.message()).doesNotContain("Error:");
    }

    @Test
    void check_multipleFailed_showsAllWithDetails() {
        PluginEntity failed1 = createPlugin("org.deltafi", "broken-one", PluginState.FAILED);
        failed1.setImageName("deltafi/broken-one");
        failed1.setImageTag("1.0.0");
        failed1.setInstallError("Install timed out after 5 minutes");
        failed1.setInstallAttempts(3);

        PluginEntity failed2 = createPlugin("com.example", "broken-two", PluginState.FAILED);
        failed2.setImageName("example/broken-two");
        failed2.setImageTag("2.0.0");
        failed2.setInstallError("Image not found");
        failed2.setInstallAttempts(1);

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(1, List.of(), List.of(), List.of(failed1, failed2), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_RED);
        assertThat(result.message()).contains("__deltafi/broken-one:1.0.0__");
        assertThat(result.message()).contains("Error: Install timed out after 5 minutes");
        assertThat(result.message()).contains("Attempts: 3");
        assertThat(result.message()).contains("__example/broken-two:2.0.0__");
        assertThat(result.message()).contains("Error: Image not found");
    }

    @Test
    void check_failedAndInProgress_returnsRedWithBothSections() {
        PluginEntity failed = createPlugin("org.deltafi", "failed-plugin", PluginState.FAILED);
        failed.setImageName("deltafi/failed-plugin");
        failed.setImageTag("1.0.0");
        failed.setInstallError("Container crashed");
        failed.setInstallAttempts(2);

        PluginEntity installing = createPlugin("org.deltafi", "new-plugin", PluginState.INSTALLING);
        installing.setImageName("deltafi/new-plugin");
        installing.setImageTag("2.0.0");
        installing.setInstallAttempts(1);

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(1, List.of(), List.of(installing), List.of(failed), List.of())
        );

        CheckResult result = pluginInstallCheck.check();

        // RED because of failed plugin (takes precedence over YELLOW)
        assertThat(result.code()).isEqualTo(CODE_RED);
        assertThat(result.message()).contains("Failed Plugin Installations");
        assertThat(result.message()).contains("__deltafi/failed-plugin:1.0.0__");
        assertThat(result.message()).contains("Plugins In Progress");
        assertThat(result.message()).contains("__deltafi/new-plugin:2.0.0__ (installing)");
    }

    @Test
    void check_allStatesPresent_showsCorrectOrder() {
        PluginEntity pending = createPlugin("org.deltafi", "pending", PluginState.PENDING);
        pending.setImageName("deltafi/pending");
        pending.setImageTag("1.0.0");
        PluginEntity installing = createPlugin("org.deltafi", "installing", PluginState.INSTALLING);
        installing.setImageName("deltafi/installing");
        installing.setImageTag("1.0.0");
        installing.setInstallAttempts(1);
        PluginEntity failed = createPlugin("org.deltafi", "failed", PluginState.FAILED);
        failed.setImageName("deltafi/failed");
        failed.setImageTag("1.0.0");
        failed.setInstallError("Error");
        failed.setInstallAttempts(1);
        PluginEntity removing = createPlugin("org.deltafi", "removing", PluginState.REMOVING);
        removing.setImageName("deltafi/removing");
        removing.setImageTag("1.0.0");

        when(pluginService.getInstallSummary()).thenReturn(
                new PluginStateSummary(5, List.of(pending), List.of(installing), List.of(failed), List.of(removing))
        );

        CheckResult result = pluginInstallCheck.check();

        assertThat(result.code()).isEqualTo(CODE_RED);
        // Failed section should come before in-progress section
        int failedIndex = result.message().indexOf("Failed Plugin Installations");
        int inProgressIndex = result.message().indexOf("Plugins In Progress");
        assertThat(failedIndex).isLessThan(inProgressIndex);
    }

    private PluginEntity createPlugin(String groupId, String artifactId, PluginState state) {
        PluginEntity plugin = new PluginEntity();
        plugin.setKey(new GroupIdArtifactId(groupId, artifactId));
        plugin.setVersion("1.0.0");
        plugin.setInstallState(state);
        return plugin;
    }
}

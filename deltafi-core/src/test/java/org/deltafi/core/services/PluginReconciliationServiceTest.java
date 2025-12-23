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
// ABOUTME: Unit tests for PluginReconciliationService.
// ABOUTME: Tests state transitions and reconciliation logic for plugin installation.
package org.deltafi.core.services;

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.types.GroupIdArtifactId;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.common.types.PluginState;
import org.deltafi.core.types.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginReconciliationServiceTest {

    private static final PluginCoordinates TEST_COORDS = new PluginCoordinates("org.test", "test-plugin", "1.0.0");

    @Mock
    private PluginService pluginService;

    @Mock
    private DeployerService deployerService;

    @Mock
    private FlowValidationService flowValidationService;

    @Mock
    private SystemSnapshotService systemSnapshotService;

    private PluginReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new PluginReconciliationService(pluginService, deployerService, flowValidationService, systemSnapshotService);
    }

    @Test
    void reconcile_pendingPlugin_startsInstall() {
        PluginEntity plugin = createPlugin(PluginState.PENDING);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.installOrUpgradePlugin(anyString(), any())).thenReturn(new Result());

        reconciliationService.reconcile();

        verify(pluginService).markInstalling(eq(TEST_COORDS), eq(false));
        // Deploy runs async, use timeout to wait for it
        verify(deployerService, timeout(1000)).installOrUpgradePlugin(eq("registry.example.com/test-plugin:1.0.0"), any());
    }

    @Test
    void reconcile_installingPlugin_marksInstalledWhenReady() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLING);
        plugin.setRegistrationHash("abc123");  // Plugin has registered
        plugin.setLastStateChange(OffsetDateTime.now());

        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);
        when(deployerService.getRunningPluginImage(anyString())).thenReturn("registry.example.com/test-plugin:1.0.0");

        reconciliationService.reconcile();

        verify(pluginService).markInstalled(TEST_COORDS);
        verify(flowValidationService).revalidateFlowsForPlugin(TEST_COORDS);
    }

    @Test
    void reconcile_installingPlugin_redeploysWhenWrongImageRunning() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLING);
        plugin.setRegistrationHash("abc123");  // Plugin has registered
        plugin.setImageTag("2.0.0");  // Expected: registry.example.com/test-plugin:2.0.0
        plugin.setLastStateChange(OffsetDateTime.now());

        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);
        when(deployerService.getRunningPluginImage(anyString())).thenReturn("registry.example.com/test-plugin:1.0.0");  // Wrong version running
        when(deployerService.installOrUpgradePlugin(anyString(), any())).thenReturn(new Result());

        reconciliationService.reconcile();

        // Should NOT mark installed - wrong image is running
        verify(pluginService, never()).markInstalled(any());
        // Should redeploy with correct image (async)
        verify(deployerService, timeout(1000)).installOrUpgradePlugin(eq("registry.example.com/test-plugin:2.0.0"), any());
    }

    @Test
    void reconcile_installingPlugin_marksFailedOnTimeout() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLING);
        plugin.setLastStateChange(OffsetDateTime.now().minusMinutes(10));  // Timed out

        when(pluginService.getPlugins()).thenReturn(List.of(plugin));

        reconciliationService.reconcile();

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(pluginService).markFailed(eq(TEST_COORDS), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("timed out");
    }

    @Test
    void reconcile_installedPlugin_restartsIfNotRunning() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(false);
        when(deployerService.installOrUpgradePlugin(anyString(), any())).thenReturn(new Result());

        reconciliationService.reconcile();

        verify(pluginService).markInstalling(eq(TEST_COORDS), eq(true));  // isAutoRestart = true
        // Deploy runs async
        verify(deployerService, timeout(1000)).installOrUpgradePlugin(eq("registry.example.com/test-plugin:1.0.0"), any());
    }

    @Test
    void reconcile_installedPlugin_noActionIfRunningCorrectImage() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);
        when(deployerService.getRunningPluginImage(anyString())).thenReturn("registry.example.com/test-plugin:1.0.0");

        reconciliationService.reconcile();

        verify(pluginService, never()).markInstalling(any(), anyBoolean());
        verify(pluginService, never()).markFailed(any(), anyString());
    }

    @Test
    void reconcile_installedPlugin_redeploysIfWrongImage() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        plugin.setImageTag("2.0.0");  // Expected: registry.example.com/test-plugin:2.0.0
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);
        when(deployerService.getRunningPluginImage(anyString())).thenReturn("registry.example.com/test-plugin:1.0.0");  // Running: old version
        when(deployerService.installOrUpgradePlugin(anyString(), any())).thenReturn(new Result());

        reconciliationService.reconcile();

        verify(pluginService).markInstalling(eq(TEST_COORDS), eq(false));  // isAutoRestart = false (this is a version change)
        // Deploy runs async
        verify(deployerService, timeout(1000)).installOrUpgradePlugin(eq("registry.example.com/test-plugin:2.0.0"), any());
    }

    @Test
    void reconcile_installedPlugin_matchesImageWithDifferentRegistryPrefix() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);
        // Docker sometimes returns images without registry prefix
        when(deployerService.getRunningPluginImage(anyString())).thenReturn("test-plugin:1.0.0");

        reconciliationService.reconcile();

        // Should recognize this as matching (same name:tag, different registry prefix)
        verify(pluginService, never()).markInstalling(any(), anyBoolean());
    }

    @Test
    void reconcile_failedPlugin_noActionWaitsForUser() {
        PluginEntity plugin = createPlugin(PluginState.FAILED);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));

        reconciliationService.reconcile();

        // Failed plugins stay failed - user must explicitly retry or rollback
        verify(pluginService, never()).markInstalling(any(), anyBoolean());
        verify(deployerService, never()).installOrUpgradePlugin(anyString(), any());
    }

    @Test
    void reconcile_removingPlugin_deletesWhenStopped() {
        PluginEntity plugin = createPlugin(PluginState.REMOVING);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(false);

        reconciliationService.reconcile();

        verify(pluginService).uninstallPlugin(TEST_COORDS);
    }

    @Test
    void reconcile_removingPlugin_stopsIfStillRunning() {
        PluginEntity plugin = createPlugin(PluginState.REMOVING);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);

        reconciliationService.reconcile();

        verify(deployerService).removePlugin(anyString());
        verify(pluginService, never()).uninstallPlugin(any());
    }

    @Test
    void reconcile_installFailure_marksFailed() {
        PluginEntity plugin = createPlugin(PluginState.PENDING);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.installOrUpgradePlugin(anyString(), any()))
                .thenReturn(Result.builder().success(false).errors(List.of("Pull failed: 403 Forbidden")).build());

        reconciliationService.reconcile();

        // markFailed is called from async task
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(pluginService, timeout(1000)).markFailed(eq(TEST_COORDS), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("403 Forbidden");
    }

    @Test
    void reconcile_skipsSystemPlugin() {
        PluginEntity systemPlugin = new PluginEntity();
        systemPlugin.setKey(PluginService.SYSTEM_PLUGIN_ID);
        systemPlugin.setInstallState(PluginState.INSTALLED);

        when(pluginService.getPlugins()).thenReturn(List.of(systemPlugin));

        reconciliationService.reconcile();

        verify(deployerService, never()).isPluginRunning(any());
        verify(deployerService, never()).installOrUpgradePlugin(any(), any());
    }

    @Test
    void reconcile_processesMultipleCallsSequentially() {
        // Sequential reconcile calls should both run (no global lock)
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);
        when(deployerService.getRunningPluginImage(anyString())).thenReturn("registry.example.com/test-plugin:1.0.0");

        // Call twice in sequence
        reconciliationService.reconcile();
        reconciliationService.reconcile();

        // Should be called twice (no blocking between calls)
        verify(pluginService, times(2)).getPlugins();
    }

    @Test
    void pluginEntity_canRollback_trueWhenFailedWithPreviousVersion() {
        PluginEntity plugin = createPlugin(PluginState.FAILED);
        plugin.setVersion("2.0.0");
        plugin.setLastSuccessfulVersion("1.0.0");
        plugin.setLastSuccessfulImage("registry.example.com/test-plugin");
        plugin.setLastSuccessfulImageTag("1.0.0");

        assertThat(plugin.canRollback()).isTrue();
    }

    @Test
    void pluginEntity_canRollback_falseWhenNotFailed() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        plugin.setLastSuccessfulVersion("1.0.0");
        plugin.setLastSuccessfulImage("registry.example.com/test-plugin");

        assertThat(plugin.canRollback()).isFalse();
    }

    @Test
    void pluginEntity_canRollback_falseWhenNoLastSuccessfulVersion() {
        PluginEntity plugin = createPlugin(PluginState.FAILED);
        plugin.setLastSuccessfulVersion(null);

        assertThat(plugin.canRollback()).isFalse();
    }

    @Test
    void pluginEntity_canRollback_falseWhenSameVersion() {
        PluginEntity plugin = createPlugin(PluginState.FAILED);
        plugin.setVersion("1.0.0");
        plugin.setLastSuccessfulVersion("1.0.0");
        plugin.setLastSuccessfulImage("registry.example.com/test-plugin");

        assertThat(plugin.canRollback()).isFalse();
    }

    @Test
    void pluginEntity_rollback_restoresLastSuccessfulVersion() {
        PluginEntity plugin = createPlugin(PluginState.FAILED);
        plugin.setVersion("2.0.0");
        plugin.setImageName("registry.example.com/test-plugin");
        plugin.setImageTag("2.0.0");
        plugin.setLastSuccessfulVersion("1.0.0");
        plugin.setLastSuccessfulImage("registry.example.com/test-plugin-old");
        plugin.setLastSuccessfulImageTag("1.0.0");
        plugin.setInstallAttempts(3);

        plugin.rollback();

        assertThat(plugin.getVersion()).isEqualTo("1.0.0");
        assertThat(plugin.getImageName()).isEqualTo("registry.example.com/test-plugin-old");
        assertThat(plugin.getImageTag()).isEqualTo("1.0.0");
        assertThat(plugin.getInstallState()).isEqualTo(PluginState.PENDING);
        assertThat(plugin.getInstallAttempts()).isEqualTo(0);
        assertThat(plugin.getInstallError()).isNull();
    }

    @Test
    void pluginEntity_rollback_doesNothingWhenCannotRollback() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        String originalVersion = plugin.getVersion();

        plugin.rollback();

        assertThat(plugin.getVersion()).isEqualTo(originalVersion);
        assertThat(plugin.getInstallState()).isEqualTo(PluginState.INSTALLED);
    }

    @Test
    void pluginEntity_transitionToInstalled_savesLastSuccessful() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLING);
        plugin.setVersion("2.0.0");
        plugin.setImageName("registry.example.com/test-plugin");
        plugin.setImageTag("2.0.0");

        plugin.transitionToInstalled();

        assertThat(plugin.getInstallState()).isEqualTo(PluginState.INSTALLED);
        assertThat(plugin.getLastSuccessfulVersion()).isEqualTo("2.0.0");
        assertThat(plugin.getLastSuccessfulImage()).isEqualTo("registry.example.com/test-plugin");
        assertThat(plugin.getLastSuccessfulImageTag()).isEqualTo("2.0.0");
    }

    @Test
    void reconcile_disabledPlugin_stopsIfRunning() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        plugin.setDisabled(true);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(true);

        reconciliationService.reconcile();

        verify(deployerService).removePlugin(anyString());
        verify(pluginService, never()).markInstalling(any(), anyBoolean());
    }

    @Test
    void reconcile_disabledPlugin_noActionIfNotRunning() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        plugin.setDisabled(true);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(false);

        reconciliationService.reconcile();

        verify(deployerService, never()).removePlugin(anyString());
        verify(deployerService, never()).installOrUpgradePlugin(anyString(), any());
    }

    @Test
    void reconcile_disabledPendingPlugin_notInstalled() {
        PluginEntity plugin = createPlugin(PluginState.PENDING);
        plugin.setDisabled(true);
        when(pluginService.getPlugins()).thenReturn(List.of(plugin));
        when(deployerService.isPluginRunning(anyString())).thenReturn(false);

        reconciliationService.reconcile();

        verify(pluginService, never()).markInstalling(any(), anyBoolean());
        verify(deployerService, never()).installOrUpgradePlugin(anyString(), any());
    }

    @Test
    void pluginEntity_disableAndEnable_stateTransitions() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);

        // Initially not disabled
        assertThat(plugin.isDisabled()).isFalse();

        // Disable
        plugin.setDisabled(true);
        assertThat(plugin.isDisabled()).isTrue();

        // Enable - should transition to PENDING
        plugin.setDisabled(false);
        plugin.transitionToPending();
        assertThat(plugin.isDisabled()).isFalse();
        assertThat(plugin.getInstallState()).isEqualTo(PluginState.PENDING);
    }

    @Test
    void pluginEntity_transitionToInstalling_clearsRegistrationHashForUpgrade() {
        PluginEntity plugin = createPlugin(PluginState.PENDING);
        plugin.setRegistrationHash("old-hash");

        // Upgrade (not auto-restart) should clear registration hash
        plugin.transitionToInstalling(false);

        assertThat(plugin.getInstallState()).isEqualTo(PluginState.INSTALLING);
        assertThat(plugin.getRegistrationHash()).isNull();
        assertThat(plugin.getInstallAttempts()).isEqualTo(1);
    }

    @Test
    void pluginEntity_transitionToInstalling_preservesRegistrationHashForAutoRestart() {
        PluginEntity plugin = createPlugin(PluginState.INSTALLED);
        plugin.setRegistrationHash("existing-hash");

        // Auto-restart should preserve registration hash (same image being restarted)
        plugin.transitionToInstalling(true);

        assertThat(plugin.getInstallState()).isEqualTo(PluginState.INSTALLING);
        assertThat(plugin.getRegistrationHash()).isEqualTo("existing-hash");
        assertThat(plugin.getInstallAttempts()).isEqualTo(0);
    }

    private PluginEntity createPlugin(PluginState state) {
        PluginEntity plugin = new PluginEntity();
        plugin.setKey(new GroupIdArtifactId(TEST_COORDS.getGroupId(), TEST_COORDS.getArtifactId()));
        plugin.setVersion(TEST_COORDS.getVersion());
        plugin.setImageName("registry.example.com/test-plugin");
        plugin.setImageTag("1.0.0");
        plugin.setInstallState(state);
        plugin.setLastStateChange(OffsetDateTime.now());
        return plugin;
    }
}

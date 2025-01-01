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
package org.deltafi.core.security;

import org.deltafi.common.constant.DeltaFiConstants;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Holds all the permission annotations used in the system
 */
public class NeedsPermission {

    private static final String OR_ADMIN = ", '" + DeltaFiConstants.ADMIN_PERMISSION + "')";

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('" + DeltaFiConstants.ADMIN_PERMISSION + "')")
    public @interface Admin {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeletePolicyCreate'" + OR_ADMIN)
    public @interface DeletePolicyCreate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeletePolicyDelete'" + OR_ADMIN)
    public @interface DeletePolicyDelete {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeletePolicyRead'" + OR_ADMIN)
    public @interface DeletePolicyRead {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeletePolicyUpdate'" + OR_ADMIN)
    public @interface DeletePolicyUpdate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileAcknowledge'" + OR_ADMIN)
    public @interface DeltaFileAcknowledge {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileCancel'" + OR_ADMIN)
    public @interface DeltaFileCancel {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileContentView'" + OR_ADMIN)
    public @interface DeltaFileContentView {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileReplay'" + OR_ADMIN)
    public @interface DeltaFileReplay {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileIngress'" + OR_ADMIN)
    public @interface DeltaFileIngress {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileMetadataView'" + OR_ADMIN)
    public @interface DeltaFileMetadataView {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileMetadataWrite'" + OR_ADMIN)
    public @interface DeltaFileMetadataWrite {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('DeltaFileResume'" + OR_ADMIN)
    public @interface DeltaFileResume {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('EventRead'" + OR_ADMIN)
    public @interface EventRead {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('EventCreate'" + OR_ADMIN)
    public @interface EventCreate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('EventUpdate'" + OR_ADMIN)
    public @interface EventUpdate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('EventDelete'" + OR_ADMIN)
    public @interface EventDelete {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('FlowPlanCreate'" + OR_ADMIN)
    public @interface FlowPlanCreate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('FlowPlanDelete'" + OR_ADMIN)
    public @interface FlowPlanDelete {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('FlowView'" + OR_ADMIN)
    public @interface FlowView {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('FlowUpdate'" + OR_ADMIN)
    public @interface FlowUpdate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('FlowValidate'" + OR_ADMIN)
    public @interface FlowValidate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('IngressRoutingRuleCreate'" + OR_ADMIN)
    public @interface IngressRoutingRuleCreate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('IngressRoutingRuleDelete'" + OR_ADMIN)
    public @interface IngressRoutingRuleDelete {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('IngressRoutingRuleRead'" + OR_ADMIN)
    public @interface IngressRoutingRuleRead {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('IngressRoutingRuleUpdate'" + OR_ADMIN)
    public @interface IngressRoutingRuleUpdate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('MetricsView'" + OR_ADMIN)
    public @interface MetricsView {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('PluginsView'" + OR_ADMIN)
    public @interface PluginsView {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('PluginInstall'" + OR_ADMIN)
    public @interface PluginInstall {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('PluginUninstall'" + OR_ADMIN)
    public @interface PluginUninstall {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('PluginVariableUpdate'" + OR_ADMIN)
    public @interface PluginVariableUpdate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('PluginRegistration'" + OR_ADMIN)
    public @interface PluginRegistration {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('ResumePolicyApply'" + OR_ADMIN)
    public @interface ResumePolicyApply {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('ResumePolicyDryRun'" + OR_ADMIN)
    public @interface ResumePolicyDryRun {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('ResumePolicyCreate'" + OR_ADMIN)
    public @interface ResumePolicyCreate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('ResumePolicyDelete'" + OR_ADMIN)
    public @interface ResumePolicyDelete {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('ResumePolicyRead'" + OR_ADMIN)
    public @interface ResumePolicyRead {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('ResumePolicyUpdate'" + OR_ADMIN)
    public @interface ResumePolicyUpdate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('RoleCreate'" + OR_ADMIN)
    public @interface RoleCreate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('RoleDelete'" + OR_ADMIN)
    public @interface RoleDelete {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('RoleRead'" + OR_ADMIN)
    public @interface RoleRead {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('RoleUpdate'" + OR_ADMIN)
    public @interface RoleUpdate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('SnapshotCreate'" + OR_ADMIN)
    public @interface SnapshotCreate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('SnapshotDelete'" + OR_ADMIN)
    public @interface SnapshotDelete {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('SnapshotRead'" + OR_ADMIN)
    public @interface SnapshotRead {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('SnapshotRevert'" + OR_ADMIN)
    public @interface SnapshotRevert {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('StressTest'" + OR_ADMIN)
    public @interface StressTest {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('IntegrationTestUpdate'" + OR_ADMIN)
    public @interface IntegrationTestUpdate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('IntegrationTestView'" + OR_ADMIN)
    public @interface IntegrationTestView {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('IntegrationTestDelete'" + OR_ADMIN)
    public @interface IntegrationTestDelete {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('SurveyCreate'" + OR_ADMIN)
    public @interface SurveyCreate {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('SystemPropertiesRead'" + OR_ADMIN)
    public @interface SystemPropertiesRead {}


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('SystemPropertiesUpdate'" + OR_ADMIN)
    public @interface SystemPropertiesUpdate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('StatusView'" + OR_ADMIN)
    public @interface StatusView {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('TopicsDelete'" + OR_ADMIN)
    public @interface TopicsDelete {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('TopicsRead'" + OR_ADMIN)
    public @interface TopicsRead {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('TopicsWrite'" + OR_ADMIN)
    public @interface TopicsWrite {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('ActionEvent'" + OR_ADMIN)
    public @interface ActionEvent {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('UserRead'" + OR_ADMIN)
    public @interface UserRead {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('UserCreate'" + OR_ADMIN)
    public @interface UserCreate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('UserUpdate'" + OR_ADMIN)
    public @interface UserUpdate {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('UserDelete'" + OR_ADMIN)
    public @interface UserDelete {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize(value = "hasAnyAuthority('UIAccess'" + OR_ADMIN)
    public @interface UIAccess {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyAuthority('VersionsView'" + OR_ADMIN)
    public @interface VersionsView {}
}

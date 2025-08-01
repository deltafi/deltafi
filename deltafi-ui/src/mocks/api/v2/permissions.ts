/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

const generateData = () => {
  return [
    {
      category: "Administration",
      name: "Admin",
      description: "One permission to rule them all"
    },
    {
      category: "General",
      name: "UIAccess",
      description: "Grants access to the UI"
    },
    {
      category: "General",
      name: "StatusView",
      description: "Grants the ability to view system status"
    },
    {
      category: "General",
      name: "DashboardView",
      description: "Grants the ability to view the UI dashboard"
    },
    {
      category: "General",
      name: "VersionsView",
      description: "Grants the ability to view running versions"
    },
    {
      category: "General",
      name: "MetricsView",
      description: "Grants the ability to view metrics"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFileMetadataView",
      description: "Grants the ability to query for and view DeltaFiles"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFileContentView",
      description: "Grants the ability to view DeltaFile content"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFileReplay",
      description: "Grants the ability to replay DeltaFiles"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFileResume",
      description: "Grants the ability to resume DeltaFiles in an ERROR stage"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFileAcknowledge",
      description: "Grants the ability to acknowledge DeltaFiles in an ERROR stage"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFilePinning",
      description: "Grants the ability to pin or unpin a DeltaFile"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFileCancel",
      description: "Grants the ability to cancel processing a DeltaFile"
    },
    {
      category: "DeltaFiles",
      name: "DeltaFileIngress",
      description: "Grants the ability to ingress DeltaFiles"
    },
    {
      category: "Flows",
      name: "FlowView",
      description: "Grants the ability to view flows"
    },
    {
      category: "Flows",
      name: "FlowValidate",
      description: "Grants the ability to validate flows"
    },
    {
      category: "Flows",
      name: "FlowUpdate",
      description: "Grants the ability to change flow state and update flows"
    },
    {
      category: "Plugins",
      name: "PluginsView",
      description: "Grants the ability to view installed Plugins"
    },
    {
      category: "Plugins",
      name: "PluginInstall",
      description: "Grants the ability to install Plugins"
    },
    {
      category: "Plugins",
      name: "PluginUninstall",
      description: "Grants the ability to uninstall Plugins"
    },
    {
      category: "Plugins",
      name: "PluginVariableUpdate",
      description: "Grants the ability to edit plugin variables"
    },
    {
      category: "System Properties",
      name: "SystemPropertiesRead",
      description: "Grants the ability to view System Properties"
    },
    {
      category: "System Properties",
      name: "SystemPropertiesUpdate",
      description: "Grants the ability to edit System Properties"
    },
    {
      category: "Delete Policies",
      name: "DeletePolicyCreate",
      description: "Grants the ability to create Delete Policies"
    },
    {
      category: "Delete Policies",
      name: "DeletePolicyRead",
      description: "Grants the ability to view Delete Policies"
    },
    {
      category: "Delete Policies",
      name: "DeletePolicyUpdate",
      description: "Grants the ability to edit Delete Policies"
    },
    {
      category: "Delete Policies",
      name: "DeletePolicyDelete",
      description: "Grants the ability to delete Delete Policies"
    },
    {
      category: "Snapshots",
      name: "SnapshotCreate",
      description: "Grants the ability to delete Snapshots"
    },
    {
      category: "Snapshots",
      name: "SnapshotRead",
      description: "Grants the ability to view Snapshots"
    },
    {
      category: "Snapshots",
      name: "SnapshotDelete",
      description: "Grants the ability to delete Snapshots"
    },
    {
      category: "Snapshots",
      name: "SnapshotRevert",
      description: "Grants the ability to revert the system to a Snapshot"
    },
    {
      category: "Users",
      name: "UserCreate",
      description: "Grants the ability to create Users"
    },
    {
      category: "Users",
      name: "UserRead",
      description: "Grants the ability to view Users"
    },
    {
      category: "Users",
      name: "UserUpdate",
      description: "Grants the ability to edit Users"
    },
    {
      category: "Users",
      name: "UserDelete",
      description: "Grants the ability to delete Users"
    },
    {
      category: "Roles",
      name: "RoleCreate",
      description: "Grants the ability to create Roles"
    },
    {
      category: "Roles",
      name: "RoleRead",
      description: "Grants the ability to view Roles"
    },
    {
      category: "Roles",
      name: "RoleUpdate",
      description: "Grants the ability to edit Roles"
    },
    {
      category: "Roles",
      name: "RoleDelete",
      description: "Grants the ability to delete Roles"
    },
    {
      category: "Stress Tests",
      name: "StressTest",
      description: "Grants the ability to execute Stress Tests"
    }
  ];
};

export default generateData();

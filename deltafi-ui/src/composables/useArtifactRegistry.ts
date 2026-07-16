/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

import { reactive } from "vue";
import useApi from "./useApi";
import useNotifications from "./useNotifications";
import axios from "axios";

// Mirrors the backend ArtifactManifest returned by GET /{owner}/{artifactName}/versions.
// Whether a version is active and which aliases point at it are not part of the manifest:
// the active version comes from the top-level `activeVersion` and aliases from the reverse
// alias map (version -> alias names) on ListVersionsResponse.
export interface ArtifactVersion {
  artifactName: string;
  version: string;
  description?: string;
  contentType?: string;
  createdAt: string;
  modifiedAt?: string;
  sha256: string;
  size: number;
  labels: Record<string, string>;
}

export interface ListVersionsResponse {
  pluginName: string;
  artifactName: string;
  activeVersion?: string;
  versions: ArtifactVersion[];
  aliases: Record<string, string[]>;
}

export interface ArtifactSummary {
  artifactName: string;
  activeVersion?: string;
  activeManifest?: ArtifactVersion;
  totalVersions: number;
  aliases?: Record<string, string>;
}

export interface ListArtifactsResponse {
  pluginName: string;
  artifacts: ArtifactSummary[];
}

export interface PurgeConfig {
  keepLatest: number;
  maxAgeMinutes: number;
}

export interface UploadOptions {
  version?: string;
  description?: string;
  makeActive?: boolean;
  labels?: Record<string, string>;
}

export interface UploadResult {
  loading: boolean;
  error: boolean;
  percentComplete: number;
}

export default function useArtifactRegistry() {
  const { response, loading, errors, get, post, put, remove, buildURL } = useApi(2);
  const notify = useNotifications();

  const basePath = "artifacts";

  const listAllArtifacts = async () => {
    await get(basePath);
    return response;
  };

  const listArtifacts = async (owner: string) => {
    await get(`${basePath}/${owner}`);
    return response;
  };

  const listVersions = async (owner: string, artifactName: string) => {
    await get(`${basePath}/${owner}/${artifactName}/versions`);
    return response;
  };

  const uploadArtifact = (owner: string, artifactName: string, file: File, options: UploadOptions = {}) => {
    const result = reactive<UploadResult>({
      loading: true,
      error: false,
      percentComplete: 0,
    });

    const formData = new FormData();
    formData.append("file", file);
    if (options.version) formData.append("version", options.version);
    if (options.description) formData.append("description", options.description);
    if (options.makeActive !== undefined) formData.append("makeActive", String(options.makeActive));
    // The backend treats every non-reserved multipart field as an individual label
    // (reserved: file, version, description, makeActive, alias), so send each label as
    // its own field rather than a single JSON-encoded "labels" field.
    if (options.labels) {
      for (const [key, value] of Object.entries(options.labels)) {
        formData.append(key, value);
      }
    }

    axios
      .request({
        method: "post",
        url: `/api/v2/${basePath}/${owner}/${artifactName}`,
        data: formData,
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (progressEvent: any) => {
          result.percentComplete = Math.round((progressEvent.loaded / progressEvent.total) * 100);
        },
      })
      .then(() => {
        result.loading = false;
        notify.success("Upload Successful", `Uploaded artifact ${artifactName}`);
      })
      .catch((error) => {
        result.loading = false;
        result.error = true;

        let errorMessage = "Unknown error occurred";
        if (error.response?.data) {
          const serverMessage =
            typeof error.response.data === "string"
              ? error.response.data
              : error.response.data.message || error.response.data.error || "Server error";
          errorMessage = `HTTP ${error.response.status}: ${serverMessage}`;
        } else if (error.message) {
          errorMessage = error.message;
        }

        notify.error(`Failed to upload ${artifactName}`, errorMessage);
      });

    return result;
  };

  const downloadArtifact = async (owner: string, artifactName: string, version: string) => {
    try {
      await get(`${basePath}/${owner}/${artifactName}/versions/${version}`, new URLSearchParams(), false);
      const blob = response.value as Blob;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${artifactName}-${version}`;
      link.click();
      URL.revokeObjectURL(url);
      link.remove();
    } catch {
      // Error notification handled by useApi
    }
  };

  const rollbackArtifact = async (owner: string, artifactName: string, version: string) => {
    try {
      await post(`${basePath}/${owner}/${artifactName}/rollback`, { version });
      notify.success("Active Version Updated", `Set ${artifactName} active version to ${version}`);
    } catch {
      // Error notification handled by useApi
    }
  };

  const deleteVersion = async (owner: string, artifactName: string, version: string) => {
    try {
      await remove(`${basePath}/${owner}/${artifactName}/versions/${version}`);
      notify.success("Version Deleted", `Deleted version ${version} of ${artifactName}`);
    } catch {
      // Error notification handled by useApi
    }
  };

  // Delete several versions in one action. Deletes each sequentially (there is no bulk
  // endpoint) and gives a single summary notification rather than one toast per version;
  // per-version failures are already surfaced by useApi, so we keep going and report what
  // actually got deleted.
  const deleteVersions = async (owner: string, artifactName: string, versions: string[]) => {
    const deleted: string[] = [];
    for (const version of versions) {
      try {
        await remove(`${basePath}/${owner}/${artifactName}/versions/${version}`);
        deleted.push(version);
      } catch {
        // Error notification handled by useApi; continue with the remaining versions.
      }
    }
    if (deleted.length > 0) {
      const count = deleted.length;
      notify.success("Versions Deleted", `Deleted ${count} version${count === 1 ? "" : "s"} of ${artifactName}: ${deleted.join(", ")}`);
    }
    return deleted;
  };

  const deleteArtifact = async (owner: string, artifactName: string) => {
    try {
      await remove(`${basePath}/${owner}/${artifactName}`);
      notify.success("Artifact Deleted", `Deleted artifact ${artifactName}`);
    } catch {
      // Error notification handled by useApi
    }
  };

  const setAlias = async (owner: string, artifactName: string, alias: string, version: string) => {
    try {
      await put(`${basePath}/${owner}/${artifactName}/aliases/${alias}`, { version });
      notify.success("Alias Set", `Set alias "${alias}" to version ${version}`);
    } catch {
      // Error notification handled by useApi
    }
  };

  const deleteAlias = async (owner: string, artifactName: string, alias: string) => {
    try {
      await remove(`${basePath}/${owner}/${artifactName}/aliases/${alias}`);
      notify.success("Alias Deleted", `Deleted alias "${alias}" from ${artifactName}`);
    } catch {
      // Error notification handled by useApi
    }
  };

  const getPurgeConfig = async (owner: string, artifactName: string) => {
    await get(`${basePath}/${owner}/${artifactName}/purge-config`);
    return response;
  };

  const updatePurgeConfig = async (owner: string, artifactName: string, config: PurgeConfig) => {
    try {
      await put(`${basePath}/${owner}/${artifactName}/purge-config`, config);
      notify.success("Purge Config Updated", `Updated purge config for ${artifactName}`);
    } catch {
      // Error notification handled by useApi
    }
  };

  const purge = async (owner: string, artifactName: string) => {
    try {
      await post(`${basePath}/${owner}/${artifactName}/purge`, {});
      // The endpoint returns the versions it actually removed; report the real result so
      // a no-op purge (e.g. nothing old enough to meet the Max Age policy) isn't reported
      // as if it deleted something.
      const purgedVersions: string[] = response.value?.purgedVersions ?? [];
      if (purgedVersions.length === 0) {
        notify.info("Nothing to Purge", `No versions of ${artifactName} were eligible for purge.`);
      } else {
        const count = purgedVersions.length;
        notify.success("Purge Complete", `Purged ${count} version${count === 1 ? "" : "s"} of ${artifactName}: ${purgedVersions.join(", ")}`);
      }
    } catch {
      // Error notification handled by useApi
    }
  };

  return {
    response,
    loading,
    errors,
    listAllArtifacts,
    listArtifacts,
    listVersions,
    uploadArtifact,
    downloadArtifact,
    rollbackArtifact,
    deleteVersion,
    deleteVersions,
    deleteArtifact,
    setAlias,
    deleteAlias,
    getPurgeConfig,
    updatePurgeConfig,
    purge,
  };
}

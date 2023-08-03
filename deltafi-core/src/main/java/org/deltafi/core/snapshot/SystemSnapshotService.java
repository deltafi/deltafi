/*
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
package org.deltafi.core.snapshot;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.Variable;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.Result;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@AllArgsConstructor
public class SystemSnapshotService {

    private List<Snapshotter> snapshotters;
    private SystemSnapshotRepo systemSnapshotRepo;

    public SystemSnapshot getWithMaskedVariables(String snapshotId) {
        SystemSnapshot snapshot = getById(snapshotId);
        applyMaskToVariables(snapshot);
        return snapshot;
    }

    private SystemSnapshot getById(String snapshotId) {
        return systemSnapshotRepo.findById(snapshotId).orElseThrow(() -> new IllegalArgumentException("No system snapshot found with an id of " + snapshotId));
    }

    public List<SystemSnapshot> getAll() {
        List<SystemSnapshot> snapshots = systemSnapshotRepo.findAll();
        snapshots.forEach(this::applyMaskToVariables);
        return snapshots;
    }

    void applyMaskToVariables(SystemSnapshot systemSnapshot) {
        if (systemSnapshot.getPluginVariables() != null) {
            systemSnapshot.getPluginVariables().forEach(this::applyMaskToVariables);
        }
    }

    void applyMaskToVariables(PluginVariables pluginVariables) {
        if (pluginVariables.getVariables() != null) {
            pluginVariables.setVariables(pluginVariables.getVariables().stream().map(Variable::maskIfSensitive).toList());
        }
    }

    public SystemSnapshot createSnapshot(String reason) {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setReason(reason);
        snapshotters.forEach(snapshotter -> snapshotter.updateSnapshot(systemSnapshot));
        return saveSnapshot(systemSnapshot);
    }

    public Result resetFromSnapshot(String snapshotId, boolean hardReset) {
        return resetFromSnapshot(getById(snapshotId), hardReset);
    }

    public SystemSnapshot saveSnapshot(SystemSnapshot systemSnapshot) {
        return systemSnapshotRepo.save(systemSnapshot);
    }

    public SystemSnapshot importSnapshot(SystemSnapshot systemSnapshot) {
        if (systemSnapshot == null) {
            return null;
        }

        removeMaskedVariables(systemSnapshot);
        return saveSnapshot(systemSnapshot);
    }

    void removeMaskedVariables(SystemSnapshot systemSnapshot) {
        if (systemSnapshot.getPluginVariables() != null) {
            systemSnapshot.setPluginVariables(systemSnapshot.getPluginVariables().stream()
                    .map(this::removeMaskedVariables).filter(Objects::nonNull).toList());
        }
    }

    PluginVariables removeMaskedVariables(PluginVariables pluginVariables) {
        List<Variable> maskedVariables = removeMaskedVariables(pluginVariables.getVariables());
        if (maskedVariables.isEmpty()) {
            return null;
        }

        pluginVariables.setVariables(maskedVariables);
        return pluginVariables;
    }

    List<Variable> removeMaskedVariables(List<Variable> variables) {
        return variables != null ? variables.stream().filter(this::isNotMasked).toList() : List.of();
    }

    private boolean isNotMasked(Variable variable) {
        return variable != null && variable.isNotMasked();
    }

    public Result deleteSnapshot(String id) {
        if (systemSnapshotRepo.existsById(id)) {
            systemSnapshotRepo.deleteById(id);
            return new Result();
        }

        return Result.builder().success(false).errors(List.of("Could not find a snapshot with an ID of " + id)).build();
    }

    private Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        Result baseResult = new Result();
        return snapshotters.stream()
                .map(snapshotter -> snapshotter.resetFromSnapshot(systemSnapshot, hardReset))
                .reduce(baseResult, SystemSnapshotService::combine);
    }

    public static Result combine(Result a, Result b) {
        return Result.builder()
                .success(a.isSuccess() && b.isSuccess())
                .info(combineLists(a.getInfo(), b.getInfo()))
                .errors(combineLists(a.getErrors(), b.getErrors())).build();
    }

    private static List<String> combineLists(List<String> a, List<String> b) {
        List<String> combinedList = new ArrayList<>();
        if (blankList(a) && blankList(b)) {
            return combinedList;
        }

        if (null != a) {
            combinedList.addAll(a);
        }

        if (null != b) {
            combinedList.addAll(b);
        }

        return combinedList;
    }

    private static boolean blankList(List<String> value) {
        return null == value || value.isEmpty();
    }
}

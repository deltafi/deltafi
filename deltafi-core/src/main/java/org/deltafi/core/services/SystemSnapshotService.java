/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.Variable;
import org.deltafi.core.repo.SystemSnapshotRepo;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

@Service
@Slf4j
@AllArgsConstructor
public class SystemSnapshotService {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(new JavaTimeModule());

    private List<Snapshotter> snapshotters;
    private SystemSnapshotRepo systemSnapshotRepo;

    public SystemSnapshot getWithMaskedVariables(UUID snapshotId) {
        SystemSnapshot snapshot = getById(snapshotId);
        maskAndUpgradeSnapshots(snapshot);
        return snapshot;
    }

    private SystemSnapshot getById(UUID snapshotId) {
        return systemSnapshotRepo.findById(snapshotId).orElseThrow(() -> new IllegalArgumentException("No system snapshot found with an id of " + snapshotId));
    }

    public List<SystemSnapshot> getAll() {
        List<SystemSnapshot> snapshots = systemSnapshotRepo.findAll();

        // masked variables are stored unmasked so they can be used if you reset to a snapshot that was created locally
        // when returning the list of all snapshots apply masks to sensitive variables
        snapshots.forEach(this::maskAndUpgradeSnapshots);
        return snapshots;
    }

    void maskAndUpgradeSnapshots(SystemSnapshot systemSnapshot) {
        modifySnapshotData(systemSnapshot, this::applyMaskToVariables);
    }

    void applyMaskToVariables(Snapshot snapshot) {
        if (snapshot.getPluginVariables() != null) {
            snapshot.getPluginVariables().forEach(this::applyMaskToVariables);
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
        Snapshot snapshot = new Snapshot();
        snapshotters.forEach(snapshotter -> snapshotter.updateSnapshot(snapshot));
        systemSnapshot.setSnapshot(OBJECT_MAPPER.convertValue(snapshot, new TypeReference<>() {}));
        return saveSnapshot(systemSnapshot);
    }

    public Result resetFromSnapshot(UUID snapshotId, boolean hardReset) {
        SystemSnapshot systemSnapshot = getById(snapshotId);

        Snapshot snapshotData = mapSnapshotData(systemSnapshot);
        return resetFromSnapshot(snapshotData, hardReset);
    }

    public SystemSnapshot saveSnapshot(SystemSnapshot systemSnapshot) {
        return systemSnapshotRepo.save(systemSnapshot);
    }

    public SystemSnapshot importSnapshot(SystemSnapshot systemSnapshot) {
        if (systemSnapshot == null) {
            return null;
        }

        // remove any variables that were masked before importing
        modifySnapshotData(systemSnapshot, this::removeMaskedVariables);
        return saveSnapshot(systemSnapshot);
    }

    void removeMaskedVariables(Snapshot snasphot) {
        if (snasphot.getPluginVariables() != null) {
            snasphot.setPluginVariables(snasphot.getPluginVariables().stream()
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

    public Result deleteSnapshot(UUID id) {
        if (systemSnapshotRepo.existsById(id)) {
            systemSnapshotRepo.deleteById(id);
            return new Result();
        }

        return Result.builder().success(false).errors(List.of("Could not find a snapshot with an ID of " + id)).build();
    }

    /**
     * Map the given snapshot data to the latest data model
     * @param snapshot that will be used to reset the system settings
     * @return a snapshot using the latest data model that can be applied to the current system
     */
    public Snapshot mapSnapshotData(SystemSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        int version = snapshot.getSchemaVersion();
        if (SystemSnapshot.CURRENT_VERSION == version) {
            return OBJECT_MAPPER.convertValue(snapshot.getSnapshot(), Snapshot.class);
        }

        throw new IllegalArgumentException("Invalid system snapshot schema version '" + version + "' in snapshot with id '" + snapshot.getId() + "' with reason: '" + snapshot.getReason() + "'");
    }

    private Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        Result baseResult = new Result();
        return snapshotters.stream()
                .map(snapshotter -> snapshotter.resetFromSnapshot(snapshot, hardReset))
                .reduce(baseResult, SystemSnapshotService::combine);
    }

    public static Result combine(Result a, Result b) {
        return Result.builder()
                .success(a.isSuccess() && b.isSuccess())
                .info(combineLists(a.getInfo(), b.getInfo()))
                .errors(combineLists(a.getErrors(), b.getErrors())).build();
    }

    private void modifySnapshotData(SystemSnapshot systemSnapshot, Consumer<Snapshot> snapshotConsumer) {
        Snapshot snapshot = mapSnapshotData(systemSnapshot);
        snapshotConsumer.accept(snapshot);
        systemSnapshot.setSnapshot(OBJECT_MAPPER.convertValue(snapshot, new TypeReference<>() {}));
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

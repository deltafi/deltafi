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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.networknt.schema.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.generated.types.SystemSnapshotFilter;
import org.deltafi.core.generated.types.SystemSnapshotSort;
import org.deltafi.core.generated.types.SystemSnapshots;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.SystemSnapshotService;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.SystemSnapshot;

import java.util.List;
import java.util.UUID;

@DgsComponent
@RequiredArgsConstructor
public class SystemSnapshotDatafetcher {

    private final SystemSnapshotService systemSnapshotService;
    private final CoreAuditLogger auditLogger;

    @DgsQuery
    @NeedsPermission.SnapshotRead
    public SystemSnapshot getSystemSnapshot(@InputArgument UUID snapshotId) {
        return systemSnapshotService.getWithMaskedVariables(snapshotId);
    }

    @DgsQuery
    @NeedsPermission.SnapshotRead
    public List<SystemSnapshot> getSystemSnapshots() {
        return systemSnapshotService.getAll();
    }

    @DgsQuery
    @NeedsPermission.SnapshotRead
    public SystemSnapshots getSystemSnapshotsByFilter(
            @InputArgument Integer offset,
            @InputArgument Integer limit,
            @InputArgument SystemSnapshotFilter filter,
            @InputArgument DeltaFileDirection direction,
            @InputArgument SystemSnapshotSort sortField) {
        return systemSnapshotService.getSystemSnapshots(offset, limit, filter, direction, sortField);
    }

    @DgsMutation
    @NeedsPermission.SnapshotCreate
    public SystemSnapshot snapshotSystem(@InputArgument String reason) {
        auditLogger.audit("created system snapshot{}", StringUtils.isNotBlank(reason) ? " with a reason of " + reason : "");
        return systemSnapshotService.createSnapshot(reason);
    }

    @DgsMutation
    @NeedsPermission.SnapshotRevert
    public Result resetFromSnapshotWithId(@InputArgument UUID snapshotId, @InputArgument Boolean hardReset) {
        boolean hard = hardReset == null || hardReset;
        auditLogger.audit("reset to snapshot {} using hardReset {}", snapshotId, hard);
        return systemSnapshotService.resetFromSnapshot(snapshotId, hard);
    }

    @DgsMutation
    @NeedsPermission.SnapshotCreate
    public SystemSnapshot importSnapshot(@InputArgument SystemSnapshot snapshot) {
        auditLogger.audit("imported system snapshot {}", snapshot.getId());
        return systemSnapshotService.importSnapshot(snapshot);
    }

    @DgsMutation
    @NeedsPermission.SnapshotCreateAndRevert
    public Result importSnapshotAndReset(@InputArgument SystemSnapshot snapshot, @InputArgument Boolean hardReset) {
        boolean hard = hardReset == null || hardReset;
        Result result = systemSnapshotService.importSnapshotAndReset(snapshot, hard);
        auditLogger.audit("imported and reset to snapshot {} using hardReset {}", snapshot.getId(), hard);
        return result;
    }

    @DgsMutation
    @NeedsPermission.SnapshotDelete
    public Result deleteSnapshot(@InputArgument UUID snapshotId) {
        auditLogger.audit("deleted system snapshot {}", snapshotId);
        return systemSnapshotService.deleteSnapshot(snapshotId);
    }
}

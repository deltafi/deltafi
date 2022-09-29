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
package org.deltafi.core.snapshot;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.types.Result;

import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class SystemSnapshotDatafetcher {

    private final SystemSnapshotService systemSnapshotService;

    @DgsQuery
    public SystemSnapshot getSystemSnapshot(String snapshotId) {
        return systemSnapshotService.get(snapshotId);
    }

    @DgsQuery
    public List<SystemSnapshot> getSystemSnapshots() {
        return systemSnapshotService.getAll();
    }

    @DgsMutation
    public SystemSnapshot snapshotSystem(@InputArgument String reason) {
        return systemSnapshotService.createSnapshot(reason);
    }

    @DgsMutation
    public Result resetFromSnapshotWithId(String snapshotId, Boolean hardReset) {
        return systemSnapshotService.resetFromSnapshot(snapshotId, Boolean.TRUE.equals(hardReset));
    }

    @DgsMutation
    public SystemSnapshot importSnapshot(SystemSnapshot snapshot) {
        return systemSnapshotService.saveSnapshot(snapshot);
    }
}

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
package org.deltafi.core.services;

import lombok.RequiredArgsConstructor;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.exceptions.ValidationException;
import org.deltafi.core.repo.UiLinkRepo;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UiLinkService implements Snapshotter {

    private final UiLinkRepo uiLinkRepo;

    public Link saveLink(Link link) {
        try {
            return uiLinkRepo.save(link);
        } catch (DataIntegrityViolationException e) {
            throw new ValidationException("A link of type '" + link.getLinkType() + "' with a name of '" + link.getName() + "' already exists");
        }
    }

    public boolean removeLink(UUID id) {
        if (uiLinkRepo.existsById(id)) {
            uiLinkRepo.deleteById(id);
            return true;
        }

        return false;
    }

    public List<Link> getLinks() {
        return uiLinkRepo.findAll();
    }

    /**
     * Update the Snapshot with current system state
     *
     * @param snapshot system snapshot that holds the current system state
     */
    @Override
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setLinks(uiLinkRepo.findAll());
    }

    /**
     * Reset the system to the state in the Snapshot
     *
     * @param snapshot system snapshot that holds the state at the time of the snapshot
     * @param hardReset      when true reset all other custom settings before applying the system snapshot values
     * @return the Result of the reset that will hold any errors or information about the reset
     */
    @Override
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        List<Link> links = Objects.requireNonNullElseGet(snapshot.getLinks(), List::of);
        if (hardReset) {
            uiLinkRepo.deleteAll();
        } else {
            links.forEach(link -> uiLinkRepo.deleteByNameAndLinkType(link.getName(), link.getLinkType()));
        }

        uiLinkRepo.saveAll(links);
        return new Result();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PROPERTIES_ORDER;
    }
}

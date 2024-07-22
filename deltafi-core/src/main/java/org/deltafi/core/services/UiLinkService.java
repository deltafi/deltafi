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
package org.deltafi.core.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.exceptions.ValidationException;
import org.deltafi.core.repo.UiLinkRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;


@Service
@RequiredArgsConstructor
public class UiLinkService implements Snapshotter {

    private final UiLinkRepo uiLinkRepo;

    @PostConstruct
    public void init() {
        uiLinkRepo.ensureAllIndices();
    }

    public Link saveLink(Link link) {
        try {
            return uiLinkRepo.save(link);
        } catch (DuplicateKeyException e) {
            throw new ValidationException("A link of type '" + link.getLinkType() + "' with a name of '" + link.getName() + "' already exists");
        }
    }

    public boolean removeLink(String id) {
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
     * Update the SystemSnapshot with current system state
     *
     * @param systemSnapshot system snapshot that holds the current system state
     */
    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setLinks(uiLinkRepo.findAll());
    }

    /**
     * Reset the system to the state in the SystemSnapshot
     *
     * @param systemSnapshot system snapshot that holds the state at the time of the snapshot
     * @param hardReset      when true reset all other custom settings before applying the system snapshot values
     * @return the Result of the reset that will hold any errors or information about the reset
     */
    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        List<Link> links = Objects.requireNonNullElseGet(systemSnapshot.getLinks(), List::of);
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

package org.deltafi.config.server.repo;

import org.deltafi.config.server.domain.PropertyUpdate;

import java.util.List;
import java.util.Set;

public interface PropertyCustomRepository {

    /**
     * Get a set of all id's in the collection.
     *
     * @return
     */
    Set<String> getIds();

    /**
     * Apply the given updates to the collection
     * @param updates - list of updates to apply
     * @return - number of property sets that were updated
     */
    int updateProperties(List<PropertyUpdate> updates);

}

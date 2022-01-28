package org.deltafi.config.server.repo;

import org.deltafi.config.server.api.domain.PropertyId;
import org.deltafi.config.server.api.domain.PropertyUpdate;

import java.util.List;
import java.util.Set;

public interface PropertyCustomRepository {

    /**
     * Get a set of all id's in the collection.
     *
     * @return - all PropertySet ids
     */
    Set<String> getIds();

    /**
     * Apply the given updates to the collection
     * @param updates - list of updates to apply
     * @return - number of property sets that were updated
     */
    int updateProperties(List<PropertyUpdate> updates);

    /**
     * Unset the value field for each property in the list
     * @param propertyIds - list of properties that need to be unset
     * @return - number of property sets that were updated
     */
    int unsetProperties(List<PropertyId> propertyIds);

}

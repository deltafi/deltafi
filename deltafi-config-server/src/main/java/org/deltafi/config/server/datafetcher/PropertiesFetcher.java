package org.deltafi.config.server.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import org.deltafi.config.server.constants.PropertyConstants;
import org.deltafi.config.server.domain.PropertySet;
import org.deltafi.config.server.domain.PropertyUpdate;
import org.deltafi.config.server.service.PropertyService;

import java.util.List;

@DgsComponent
public class PropertiesFetcher {

    private final PropertyService propertiesService;

    public PropertiesFetcher(PropertyService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @DgsQuery
    public List<PropertySet> getPropertySets() {
        return propertiesService.getAllProperties();
    }

    @DgsMutation
    public int updateProperties(@InputArgument(collectionType = PropertyUpdate.class) List<PropertyUpdate> updates) {
        return propertiesService.updateProperties(updates);
    }

    @DgsMutation
    public boolean addPluginPropertySet(PropertySet propertySet) {
        validateMutation(propertySet.getId());
        propertiesService.saveProperties(propertySet);
        return true;
    }

    @DgsMutation
    public boolean removePluginPropertySet(String propertySetId) {
        validateMutation(propertySetId);
        return propertiesService.removeProperties(propertySetId);
    }

    private void validateMutation(String propertySetId) {
        if (PropertyConstants.ACTION_KIT_PROPERTY_SET.equals(propertySetId) || PropertyConstants.DELTAFI_PROPERTY_SET.equals(propertySetId)) {
            throw new IllegalArgumentException("Core PropertySet: " + propertySetId + " cannot be added, replaced or removed");
        }
    }

}

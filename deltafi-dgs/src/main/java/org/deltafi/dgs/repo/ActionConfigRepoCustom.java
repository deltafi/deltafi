package org.deltafi.dgs.repo;

import org.deltafi.dgs.configuration.ActionConfiguration;
import org.deltafi.dgs.configuration.EnrichActionConfiguration;
import org.deltafi.dgs.configuration.FormatActionConfiguration;
import org.deltafi.dgs.configuration.LoadActionConfiguration;

public interface ActionConfigRepoCustom {

    <C extends ActionConfiguration> C upsertConfiguration(C config, Class<C> clazz);

    boolean exists(ActionConfiguration config);

    LoadActionConfiguration findLoadAction(String name);

    FormatActionConfiguration findFormatAction(String name);

    EnrichActionConfiguration findEnrichAction(String name);

    long deleteAllWithCount();

}

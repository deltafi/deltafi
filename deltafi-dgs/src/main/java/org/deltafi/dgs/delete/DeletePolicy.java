package org.deltafi.dgs.delete;

import org.deltafi.dgs.services.DeltaFilesService;

import java.util.Map;

public abstract class DeletePolicy {
    protected final DeltaFilesService deltaFilesService;
    protected final String name;

    protected final Map<String, String> parameters;

    protected DeletePolicy(DeltaFilesService deltaFilesService, String name, Map<String, String> parameters) {
        this.deltaFilesService = deltaFilesService;
        this.name = name;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public abstract void run();
}

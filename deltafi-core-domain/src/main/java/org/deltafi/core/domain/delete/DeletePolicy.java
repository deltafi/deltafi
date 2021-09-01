package org.deltafi.core.domain.delete;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.deltafi.core.domain.services.DeltaFilesService;

import java.util.Map;

@AllArgsConstructor
@Getter
public abstract class DeletePolicy {
    protected final DeltaFilesService deltaFilesService;
    protected final String name;
    protected final Map<String, String> parameters;

    public abstract void run();
}
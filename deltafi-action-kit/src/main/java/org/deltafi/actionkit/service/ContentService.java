package org.deltafi.actionkit.service;

import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.ObjectReference;

import java.io.InputStream;

public interface ContentService {

    interface InputProcessor {
        void operation(InputStream stream);
    }

    void get(ObjectReference ref, InputProcessor proc);

    byte[] retrieveContent(ObjectReference objectReference);

    ObjectReference putObject(String data, DeltaFile deltaFile, String actionName);

    ObjectReference putObject(ObjectReference objectReference, byte[] object);

    /**
     * Remove stored data for the given DeltaFile
     * @param deltaFile - deltaFile that content needs to be removed for
     * @return - true if all objects were successfully removed, otherwise false
     */
    boolean deleteObjectsForDeltaFile(DeltaFile deltaFile);
}
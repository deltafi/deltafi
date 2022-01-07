package org.deltafi.common.resource;

import java.io.IOException;
import java.util.Objects;

public class Resource {
    public static String read(String path) throws IOException {
        return new String(Objects.requireNonNull(Resource.class.getResourceAsStream(path)).readAllBytes());
    }
}

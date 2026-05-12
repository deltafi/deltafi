/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Pure-JDK classpath resource discovery. Handles {@code file:} URLs (exploded directories, dev mode)
 * and {@code jar:} URLs. Works against standard JARs, Spring Boot fat JARs (the custom
 * {@code JarUrlConnection} Spring Boot registers extends {@link JarURLConnection}, so the standard
 * code path enumerates both the plugin's own {@code BOOT-INF/classes/} resources and any
 * {@code BOOT-INF/lib/*.jar} dependency JAR resources), and any other classloader that emits
 * URLs whose connections are {@code JarURLConnection} subclasses.
 */
@Slf4j
public final class ClasspathScanner {

    private ClasspathScanner() {}

    /**
     * List the immediate children of a classpath directory across every classpath root that contains it.
     */
    public static List<URL> list(ClassLoader loader, String directory) {
        List<URL> results = new ArrayList<>();
        String normalized = directory.endsWith("/") ? directory.substring(0, directory.length() - 1) : directory;
        try {
            Enumeration<URL> dirUrls = loader.getResources(normalized);
            while (dirUrls.hasMoreElements()) {
                URL dirUrl = dirUrls.nextElement();
                String protocol = dirUrl.getProtocol();
                if ("file".equals(protocol)) {
                    listFromFile(dirUrl, results);
                } else if ("jar".equals(protocol)) {
                    listFromJar(dirUrl, results);
                } else {
                    log.warn("Skipping classpath directory '{}' with unsupported URL protocol '{}': {}",
                            normalized, protocol, dirUrl);
                }
            }
        } catch (IOException e) {
            log.warn("Unable to list classpath directory '{}'", normalized, e);
        }
        return results;
    }

    /**
     * Last path segment of a URL (the entry's filename). Works for {@code file:}, {@code jar:file:},
     * and Spring Boot {@code jar:nested:} URLs since {@code URL#toString} always ends with the entry name.
     */
    public static String filenameOf(URL url) {
        String s = url.toString();
        int slash = s.lastIndexOf('/');
        return slash < 0 ? s : s.substring(slash + 1);
    }

    private static void listFromFile(URL url, List<URL> out) throws IOException {
        try {
            File directory = new File(url.toURI());
            File[] children = directory.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                if (child.isFile()) {
                    out.add(child.toURI().toURL());
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid file URL: " + url, e);
        }
    }

    private static void listFromJar(URL dirUrl, List<URL> out) throws IOException {
        JarURLConnection connection = (JarURLConnection) dirUrl.openConnection();
        String prefix = connection.getEntryName();
        if (prefix == null) {
            return;
        }
        // Do not close the JarFile — the connection owns its lifecycle (and may cache it, including
        // under Spring Boot's LaunchedClassLoader where closing would break subsequent lookups).
        JarFile jar = connection.getJarFile();
        String dirPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        URL jarFileUrl = connection.getJarFileURL();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (skipEntry(entry, dirPrefix)) {
                continue;
            }
            out.add(URI.create("jar:" + jarFileUrl + "!/" + entry.getName()).toURL());
        }
    }

    private static boolean skipEntry(JarEntry entry, String dirPrefix) {
        String entryName = entry.getName();
        if (entry.isDirectory() || !entryName.startsWith(dirPrefix)) {
            return true;
        }

        String relative = entryName.substring(dirPrefix.length());
        return relative.isEmpty() || relative.contains("/");
    }
}

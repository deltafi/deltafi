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
package org.deltafi.test.content;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.test.content.loader.ContentLoader;

import java.io.File;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

/**
 * Class used to set up an action for testing. Provides methods for interacting with
 * a memory backed ContentStorageService. Provides a method to return a populated
 * ActionContext that has a reference to the ContentStorageService.
 */
public class DeltaFiTestRunner {

    protected static final String DID = "did";
    protected static final String HOSTNAME = "hostname";
    private final ContentStorageService storageService = new ContentStorageService(new InMemoryObjectStorageService());
    private String testDataFolder = null;

    /**
     * Create a new DeltaFiTestRunner which has an in-memory ContentStorageService
     * that can be interacted with. The in-memory service will be set in the
     * action that is passed in.
     *
     * @param action to add the in-memory ContentStorageService to
     * @return a new instance of a DeltaFiTestRunner
     */
    public static DeltaFiTestRunner setup(Action<?> action) {
        return setup(action, "./");
    }

    /**
     * Create a new DeltaFiTestRunner which has an in-memory ContentStorageService
     * that can be interacted with. The in-memory service will be set in the
     * action that is passed in.
     *
     * @param action action to add the in-memory ContentStorageService to
     * @param testDataFolder name of the base folder to use when loading resources
     * @return a new instance of a DeltaFiTestRunner
     */
    public static DeltaFiTestRunner setup(Action<?> action, String testDataFolder) {
        DeltaFiTestRunner deltaFiTestSetup = new DeltaFiTestRunner();
        deltaFiTestSetup.testDataFolder = testDataFolder;
        action.setContentStorageService(deltaFiTestSetup.storageService);
        return deltaFiTestSetup;
    }

    /**
     * Read the file contents at the given paths and load them into
     * the ContentStorageService to make the data available for testing
     * @param paths list of the paths to the resources to load
     * @return list of ActionContent that should be added to the test input
     * to reference the loaded content
     */
    public List<ActionContent> saveContentFromResource(String ... paths) {
        if (paths == null) {
            return List.of();
        }

        List<String> pathList = List.of(paths);
        if (testDataFolder != null) {
            pathList = pathList.stream().map(this::joinPath).toList();
        }

        return pathList.stream().map(path -> saveContent(ContentLoader.contentLoader().classPathResource(path))).toList();
    }

    /**
     * Load each byte array into the ContentStorageService
     * @param contents byte arrays to store
     * @return list of ActionContent that should be added to the test input
     * to reference the loaded content
     */
    public List<ActionContent> saveContent(byte[] ... contents) {
        if (contents == null) {
            return List.of();
        }
        return Stream.of(contents).map(content -> saveContent(ContentLoader.contentLoader().bytes(content))).toList();
    }

    /**
     * Load each string into the ContentStorageService
     * @param contents string values to store
     * @return list of ActionContent that should be added to the test input
     * to reference the loaded content
     */
    public List<ActionContent> saveContent(String ... contents) {
        return Stream.of(contents)
                .map(content -> saveContent(ContentLoader.contentLoader().string(content))).toList();
    }

    /**
     * Create an empty ActionContent with the given name and mediaType
     * @param name of the content
     * @param mediaType of the content
     * @return an empty ActionContent with the given name and mediaType
     */
    public ActionContent saveEmptyContent(String name, String mediaType) {
        return saveContent(ContentLoader.contentLoader().contentName(name).mediaType(mediaType));
    }

    /**
     * Save the content with the given name and mediaType
     * @param content content to save
     * @param name of the content
     * @param mediaType of the content
     * @return an ActionContent with the given name and mediaType that holds the given content
     */
    public ActionContent saveContent(String content, String name, String mediaType) {
        return saveContent(ContentLoader.contentLoader().contentName(name).string(content).mediaType(mediaType));
    }

    /**
     * Load the values from the ContentLoader into the ContentStorageService
     * @param contentLoader holds the value and details of the content to store
     * @return ActionContent that should be added to the test input
     * to reference the loaded content
     */
    public ActionContent saveContent(ContentLoader contentLoader) {
        try {
            Content content = storageService.save(DID, contentLoader.getValue(), contentLoader.getContentName(), contentLoader.getMediaType());
            return new ActionContent(content, storageService);
        } catch (Exception e) {
            Assertions.fail("Unable to store content", e);
            throw new DeltaFiTestException(e);
        }
    }

    /**
     * Get a new ActionContext with the ContentStorageService populated
     * @return new ActionContext
     */
    public ActionContext actionContext() {
        return ActionContext.builder()
                .did(DID)
                .name("name")
                .sourceFilename("filename")
                .ingressFlow(null)
                .egressFlow(null)
                .hostname(HOSTNAME)
                .systemName("systemName")
                .actionVersion("1.0")
                .startTime(OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .contentStorageService(storageService)
                .build();
    }

    /**
     * Read the bytes of file at the given classpath resource path
     * @param path of the file to read
     * @return byte[] from the content of the file
     */
    public byte[] readResourceAsBytes(String path) {
        path = joinPath(path);
        return ContentLoader.readAsBytesFromClasspath(path).get(0);
    }

    /**
     * Read the classpath resource at the given path as a string using the default charset
     * @param path of the resource to read
     * @return the contents of the resource as a string
     */
    public String readResourceAsString(String path) {
        path = joinPath(path);
        return ContentLoader.readAsStringFromClasspath(List.of(path)).get(0);
    }

    /**
     * Read the content from the storage service as a string
     * @param content to read
     * @return string stored for the given content
     */
    public String readContent(Content content) {
        ActionContent actionContent = new ActionContent(content, storageService);
        return actionContent.loadString();
    }

    private String joinPath(String path) {
        return testDataFolder != null ?
                new File(testDataFolder, path).getPath() : path;
    }

    private static class DeltaFiTestException extends RuntimeException {
        public DeltaFiTestException(Throwable cause) {
            super(cause);
        }
    }
}

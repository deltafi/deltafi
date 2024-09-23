/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.compress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.LineageData;
import org.deltafi.common.types.LineageMap;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.junit.jupiter.api.Assertions.*;

public class DecompressTest {

    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String LINEAGE_CONTENT_TYPE = "application/json";
    private static final String LINEAGE_FILENAME = "Lineage.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FILE1 = "thing1.txt";
    private static final String FILE2 = "thing2.txt";

    static {
        OBJECT_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private final Decompress action = new Decompress();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("DecompressTest");

    Map<String, LineageData> BASE_LINEAGE = Map.of(
            "top-dir/a.txt.gz", new LineageData("top-dir/a.txt.gz", "multi-layers.tgz", false),
            "top-dir/dir2/sub2/b.txt", new LineageData("top-dir/dir2/sub2/b.txt", "multi-layers.tgz", false),
            "top-dir/dir3-has-two-zips.tar", new LineageData("top-dir/dir3-has-two-zips.tar", "multi-layers.tgz", false),
            "top-dir/dir1/three.zip", new LineageData("top-dir/dir1/three.zip", "multi-layers.tgz", false));

    @Test
    public void errorResultOnNoContent() {
        ResultType result = action.transform(runner.actionContext(), new DecompressParameters(),
                TransformInput.builder().build());

        assertInstanceOf(ErrorResult.class, result);
    }

    @Test
    public void errorUsingFormat() {
        DecompressParameters params = new DecompressParameters();
        params.setMaxRecursionLevels(1);
        params.setFormat(Format.AR);

        ResultType result = runRecursiveTest(params, input("multi-layers.tgz"));
        assertInstanceOf(ErrorResult.class, result);
    }

    @Test
    public void errorWithRetainContent() {
        DecompressParameters params = new DecompressParameters();
        params.setMaxRecursionLevels(1);
        params.setRetainExistingContent(true);

        ResultType result = runRecursiveTest(params, input("multi-layers.tgz"));
        assertInstanceOf(ErrorResult.class, result);
    }

    @Test
    public void errorRecursionToHigh() {
        DecompressParameters params = new DecompressParameters();
        params.setMaxRecursionLevels(101);

        ResultType result = runRecursiveTest(params, input("multi-layers.tgz"));
        assertInstanceOf(ErrorResult.class, result);
    }

    @Test
    public void unarchivesAr() {
        runTest(Format.AR, false);
    }

    @Test
    public void unarchiveSevenZ() {
        runTest(Format.SEVEN_Z, false);
    }

    @Test
    public void unarchivesTar() {
        runTest(Format.TAR, false);
    }

    @Test
    public void unarchivesTarGzip() {
        runTest(Format.TAR_GZIP, false);
    }

    @Test
    public void unarchivesTarXz() {
        runTest(Format.TAR_XZ, false);
    }

    @Test
    public void unarchivesTarZ() {
        runTest(Format.TAR_Z, false);
    }

    @Test
    public void unarchivesZip() {
        runTest(Format.ZIP, false);
    }

    @Test
    public void unarchivesArDetected() {
        runTest(Format.AR, true);
    }

    @Test
    public void unarchiveSevenZDetected() {
        runTest(Format.SEVEN_Z, true);
    }

    @Test
    public void unarchivesTarDetected() {
        runTest(Format.TAR, true);
    }

    @Test
    public void unarchivesTarGzipDetected() {
        runTest(Format.TAR_GZIP, true);
    }

    @Test
    public void unarchivesTarXzDetected() {
        runTest(Format.TAR_XZ, true);
    }

    @Test
    public void unarchivesTarZDetected() {
        runTest(Format.TAR_Z, true);
    }

    @Test
    public void unarchivesZipDetected() {
        runTest(Format.ZIP, true);
    }

    @Test
    public void retainAndUnarchivesZipDetected() {
        runTest(Format.ZIP, true, true);
    }

    private void runTest(Format archiveType, boolean detected) {
        runTest(archiveType, detected, false);
    }

    private void runTest(Format archiveType, boolean detected, boolean retainExistingContent) {
        String inputName = "compressed." + archiveType.getValue();
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(detected ? null : archiveType, retainExistingContent, null, 0),
                input(inputName));

        verifyTransform(result, archiveType, retainExistingContent, inputName);
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private void verifyTransform(ResultType result, Format archiveType, boolean retainExistingContent, String originalInputName) {
        int startIdx = retainExistingContent ? 1 : 0;

        assertTransformResult(result)
                .hasContentMatchingAt(startIdx, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(FILE1)
                            .hasMediaType(MediaType.APPLICATION_OCTET_STREAM);
                    return true;
                })
                .hasContentMatchingAt(startIdx + 1, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(FILE2)
                            .hasMediaType(MediaType.APPLICATION_OCTET_STREAM);
                    return true;
                })
                .addedMetadata("compressFormat", archiveType.getValue());

        if (retainExistingContent) {
            assertTransformResult(result)
                    .hasContentMatchingAt(0, actionContent -> {
                        ContentAssert.assertThat(actionContent)
                                .hasName(originalInputName);
                        return true;
                    })
                    .contentLoadBytesEquals(List.of(
                            runner.readResourceAsBytes(originalInputName),
                            runner.readResourceAsBytes(FILE1),
                            runner.readResourceAsBytes(FILE2)));
        } else {
            assertTransformResult(result)
                    .contentLoadBytesEquals(List.of(
                            runner.readResourceAsBytes(FILE1),
                            runner.readResourceAsBytes(FILE2)));
        }
    }

    @Test
    public void decompressesSingleGzip() {
        runTest(Format.GZIP, input("fileA.gz"), "fileA");
    }

    @Test
    public void decompressesMultipleGzip() {
        runTest(Format.GZIP, input("fileA.gz", "fileB.gz"), "fileA", "fileB");
    }

    @Test
    public void retainAndDecompressesMultipleGzip() {
        runTest(Format.GZIP, true, input("fileA.gz", "fileB.gz"), "fileA", "fileB");
    }

    @Test
    public void decompressesSingleXz() {
        runTest(Format.XZ, input("fileA.xz"), "fileA");
    }

    @Test
    public void decompressesSingleZ() {
        runTest(Format.Z, input("fileC.Z"), "fileC");
    }

    @Test
    public void decompressesSingleGzipDetected() {
        runTest(new DecompressParameters(), Format.GZIP, input("fileA.gz"), "fileA");
    }

    @Test
    public void decompressesSingleXzDetected() {
        runTest(new DecompressParameters(), Format.XZ, input("fileA.xz"), "fileA");
    }

    @Test
    public void decompressesSingleZDetected() {
        runTest(new DecompressParameters(), Format.Z, input("fileC.Z"), "fileC");
    }

    @Test
    void nonRecursiveWithLineage() {
        DecompressParameters params = new DecompressParameters();
        params.setLineageFilename(LINEAGE_FILENAME);
        params.setMaxRecursionLevels(0);

        ResultType resultType = runRecursiveTest(params, input("multi-layers.tgz"));

        Map<String, LineageData> lineage = new HashMap<>(BASE_LINEAGE);
        String expectedLineage = mapToJson(lineage);

        assertTransformResult(resultType)
                .hasContentCount(5)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "top-dir/dir2/sub2/b.txt", "bbb\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "top-dir/dir3-has-two-zips.tar", null))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "top-dir/dir1/three.zip", null))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "top-dir/a.txt.gz", null))
                .hasContentMatchingAt(4, actionContent -> lineageMatches(actionContent, LINEAGE_FILENAME, expectedLineage));
    }

    @Test
    void recursiveWithoutLineage() {
        DecompressParameters params = new DecompressParameters();
        params.setMaxRecursionLevels(5);

        ResultType resultType = runRecursiveTest(params, input("multi-layers.tgz"));

        assertTransformResult(resultType)
                .hasContentCount(7)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "top-dir/dir2/sub2/b.txt", "bbb\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "top-dir/dir1/1", "111\n"))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "top-dir/dir1/2", "222\n"))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "top-dir/dir1/3", "333\n"))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "top-dir/a.txt", "aaa\n"))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "top-dir/dir3/z.txt", "zzz\n"))
                .hasContentMatchingAt(6, actionContent -> contentMatches(actionContent, "top-dir/dir3/y.txt", "yyy\n"));
    }

    @Test
    void recurrsiveWithLineage() {
        DecompressParameters params = new DecompressParameters();
        params.setMaxRecursionLevels(5);
        params.setLineageFilename(LINEAGE_FILENAME);

        ResultType resultType = runRecursiveTest(params, input("multi-layers.tgz"));

        assertTransformResult(resultType)
                .hasContentCount(8)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "top-dir/dir2/sub2/b.txt", "bbb\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "top-dir/dir1/1", "111\n"))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "top-dir/dir1/2", "222\n"))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "top-dir/dir1/3", "333\n"))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "top-dir/a.txt", "aaa\n"))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "top-dir/dir3/z.txt", "zzz\n"))
                .hasContentMatchingAt(6, actionContent -> contentMatches(actionContent, "top-dir/dir3/y.txt", "yyy\n"))
                .hasContentMatchingAt(7, actionContent -> lineageMatches(actionContent, LINEAGE_FILENAME, null));
    }

    @Test
    void recurrsiveWithNameCollisions() {
        collisions("collisions.tgz");
    }

    @Test
    void recurrsiveWithNameCollisions7Z() {
        collisions("collisions.7zip");
    }

    private void collisions(String source) {
        DecompressParameters params = new DecompressParameters();
        params.setMaxRecursionLevels(5);
        params.setLineageFilename(LINEAGE_FILENAME);

        // Test file is similar to multi-layers.tgz but adds 3 TAR files, each
        // contain a pathless "c" and "d" file. One of the new TARs is under
        // "top-dir/" but the other 2 are not, and have colliding "c" and "d" names.
        // It also adds 7z archives of the top level TAR files.
        ResultType resultType = runRecursiveTest(params, input(source));

        String lineageJson = """
                {
                  "c": {
                    "fullName": "c",
                    "parentContentName": "c_and_d_2.tar",
                    "modifiedContentName": false
                  },
                  "c_and_d.tar": {
                    "fullName": "c_and_d.tar",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "c_and_d.tar:c": {
                    "fullName": "c",
                    "parentContentName": "c_and_d.tar",
                    "modifiedContentName": true
                  },
                  "c_and_d.tar:d": {
                    "fullName": "d",
                    "parentContentName": "c_and_d.tar",
                    "modifiedContentName": true
                  },
                  "c_and_d_2.tar": {
                    "fullName": "c_and_d_2.tar",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "c_and_d_2_tar.7z": {
                    "fullName": "c_and_d_2_tar.7z",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "c_and_d_2_tar.7z:c_and_d_2.tar": {
                    "fullName": "c_and_d_2.tar",
                    "parentContentName": "c_and_d_2_tar.7z",
                    "modifiedContentName": true
                  },
                  "c_and_d_2_tar.7z:c_and_d_2.tar:c": {
                    "fullName": "c",
                    "parentContentName": "c_and_d_2_tar.7z:c_and_d_2.tar",
                    "modifiedContentName": true
                  },
                  "c_and_d_2_tar.7z:c_and_d_2.tar:d": {
                    "fullName": "d",
                    "parentContentName": "c_and_d_2_tar.7z:c_and_d_2.tar",
                    "modifiedContentName": true
                  },
                  "c_and_d_tar.7z": {
                    "fullName": "c_and_d_tar.7z",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "c_and_d_tar.7z:c_and_d.tar": {
                    "fullName": "c_and_d.tar",
                    "parentContentName": "c_and_d_tar.7z",
                    "modifiedContentName": true
                  },
                  "c_and_d_tar.7z:c_and_d.tar:c": {
                    "fullName": "c",
                    "parentContentName": "c_and_d_tar.7z:c_and_d.tar",
                    "modifiedContentName": true
                  },
                  "c_and_d_tar.7z:c_and_d.tar:d": {
                    "fullName": "d",
                    "parentContentName": "c_and_d_tar.7z:c_and_d.tar",
                    "modifiedContentName": true
                  },
                  "d": {
                    "fullName": "d",
                    "parentContentName": "c_and_d_2.tar",
                    "modifiedContentName": false
                  },
                  "top-dir/a.txt": {
                    "fullName": "top-dir/a.txt",
                    "parentContentName": "top-dir/a.txt.gz",
                    "modifiedContentName": false
                  },
                  "top-dir/a.txt.gz": {
                    "fullName": "top-dir/a.txt.gz",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "top-dir/c": {
                    "fullName": "top-dir/c",
                    "parentContentName": "top-dir/c_and_d.tar",
                    "modifiedContentName": false
                  },
                  "top-dir/c_and_d.tar": {
                    "fullName": "top-dir/c_and_d.tar",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "top-dir/d": {
                    "fullName": "top-dir/d",
                    "parentContentName": "top-dir/c_and_d.tar",
                    "modifiedContentName": false
                  },
                  "top-dir/dir1/1": {
                    "fullName": "top-dir/dir1/1",
                    "parentContentName": "top-dir/dir1/three.zip",
                    "modifiedContentName": false
                  },
                  "top-dir/dir1/2": {
                    "fullName": "top-dir/dir1/2",
                    "parentContentName": "top-dir/dir1/three.zip",
                    "modifiedContentName": false
                  },
                  "top-dir/dir1/3": {
                    "fullName": "top-dir/dir1/3",
                    "parentContentName": "top-dir/dir1/three.zip",
                    "modifiedContentName": false
                  },
                  "top-dir/dir1/three.zip": {
                    "fullName": "top-dir/dir1/three.zip",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "top-dir/dir2/sub2/b.txt": {
                    "fullName": "top-dir/dir2/sub2/b.txt",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "top-dir/dir3-has-two-zips.tar": {
                    "fullName": "top-dir/dir3-has-two-zips.tar",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "top-dir/dir3/y.txt": {
                    "fullName": "top-dir/dir3/y.txt",
                    "parentContentName": "top-dir/dir3/y.txt.zip",
                    "modifiedContentName": false
                  },
                  "top-dir/dir3/y.txt.zip": {
                    "fullName": "top-dir/dir3/y.txt.zip",
                    "parentContentName": "top-dir/dir3-has-two-zips.tar",
                    "modifiedContentName": false
                  },
                  "top-dir/dir3/z.txt": {
                    "fullName": "top-dir/dir3/z.txt",
                    "parentContentName": "top-dir/dir3/z.txt.zip",
                    "modifiedContentName": false
                  },
                  "top-dir/dir3/z.txt.zip": {
                    "fullName": "top-dir/dir3/z.txt.zip",
                    "parentContentName": "top-dir/dir3-has-two-zips.tar",
                    "modifiedContentName": false
                  },
                  "top-dir/dir4.7z": {
                    "fullName": "top-dir/dir4.7z",
                    "parentContentName": "collisions.tgz",
                    "modifiedContentName": false
                  },
                  "top-dir/dir4/c": {
                    "fullName": "top-dir/dir4/c",
                    "parentContentName": "top-dir/dir4.7z",
                    "modifiedContentName": false
                  },
                  "top-dir/dir4/d": {
                    "fullName": "top-dir/dir4/d",
                    "parentContentName": "top-dir/dir4.7z",
                    "modifiedContentName": false
                  }
                }""";

        assertTransformResult(resultType)
                .hasContentCount(20)
                .hasContentMatchingAt(19, actionContent -> lineageMatches(actionContent, LINEAGE_FILENAME, lineageJson));
    }

    @Test
    void recursiveOneAdditionalLevelOnly() {
        DecompressParameters params = new DecompressParameters();
        params.setMaxRecursionLevels(1);
        params.setLineageFilename(LINEAGE_FILENAME);

        ResultType resultType = runRecursiveTest(params, input("multi-layers.tgz"));

        Map<String, LineageData> lineage = new HashMap<>(BASE_LINEAGE);
        // Lineage added by 1 additional recursion:
        lineage.putAll(Map.of(
                "top-dir/a.txt", new LineageData(
                        "top-dir/a.txt", "top-dir/a.txt.gz", false),

                "top-dir/dir3/z.txt.zip", new LineageData(
                        "top-dir/dir3/z.txt.zip", "top-dir/dir3-has-two-zips.tar", false),

                "top-dir/dir3/y.txt.zip", new LineageData(
                        "top-dir/dir3/y.txt.zip", "top-dir/dir3-has-two-zips.tar", false),

                "top-dir/dir1/1", new LineageData(
                        "top-dir/dir1/1", "top-dir/dir1/three.zip", false),

                "top-dir/dir1/2", new LineageData(
                        "top-dir/dir1/2", "top-dir/dir1/three.zip", false),

                "top-dir/dir1/3", new LineageData(
                        "top-dir/dir1/3", "top-dir/dir1/three.zip", false)));

        String expectedLineage = mapToJson(lineage);

        assertTransformResult(resultType)
                .hasContentCount(8)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "top-dir/dir2/sub2/b.txt", "bbb\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "top-dir/dir3/z.txt.zip", null))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "top-dir/dir3/y.txt.zip", null))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "top-dir/dir1/1", "111\n"))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "top-dir/dir1/2", "222\n"))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "top-dir/dir1/3", "333\n"))
                .hasContentMatchingAt(6, actionContent -> contentMatches(actionContent, "top-dir/a.txt", "aaa\n"))
                .hasContentMatchingAt(7, actionContent -> lineageMatches(actionContent, LINEAGE_FILENAME, expectedLineage));
    }

    private void runTest(Format compressFormat, TransformInput input, String... outputFiles) {
        runTest(compressFormat, false, input, outputFiles);
    }

    private void runTest(Format compressFormat, boolean retainExistingContent, TransformInput input, String... outputFiles) {
        runTest(new DecompressParameters(compressFormat, retainExistingContent, null, 0), compressFormat, input, outputFiles);
    }

    private ResultType runRecursiveTest(DecompressParameters parameters, TransformInput input) {
        return action.transform(runner.actionContext(), parameters, input);
    }

    private void runTest(DecompressParameters parameters, Format expectedCompressFormat, TransformInput input, String... outputFiles) {
        ResultType result = action.transform(runner.actionContext(), parameters, input);
        List<String> names = new ArrayList<>();
        if (parameters.retainExistingContent) {
            names.addAll(input.getContent().stream().map(ActionContent::getName).toList());
        }
        names.addAll(List.of(outputFiles));

        assertTransformResult(result)
                .contentLoadBytesEquals(names.stream().map(runner::readResourceAsBytes).toList())
                .addedMetadata("compressFormat", expectedCompressFormat.getValue());
    }

    private String mapToJson(Map<String, LineageData> theMap) {
        String jsonLineage = "";
        try {
            jsonLineage = OBJECT_MAPPER.writeValueAsString(theMap);
        } catch (JsonProcessingException e) {
            jsonLineage = "ERROR";
        }
        return jsonLineage;
    }

    boolean contentMatches(ActionContent actionContent, String expectedName, String contentValue) {
        ContentAssert.assertThat(actionContent)
                .hasName(expectedName)
                .hasMediaType(CONTENT_TYPE);
        if (contentValue != null) {
            ContentAssert.assertThat(actionContent)
                    .loadStringIsEqualTo(contentValue);
        }
        return true;
    }

    boolean lineageMatches(ActionContent actionContent, String expectedName, String contentValue) {
        ContentAssert.assertThat(actionContent)
                .hasName(expectedName)
                .hasMediaType(LINEAGE_CONTENT_TYPE);

        try {
            LineageMap actualMap = new LineageMap();
            actualMap.readMapFromString(actionContent.loadString());
            assertFalse(actualMap.isEmpty());

            if (contentValue != null) {
                LineageMap expectedMap = new LineageMap();
                expectedMap.readMapFromString(contentValue);

                assertEquals(expectedMap.size(), actualMap.size());
            }
        } catch (JsonProcessingException e) {
            return false;
        }

        return true;
    }

}

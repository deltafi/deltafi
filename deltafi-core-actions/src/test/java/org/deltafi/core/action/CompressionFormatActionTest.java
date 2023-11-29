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
package org.deltafi.core.action;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.format.FormatInput;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.common.types.SaveManyContent;
import org.deltafi.core.exception.DecompressionTransformException;
import org.deltafi.core.parameters.ArchiveType;
import org.deltafi.core.parameters.CompressionFormatParameters;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

class CompressionFormatActionTest {
    private static final String MEDIA_TYPE = "application/binary";

    CompressionFormatAction action = new CompressionFormatAction();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "CompressionFormatActionTest");

    @Test
    void archiveZip() {
        test3Inputs(ArchiveType.ZIP);
    }

    @Test
    void archiveTarGzip() {
        test3Inputs(ArchiveType.TAR_GZIP);
    }

    @Test
    void archiveTarXz() {
        test3Inputs(ArchiveType.TAR_XZ);
    }

    @Test
    void archiveTar() {
        test3Inputs(ArchiveType.TAR);
    }

    @Test
    void archiveAr() {
        test3Inputs(ArchiveType.AR);
    }

    @Test
    void compressGzip() {
        test1Input(ArchiveType.GZIP);
    }

    @Test
    void compressXz() {
        test1Input(ArchiveType.XZ);
    }

    @Test
    void compressZip() {
        test1Input(ArchiveType.ZIP);
    }

    @Test
    void compressEachGzip() {
        // Expected output file created with: `gzip -nk fileA`
        compressMany(ArchiveType.GZIP);
    }

    @Test
    void compressEachXz() {
        // Expected output file created with: `xz -zk fileA`
        compressMany(ArchiveType.XZ);
    }

    @Test
    void testActionError() {
        ResultType resultType = runActionWithoutContent(MEDIA_TYPE, ArchiveType.GZIP, false);
        assertErrorResult(resultType).hasCause("No content found");
    }

    void test3Inputs(ArchiveType archiveType, String... files) {
        test3Inputs(true, archiveType, files);
        test3Inputs(false, archiveType, files);
    }

    void test3Inputs(boolean withMediaType, ArchiveType archiveType, String... files) {
        if (withMediaType) {
            testFormat(MEDIA_TYPE, archiveType, "data/fileA", "data/fileB", "data/fileC");
        } else {
            testFormat(null, archiveType, "data/fileA", "data/fileB", "data/fileC");
        }
    }

    void test1Input(ArchiveType archiveType, String... files) {
        testFormat(MEDIA_TYPE, archiveType, "data/fileA");
        testFormat(null, archiveType, "data/fileA");
    }

    void testFormat(String mediaType, ArchiveType archiveType, String... files) {
        testFormat(mediaType, true, archiveType, files);
        testFormat(mediaType, false, archiveType, files);
    }

    void testFormat(String mediaType, boolean withSuffix, ArchiveType archiveType, String... files) {
        String metadataValue = archiveType.getValue();
        String[] parts = files[0].split("/");
        String basename = parts[parts.length - 1];
        String expectedName = withSuffix ? basename + "." + metadataValue : basename;

        String expectedMediaType = (mediaType != null) ? mediaType : getMediaType(archiveType);
        ResultType resultType = runAction(mediaType, archiveType, withSuffix, files);
        List<byte[]> expectedOutput = getExpectedContent(files);
        assertFormatResult(resultType).addedMetadata("archiveType", metadataValue).formattedContentMatches(actionContent -> contentMatches(actionContent, expectedName, expectedMediaType, archiveType.getValue(), expectedOutput));
    }

    private String getMediaType(ArchiveType archiveType) {
        return switch (archiveType) {
            case AR -> "application/x-archive";
            case GZIP, TAR_GZIP -> "application/gzip";
            case TAR -> "application/x-tar";
            case TAR_XZ, XZ -> "application/x-xz";
            case ZIP -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    void compressMany(ArchiveType archiveType) {
        testCompressMany(MEDIA_TYPE, archiveType, "data/fileA", "data/fileB", "data/fileC");
        testCompressMany(null, archiveType, "data/fileA", "data/fileB"/*, "data/fileC"*/);
    }

    List<byte[]> getExpectedContent(String... files) {
        List<byte[]> bytes = new ArrayList<>();
        for (String path : List.of(files)) {
            bytes.add(runner.readResourceAsBytes(path));
        }
        return bytes;
    }

    void testCompressMany(String mediaType, ArchiveType archiveType, String... files) {
        String metadataValue = archiveType.getValue();
        String suffix = "." + metadataValue;
        List<String> fileList = List.of(files);
        int fileCount = fileList.size();

        String expectedMediaType = (mediaType != null) ? mediaType : getMediaType(archiveType);

        ResultType resultType = runAction(mediaType, archiveType, true, files);
        assertFormatManyResult(resultType)
                .hasChildrenSize(fileCount);

        for (int index = 0; index < fileCount; ++index) {
            String path = fileList.get(index);
            assertFormatManyResult(resultType)
                    .hasChildResultAt(index, child ->
                            compareChild(child, suffix, expectedMediaType, path));
        }
    }

    boolean compareChild(FormatResult child, String suffix, String mediaType, String path) {
        String[] parts = path.split("/");
        String basename = parts[parts.length - 1];
        String expectedName = basename + suffix;
        byte[] expectedBytes = runner.readResourceAsBytes(path + suffix);

        ContentAssert.assertThat(child.getContent())
                .hasName(expectedName)
                .hasMediaType(mediaType)
                .loadBytesIsEqualTo(expectedBytes);

        return true;
    }

    /**
     * Compares the actual and expected content using the original, un-archived
     * input(s) against the de-archived version of the actual (output) content.
     * This is done due to small variations in archived content, such as file
     * modification times, user/group info, etc.
     */
    boolean contentMatches(ActionContent actionContent, String expectedName, String mediaType, String archiveType, List<byte[]> expectedOutput) {
        ContentAssert.assertThat(actionContent)
                .hasName(expectedName)
                .hasMediaType(mediaType);

        List<SaveManyContent> decompressedContent = new ArrayList<>();
        String dearchiveType = decompressAutomatic(actionContent.loadInputStream(), decompressedContent);

        assertEquals(archiveType, dearchiveType);
        assertEquals(expectedOutput.size(), decompressedContent.size());
        for (int i = 0; i < expectedOutput.size(); ++i) {
            assertArrayEquals(expectedOutput.get(i), decompressedContent.get(i).content());
        }

        return true;
    }

    // Code borrowed from DecompressTransformAction
    String decompressAutomatic(@NotNull InputStream stream, List<SaveManyContent> decompressedContent) {
        String decompressionType;
        try (BufferedInputStream buffer = new BufferedInputStream(stream)) {
            try {
                String compressionType = CompressorStreamFactory.detect(buffer);
                try (BufferedInputStream decompressed = new BufferedInputStream(new CompressorStreamFactory().createCompressorInputStream(buffer))) {
                    try {
                        String archiveType = ArchiveStreamFactory.detect(decompressed);
                        try (ArchiveInputStream dearchived = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(decompressed))) {
                            unarchive(dearchived, decompressedContent);
                        } catch (IOException e) {
                            throw new DecompressionTransformException("Unable to unarchive content", e);
                        }
                        decompressionType = String.join(".", archiveType, compressionType);
                    } catch (ArchiveException e) {
                        decompressedContent.add(new SaveManyContent("name", MediaType.APPLICATION_OCTET_STREAM, decompressed.readAllBytes()));
                        decompressionType = compressionType;
                    }
                }
            } catch (CompressorException ex) {
                try {
                    String archiveType = ArchiveStreamFactory.detect(buffer);
                    try (ArchiveInputStream dearchived = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(buffer))) {
                        unarchive(dearchived, decompressedContent);
                        decompressionType = archiveType;
                    } catch (IOException e) {
                        throw new DecompressionTransformException("Unable to unarchive content", e);
                    }

                } catch (ArchiveException e) {
                    throw new DecompressionTransformException("No compression or archive formats detected");
                }
            } catch (IOException ex) {
                throw new DecompressionTransformException("Unable to decompress content", ex);
            }
        } catch (IOException ex) {
            throw new DecompressionTransformException("Unable to stream content", ex);
        }

        return decompressionType;
    }

    void unarchive(ArchiveInputStream archive, List<SaveManyContent> decompressedContent) throws IOException {
        ArchiveEntry entry;
        while ((entry = archive.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            decompressedContent.add(new SaveManyContent(entry.getName(), MediaType.APPLICATION_OCTET_STREAM, archive.readAllBytes()));
        }
    }

    ResultType runAction(String mediaType, ArchiveType dearchiveType, boolean withSuffix, String... files) {
        return action.format(runner.actionContext(),
                new CompressionFormatParameters(
                        dearchiveType, mediaType, withSuffix), input(files));
    }

    FormatInput input(String... files) {
        return FormatInput.builder().content(
                runner.saveContentFromResource(files)).build();
    }

    ResultType runActionWithoutContent(String mediaType, ArchiveType dearchiveType, boolean withSuffix) {
        return action.format(runner.actionContext(),
                new CompressionFormatParameters(
                        dearchiveType, mediaType, withSuffix),
                FormatInput.builder().build()
        );
    }

}

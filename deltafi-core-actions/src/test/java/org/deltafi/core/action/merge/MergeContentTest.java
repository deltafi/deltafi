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
package org.deltafi.core.action.merge;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.bouncycastle.util.Arrays;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.SaveManyContent;
import org.deltafi.core.action.MergeContent;
import org.deltafi.core.exception.DecompressionTransformException;
import org.deltafi.core.parameters.ArchiveType;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class MergeContentFormatActionTest {
    private final MergeContent action = new MergeContent();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "MergeContentFormatActionTest");

    private final List<byte[]> expectedArchiveContents = new ArrayList<>();

    public MergeContentFormatActionTest() {
        expectedArchiveContents.add(runner.readResourceAsBytes("thing1.txt"));
        expectedArchiveContents.add(runner.readResourceAsBytes("thing2.txt"));
    }

    @Test
    void mergesContentToBinaryConcatenation() {
        ResultType resultType = runAction(null);
        byte[] expectedOutput = Arrays.concatenate(runner.readResourceAsBytes("thing1.txt"),
                runner.readResourceAsBytes("thing2.txt"));
        assertTransformResult(resultType)
                .hasContentMatching(actionContent -> binaryContentMatches(actionContent, expectedOutput));
    }

    private boolean binaryContentMatches(ActionContent actionContent, byte[] expectedOutput) {
        ContentAssert.assertThat(actionContent).hasName("merged").hasMediaType(MediaType.APPLICATION_OCTET_STREAM);
        Assertions.assertArrayEquals(expectedOutput, actionContent.loadBytes());
        return true;
    }

    @Test
    void mergesContentToTar() {
        verifyResult(ArchiveType.TAR, runAction(ArchiveType.TAR));
    }

    @Test
    void mergesContentToZip() {
        verifyResult(ArchiveType.ZIP, runAction(ArchiveType.ZIP));
    }

    @Test
    void mergesContentToAr() {
        verifyResult(ArchiveType.AR, runAction(ArchiveType.AR));
    }

    @Test
    void mergesContentToTarGz() {
        verifyResult(ArchiveType.TAR_GZIP, runAction(ArchiveType.TAR_GZIP));
    }

    @Test
    void mergesContentToTarXz() {
        verifyResult(ArchiveType.TAR_XZ, runAction(ArchiveType.TAR_XZ));
    }

    private ResultType runAction(ArchiveType archiveType) {
        return action.transform(runner.actionContext(), new MergeContentParameters(archiveType),
                input("thing1.txt", "thing2.txt"));
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private void verifyResult(ArchiveType archiveType, ResultType resultType) {
        assertTransformResult(resultType)
                .hasContentMatching(actionContent -> contentMatches(actionContent,
                        String.join(".", "merged", archiveType.getValue()), archiveType.getMediaType(),
                        archiveType.getValue(), expectedArchiveContents));
    }

    /**
     * Compares the actual and expected content using the original, un-archived input(s) against the de-archived
     * version of the actual (output) content. This is done due to small variations in archived content, such as file
     * modification times, user/group info, etc.
     */
    private boolean contentMatches(ActionContent actionContent, String expectedName, String mediaType,
            String archiveType, List<byte[]> expectedOutput) {
        ContentAssert.assertThat(actionContent).hasName(expectedName).hasMediaType(mediaType);

        List<SaveManyContent> decompressedContent = new ArrayList<>();
        String dearchiveType = decompressAutomatic(actionContent.loadInputStream(), decompressedContent);

        Assertions.assertEquals(archiveType, dearchiveType);
        Assertions.assertEquals(expectedOutput.size(), decompressedContent.size());
        for (int i = 0; i < expectedOutput.size(); ++i) {
            Assertions.assertArrayEquals(expectedOutput.get(i), decompressedContent.get(i).content());
        }

        return true;
    }

    // Code borrowed from DecompressTransformAction
    private String decompressAutomatic(@NotNull InputStream stream, List<SaveManyContent> decompressedContent) {
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

    private void unarchive(ArchiveInputStream archive, List<SaveManyContent> decompressedContent) throws IOException {
        ArchiveEntry entry;
        while ((entry = archive.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;
            decompressedContent.add(new SaveManyContent(entry.getName(), MediaType.APPLICATION_OCTET_STREAM, archive.readAllBytes()));
        }
    }
}

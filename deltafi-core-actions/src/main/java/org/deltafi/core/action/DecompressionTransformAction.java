package org.deltafi.core.action;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.MultipartTransformAction;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.Content;
import org.deltafi.core.exception.DecompressionTransformException;
import org.deltafi.core.parameters.DecompressionTransformParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class DecompressionTransformAction extends MultipartTransformAction<DecompressionTransformParameters> {

    private static final String CONSUMES = "compressedBinary";
    private static final String PRODUCES = "binary";

    public DecompressionTransformAction() { super(DecompressionTransformParameters.class); }

    @Override
    public String getConsumes() { return CONSUMES; }

    @Override
    public String getProduces() { return PRODUCES; }

    @Override
    public Result transform(@NotNull ActionContext context, @NotNull DecompressionTransformParameters params, @NotNull SourceInfo sourceInfo, @NotNull List<Content> contentList) {

        TransformResult result = new TransformResult(context, PRODUCES);

        try(InputStream content = loadContentAsInputStream(contentList.get(0).getContentReference())) {
            try {
                switch (params.getDecompressionType()) {
                    case TAR_GZIP:
                        decompressTarGzip(content, result, context.getDid());
                        break;
                    case ZIP:
                        unarchiveZip(content, result, context.getDid());
                        break;
                    case TAR:
                        unarchiveTar(content, result, context.getDid());
                        break;
                    case GZIP:
                        decompressGzip(content, result, context.getDid(), getContentName(contentList));
                        break;
                    default:
                        return new ErrorResult(context, "Invalid decompression type: " + params.getDecompressionType());
                }
                content.close();
            } catch (DecompressionTransformException | IOException e) {
                return new ErrorResult(context, e.getMessage(), e.getCause());
            }
        } catch (ObjectStorageException | IOException e) {
            return new ErrorResult(context, "Failed to load compressed binary from storage", e);
        }
        result.addMetadata("decompressionType", params.getDecompressionType().getValue());

        return result;
    }

    @Nullable
    private String getContentName(@NotNull List<Content> contentList) {
        Content first = contentList.get(0);
        String name = null;
        if (first != null) {
            name = first.getName();
        }
        return name;
    }

    void decompressTarGzip(@NotNull InputStream stream, @NotNull TransformResult result, @NotNull String did) throws DecompressionTransformException {
        try (GzipCompressorInputStream decompressed = new GzipCompressorInputStream(stream)) {
            unarchiveTar(decompressed, result, did);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress gzip", e);
        }
    }

    void decompressGzip(@NotNull InputStream stream, @NotNull TransformResult result, @NotNull String did, String name) throws DecompressionTransformException {
        try (GzipCompressorInputStream decompressed = new GzipCompressorInputStream(stream)){
            ContentReference reference = saveContent(did, decompressed, MediaType.APPLICATION_OCTET_STREAM);
            Content content = new Content(name, Collections.emptyList(), reference);
            result.addContent(content);
        } catch (ObjectStorageException e) {
            throw new DecompressionTransformException("Unable to store content", e);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress gzip", e);
        }
    }

    void unarchive( ArchiveInputStream archive, @NotNull TransformResult result, @NotNull String did) throws IOException, ObjectStorageException {
        ArchiveEntry entry;
        while ((entry = archive.getNextEntry()) != null) {
            ContentReference reference = saveContent(did, CloseShieldInputStream.wrap(archive), MediaType.APPLICATION_OCTET_STREAM);
            Content content = new Content(entry.getName(), Collections.singletonList(new KeyValue("lastModified", entry.getLastModifiedDate().toString())), reference);
            result.addContent(content);
        }
    }

    void unarchiveTar(@NotNull InputStream stream, @NotNull TransformResult result, @NotNull String did) throws DecompressionTransformException {
        try (TarArchiveInputStream archive = new TarArchiveInputStream(stream)){
            unarchive( archive, result, did);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to unarchive tar", e);
        } catch (ObjectStorageException e) {
            throw new DecompressionTransformException("Unable to store content", e);
        }
    }

    void unarchiveZip(@NotNull InputStream stream, @NotNull TransformResult result, @NotNull String did) throws DecompressionTransformException {
        try (ZipArchiveInputStream archive = new ZipArchiveInputStream(stream)){
            unarchive(archive, result, did);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress zip", e);
        } catch (ObjectStorageException e) {
            throw new DecompressionTransformException("Unable to store content", e);
        }
    }
}

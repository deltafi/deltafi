/**
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
package org.deltafi.core.datafetchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.UniqueKeyValues;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@DgsComponent
@Slf4j
public class DeltaFilesDatafetcher {
  final DeltaFilesService deltaFilesService;
  ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  ContentStorageService contentStorageService;

  DeltaFilesDatafetcher(DeltaFilesService deltaFilesService, ContentStorageService contentStorageService) {
    this.deltaFilesService = deltaFilesService;
    this.contentStorageService = contentStorageService;
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public DeltaFile deltaFile(@InputArgument String did) {
    DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

    if (deltaFile == null) {
      throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
    }

    return deltaFile;
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public String rawDeltaFile(@InputArgument String did, @InputArgument Boolean pretty) throws JsonProcessingException {
    String deltaFileJson = deltaFilesService.getRawDeltaFile(did, pretty == null || pretty);

    if (deltaFileJson == null) {
      throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
    }

    return deltaFileJson;
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public DeltaFiles deltaFiles(DataFetchingEnvironment dfe) {
    Integer offset = dfe.getArgument("offset");
    Integer limit = dfe.getArgument("limit");
    DeltaFilesFilter filter = objectMapper.convertValue(dfe.getArgument("filter"), DeltaFilesFilter.class);
    DeltaFileOrder orderBy = objectMapper.convertValue(dfe.getArgument("orderBy"), DeltaFileOrder.class);

    List<String> rawIncludeFields = dfe.getSelectionSet().getFields().stream().filter(f -> f.getFullyQualifiedName().contains("/")).map(this::buildName).collect(Collectors.toList());
    // remove subfields -- for example if we have did, sourceInfo, and sourceInfo.flow, this should resolve to did and sourceInfo.flow
    List<String> includeFields = rawIncludeFields.stream().filter(f -> rawIncludeFields.stream().noneMatch(p -> p.startsWith(f) && !p.equals(f))).collect(Collectors.toList());

    return deltaFilesService.getDeltaFiles(offset, limit, filter, orderBy, includeFields);
  }

  String buildName(SelectedField f) {
    return Arrays.stream(f.getFullyQualifiedName().split("/")).skip(1).map(s -> s.contains(".") ? s.substring(s.lastIndexOf(".") + 1) : s).collect(Collectors.joining("."));
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<DeltaFile> lastCreated(@InputArgument Integer last) {
    return deltaFilesService.getLastCreatedDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<DeltaFile> lastModified(@InputArgument Integer last) {
    return deltaFilesService.getLastModifiedDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<DeltaFile> lastErrored(@InputArgument Integer last) {
    return deltaFilesService.getLastErroredDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public DeltaFile lastWithFilename(@InputArgument String filename) {
    return deltaFilesService.getLastWithFilename(filename);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public ErrorsByFlow errorSummaryByFlow(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument ErrorSummaryFilter filter,
          @InputArgument DeltaFileOrder orderBy) {
    return deltaFilesService.getErrorSummaryByFlow(offset, limit, filter, orderBy);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public ErrorsByMessage errorSummaryByMessage(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument ErrorSummaryFilter filter,
          @InputArgument DeltaFileOrder orderBy) {
    return deltaFilesService.getErrorSummaryByMessage(offset, limit, filter, orderBy);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileResume
  public List<RetryResult> resume(@InputArgument List<String> dids, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    return deltaFilesService.resume(dids, (removeSourceMetadata == null) ? Collections.emptyList() : removeSourceMetadata, (replaceSourceMetadata == null) ? Collections.emptyList() : replaceSourceMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileReplay
  public List<RetryResult> replay(@InputArgument List<String> dids, String replaceFilename, String replaceFlow, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    return deltaFilesService.replay(dids, replaceFilename, replaceFlow, (removeSourceMetadata == null) ? Collections.emptyList() : removeSourceMetadata, (replaceSourceMetadata == null) ? Collections.emptyList() : replaceSourceMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileAcknowledge
  public List<AcknowledgeResult> acknowledge(@InputArgument List<String> dids, @InputArgument String reason) {
    return deltaFilesService.acknowledge(dids, reason);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileCancel
  public List<CancelResult> cancel(@InputArgument List<String> dids) {
    return deltaFilesService.cancel(dids);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<UniqueKeyValues> sourceMetadataUnion(@InputArgument List<String> dids) {
    return deltaFilesService.sourceMetadataUnion(dids);
  }

  @DgsMutation
  @NeedsPermission.StressTest
  public int stressTest(@InputArgument String flow, @InputArgument Integer contentSize, @InputArgument Integer numFiles, @InputArgument Map<String, String> metadata, @InputArgument Integer batchSize) throws ObjectStorageException {
    Random random = new Random();
    SourceInfo sourceInfo = new SourceInfo("stressTestData", flow, metadata == null ? new HashMap<>() : metadata);
    Content c = new Content("stressTestContent", Collections.emptyMap(), null);

    // batches let us test quick bursts of ingress traffic, deferring ingress until after content is stored for the batch
    if (batchSize == null || batchSize < 1) {
      batchSize = 1;
    }
    int remainingFiles = numFiles;

    while (remainingFiles > 0) {
      List<ContentReference> crs = new ArrayList<>();
      for (int i = 0; i < Math.min(remainingFiles, batchSize); i++) {
        if (contentSize > 0) {
          String did = UUID.randomUUID().toString();
          log.debug("Saving content for {} ({}/{})", did, i + (numFiles - remainingFiles) + 1, numFiles);
          byte[] contentBytes = new byte[contentSize];
          random.nextBytes(contentBytes);
          crs.add(contentStorageService.save(did, contentBytes, "application/octet-stream"));
        } else {
          crs.add(new ContentReference("application/octet-stream", Collections.emptyList()));
        }
      }

      for (int i = 0; i < Math.min(remainingFiles, batchSize); i++) {
        ContentReference cr = crs.get(i);
        c.setContentReference(cr);
        String did = cr.getSegments().isEmpty() ? UUID.randomUUID().toString() : cr.getSegments().get(0).getDid();
        IngressEvent ingressEvent = new IngressEvent(did, sourceInfo, List.of(c), OffsetDateTime.now());
        log.debug("Ingressing metadata for {} ({}/{})", did, i + (numFiles - remainingFiles) + 1, numFiles);
        deltaFilesService.ingress(ingressEvent);
      }

      remainingFiles -= batchSize;
    }

    return numFiles;
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<String> domains() {
    return deltaFilesService.domains();
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<String> indexedMetadataKeys(@InputArgument String domain) {
    return deltaFilesService.indexedMetadataKeys(domain);
  }
}
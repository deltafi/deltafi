/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.UniqueKeyValues;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.services.DeltaFilesService;

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
  @SuppressWarnings("unused")
  public DeltaFile deltaFile(@InputArgument String did) {
    DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

    if (deltaFile == null) {
      throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
    }

    return deltaFile;
  }

  @DgsQuery
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
  public List<DeltaFile> lastCreated(@InputArgument Integer last) {
    return deltaFilesService.getLastCreatedDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public List<DeltaFile> lastModified(@InputArgument Integer last) {
    return deltaFilesService.getLastModifiedDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public List<DeltaFile> lastErrored(@InputArgument Integer last) {
    return deltaFilesService.getLastErroredDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public DeltaFile lastWithFilename(@InputArgument String filename) {
    return deltaFilesService.getLastWithFilename(filename);
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public ErrorsByFlow errorSummaryByFlow(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument ErrorSummaryFilter filter,
          @InputArgument DeltaFileOrder orderBy) {
    return deltaFilesService.getErrorSummaryByFlow(offset, limit, filter, orderBy);
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public ErrorsByMessage errorSummaryByMessage(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument ErrorSummaryFilter filter,
          @InputArgument DeltaFileOrder orderBy) {
    return deltaFilesService.getErrorSummaryByMessage(offset, limit, filter, orderBy);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public DeltaFile ingress(@InputArgument IngressInput input) {
    return deltaFilesService.ingress(input);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public DeltaFile actionEvent(@InputArgument ActionEventInput event) throws JsonProcessingException {
    return deltaFilesService.handleActionEvent(event);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public List<RetryResult> resume(@InputArgument List<String> dids, String replaceFilename, String replaceFlow, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    return deltaFilesService.resume(dids, replaceFilename, replaceFlow, (removeSourceMetadata == null) ? Collections.emptyList() : removeSourceMetadata, (replaceSourceMetadata == null) ? Collections.emptyList() : replaceSourceMetadata);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public List<RetryResult> retry(@InputArgument List<String> dids, String replaceFilename, String replaceFlow, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    return resume(dids, replaceFilename, replaceFlow, removeSourceMetadata, replaceSourceMetadata);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public List<RetryResult> replay(@InputArgument List<String> dids, String replaceFilename, String replaceFlow, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    return deltaFilesService.replay(dids, replaceFilename, replaceFlow, (removeSourceMetadata == null) ? Collections.emptyList() : removeSourceMetadata, (replaceSourceMetadata == null) ? Collections.emptyList() : replaceSourceMetadata);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public List<AcknowledgeResult> acknowledge(@InputArgument List<String> dids, @InputArgument String reason) {
    return deltaFilesService.acknowledge(dids, reason);
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public List<UniqueKeyValues> sourceMetadataUnion(@InputArgument List<String> dids) {
    return deltaFilesService.sourceMetadataUnion(dids);
  }

  @DgsMutation
  public int stressTest(@InputArgument String flow, @InputArgument Integer contentSize, @InputArgument Integer numFiles, @InputArgument List<KeyValue> metadata) throws ObjectStorageException {
    Random random = new Random();
    SourceInfo sourceInfo = new SourceInfo("stressTestData", flow, metadata == null ? new ArrayList<>() : metadata);
    Content c = new Content("stressTestContent", Collections.emptyList(), null);
    List<ContentReference> crs = new ArrayList<>();

    for (int i = 0; i < numFiles; i++) {
      String did = UUID.randomUUID().toString();
      log.info("Saving content for {} ({}/{})", did, i+1, numFiles);
      byte[] contentBytes = new byte[contentSize];
      random.nextBytes(contentBytes);
      crs.add(contentStorageService.save(did, contentBytes, "application/octet-stream"));
    }
    for (ContentReference cr : crs) {
      c.setContentReference(cr);
      IngressInput ingressInput = new IngressInput(cr.getDid(), sourceInfo, List.of(c), OffsetDateTime.now());
      log.info("Ingressing " + cr.getDid());
      ingress(ingressInput);
    }

    return numFiles;
  }

  @DgsQuery
  public List<String> domains() {
    return deltaFilesService.domains();
  }

  @DgsQuery
  public List<String> indexedMetadataKeys(@InputArgument String domain) {
    return deltaFilesService.indexedMetadataKeys(domain);
  }
}

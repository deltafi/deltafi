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
package org.deltafi.core.datafetchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.uuid.Generators;
import com.netflix.graphql.dgs.*;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.RestDataSourceService;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.types.*;
import org.deltafi.core.types.ResumePolicy;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@DgsComponent
@Slf4j
public class DeltaFilesDatafetcher {
  final DeltaFilesService deltaFilesService;
  final ContentStorageService contentStorageService;
  final RestDataSourceService restDataSourceService;
  private final CoreAuditLogger auditLogger;

  static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  DeltaFilesDatafetcher(DeltaFilesService deltaFilesService, ContentStorageService contentStorageService, RestDataSourceService restDataSourceService, CoreAuditLogger auditLogger) {
    this.deltaFilesService = deltaFilesService;
    this.contentStorageService = contentStorageService;
    this.restDataSourceService = restDataSourceService;
    this.auditLogger = auditLogger;
  }

  @DgsData(parentType = "DeltaFile", field = "annotations")
  public Object getAnnotations(DgsDataFetchingEnvironment dfe) {
    DeltaFile deltaFile = dfe.getSource();
    if (deltaFile.getAnnotations() == null) {
      return Collections.emptyMap();
    }
    return deltaFile.getAnnotations().stream()
            .collect(Collectors.toMap(Annotation::getKey, Annotation::getValue));
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public DeltaFile deltaFile(@InputArgument UUID did) {
    DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

    if (deltaFile == null) {
      throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
    }

    return deltaFile;
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public String rawDeltaFile(@InputArgument UUID did, @InputArgument Boolean pretty) throws JsonProcessingException {
    String deltaFileJson = deltaFilesService.getRawDeltaFile(did, pretty == null || pretty);

    if (deltaFileJson == null) {
      throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
    }

    return deltaFileJson;
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public Collection<String> pendingAnnotations(@InputArgument UUID did) {
    return deltaFilesService.getPendingAnnotations(did);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public DeltaFiles deltaFiles(DataFetchingEnvironment dfe, @InputArgument Integer offset, @InputArgument Integer limit) {
    DeltaFilesFilter filter = objectMapper.convertValue(dfe.getArgument("filter"), DeltaFilesFilter.class);
    DeltaFileOrder orderBy = objectMapper.convertValue(dfe.getArgument("orderBy"), DeltaFileOrder.class);

    List<String> rawIncludeFields = dfe.getSelectionSet().getFields().stream().filter(f -> f.getFullyQualifiedName().contains("/")).map(this::buildName).toList();

    // remove subfields -- for example if we have did, sourceInfo, and sourceInfo.flow, this should resolve to did and sourceInfo.flow
    List<String> includeFields = rawIncludeFields.stream().filter(f -> rawIncludeFields.stream().noneMatch(p -> p.startsWith(f + ".") && !p.equals(f))).toList();
    return deltaFilesService.deltaFiles(offset, limit, filter, orderBy, includeFields);
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
  public DeltaFile lastWithName(@InputArgument String name) {
    return deltaFilesService.getLastWithName(name);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public SummaryByFlow errorSummaryByFlow(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument ErrorSummaryFilter filter,
          @InputArgument DeltaFileDirection direction) {
    return deltaFilesService.getErrorSummaryByFlow(offset, limit, filter, direction);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public SummaryByFlowAndMessage errorSummaryByMessage(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument ErrorSummaryFilter filter,
          @InputArgument DeltaFileDirection direction) {
    return deltaFilesService.getErrorSummaryByMessage(offset, limit, filter, direction);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public SummaryByFlow filteredSummaryByFlow(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument FilteredSummaryFilter filter,
          @InputArgument DeltaFileDirection direction) {
    return deltaFilesService.getFilteredSummaryByFlow(offset, limit, filter, direction);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public SummaryByFlowAndMessage filteredSummaryByMessage(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument FilteredSummaryFilter filter,
          @InputArgument DeltaFileDirection direction) {
    return deltaFilesService.getFilteredSummaryByMessage(offset, limit, filter, direction);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileResume
  public List<RetryResult> resume(@InputArgument List<UUID> dids, @InputArgument List<ResumeMetadata> resumeMetadata) {
    auditLogger.audit("resumed {} deltaFiles", dids.size());
    return deltaFilesService.resume(dids, (resumeMetadata == null) ? Collections.emptyList() : resumeMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileReplay
  public List<RetryResult> replay(@InputArgument List<UUID> dids, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    auditLogger.audit("replayed {} deltaFiles", dids.size());
    return deltaFilesService.replay(dids, (removeSourceMetadata == null) ? Collections.emptyList() : removeSourceMetadata, (replaceSourceMetadata == null) ? Collections.emptyList() : replaceSourceMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileAcknowledge
  public List<AcknowledgeResult> acknowledge(@InputArgument List<UUID> dids, @InputArgument String reason) {
    auditLogger.audit("acknowledged {} deltaFiles", dids.size());
    return deltaFilesService.acknowledge(dids, reason);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileCancel
  public List<CancelResult> cancel(@InputArgument List<UUID> dids) {
    auditLogger.audit("canceled {} deltaFiles", dids.size());
    return deltaFilesService.cancel(dids);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileMetadataWrite
  public boolean addAnnotations(UUID did, List<KeyValue> annotations, boolean allowOverwrites) {
    auditLogger.audit("annotated deltafi with did {} with {}", did, CoreAuditLogger.listToString(annotations));
    deltaFilesService.addAnnotations(did, KeyValueConverter.convertKeyValues(annotations), allowOverwrites);
    return true;
  }

  @DgsMutation
  @NeedsPermission.ResumePolicyApply
  public Result applyResumePolicies(@InputArgument List<String> names) {
    auditLogger.audit("applied resume policies: {}", String.join(", ", names));
    return deltaFilesService.applyResumePolicies(names);
  }

  @DgsQuery
  @NeedsPermission.ResumePolicyDryRun
  public Result resumePolicyDryRun(@InputArgument ResumePolicyInput resumePolicyInput) {
    ResumePolicy resumePolicy = objectMapper.convertValue(resumePolicyInput, ResumePolicy.class);
    return deltaFilesService.resumePolicyDryRun(resumePolicy);
  }

  @DgsMutation
  @NeedsPermission.Admin
  public boolean taskTimedDataSource(@InputArgument String name, @InputArgument String memo, DataFetchingEnvironment dataFetchingEnvironment) {
    boolean useMemo = dataFetchingEnvironment.containsArgument("memo");
    auditLogger.audit("tasked timed data source {} with memo {} and memo override {}", name, memo, useMemo);
    // check if the memo argument was included to differentiate between setting it to null and leaving it out
    return deltaFilesService.taskTimedDataSource(name, memo, useMemo);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<PerActionUniqueKeyValues> errorMetadataUnion(@InputArgument List<UUID> dids) {
    return deltaFilesService.errorMetadataUnion(dids);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<UniqueKeyValues> sourceMetadataUnion(@InputArgument List<UUID> dids) {
    return deltaFilesService.sourceMetadataUnion(dids);
  }
  @DgsMutation
  @NeedsPermission.StressTest
  public int stressTest(@InputArgument String flow, @InputArgument Integer contentSize, @InputArgument Integer numFiles, @InputArgument Map<String, String> metadata, @InputArgument Integer batchSize) throws ObjectStorageException, IngressException {
    auditLogger.audit("started stress test for flow {} using contentSize {}, numFiles {}, batchSize {}", flow, contentSize, numFiles, batchSize);
    RestDataSource restDataSource;
    try {
      restDataSource = restDataSourceService.getFlowOrThrow(flow);
    } catch (Exception e) {
      throw new IngressException(e.getMessage());
    }

    Random random = new Random();

    // batches let us test quick bursts of ingress traffic, deferring ingress until after content is stored for the batch
    if (batchSize == null || batchSize < 1) {
      batchSize = 1;
    }
    int remainingFiles = numFiles;

    while (remainingFiles > 0) {
      List<Content> contentList = new ArrayList<>();
      for (int i = 0; i < Math.min(remainingFiles, batchSize); i++) {
        if (contentSize > 0) {
          UUID did = Generators.timeBasedEpochGenerator().generate();
          log.debug("Saving content for {} ({}/{})", did, i + (numFiles - remainingFiles) + 1, numFiles);
          byte[] contentBytes = new byte[contentSize];
          random.nextBytes(contentBytes);
          contentList.add(contentStorageService.save(did, contentBytes, "stressTestData", "application/octet-stream"));
        } else {
          contentList.add(new Content("stressTestData", "application/octet-stream", Collections.emptyList()));
        }
      }

      for (int i = 0; i < Math.min(remainingFiles, batchSize); i++) {
        Content c = contentList.get(i);
        UUID did = c.getSegments().isEmpty() ? UUID.randomUUID() : c.getSegments().getFirst().getDid();
        IngressEventItem ingressEventItem = new IngressEventItem(did, "stressTestData", flow,
                metadata == null ? new HashMap<>() : metadata,
                List.of(c));
        log.debug("Ingressing metadata for {} ({}/{})", did, i + (numFiles - remainingFiles) + 1, numFiles);
        deltaFilesService.ingress(restDataSource, ingressEventItem, OffsetDateTime.now(), OffsetDateTime.now());
      }

      remainingFiles -= batchSize;
    }

    return numFiles;
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public List<String> annotationKeys() {
    return deltaFilesService.annotationKeys();
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public Long totalCount() {
    return deltaFilesService.totalCount();
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public Long countUnacknowledgedErrors() {
    return deltaFilesService.countUnacknowledgedErrors();
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public DeltaFileStats deltaFileStats() {
    return deltaFilesService.deltaFileStats();
  }
}

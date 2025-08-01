/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.FlowDefinitionService;
import org.deltafi.core.services.RestDataSourceService;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.*;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class DeltaFilesDatafetcher {
  final DeltaFilesService deltaFilesService;
  final ContentStorageService contentStorageService;
  final RestDataSourceService restDataSourceService;
  final AnalyticEventService analyticEventService;
  private final CoreAuditLogger auditLogger;

  static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final FlowDefinitionService flowDefinitionService;

  @DgsData(parentType = "DeltaFile", field = "annotations")
  public Object getAnnotations(DgsDataFetchingEnvironment dfe) {
    DeltaFile deltaFile = dfe.getSource();
    if (deltaFile == null || deltaFile.getAnnotations() == null) {
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

    // remove subfields -- for example if we have did, sourceInfo, and sourceInfo.dataSource, this should resolve to did and sourceInfo.dataSource
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
          @InputArgument DeltaFileDirection direction,
          @InputArgument SummaryByFlowSort sortField) {
    return deltaFilesService.getErrorSummaryByFlow(offset, limit, filter, direction, sortField);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public SummaryByFlowAndMessage errorSummaryByMessage(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument ErrorSummaryFilter filter,
          @InputArgument DeltaFileDirection direction,
          @InputArgument SummaryByMessageSort sortField) {
    return deltaFilesService.getErrorSummaryByMessage(offset, limit, filter, direction, sortField);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public SummaryByFlow filteredSummaryByFlow(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument FilteredSummaryFilter filter,
          @InputArgument DeltaFileDirection direction,
          @InputArgument SummaryByFlowSort sortField) {
    return deltaFilesService.getFilteredSummaryByFlow(offset, limit, filter, direction, sortField);
  }

  @DgsQuery
  @NeedsPermission.DeltaFileMetadataView
  public SummaryByFlowAndMessage filteredSummaryByMessage(
          @InputArgument Integer offset,
          @InputArgument Integer limit,
          @InputArgument FilteredSummaryFilter filter,
          @InputArgument DeltaFileDirection direction,
          @InputArgument SummaryByMessageSort sortField) {
    return deltaFilesService.getFilteredSummaryByMessage(offset, limit, filter, direction, sortField);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileResume
  public List<RetryResult> resume(@InputArgument List<UUID> dids, @InputArgument List<ResumeMetadata> resumeMetadata) {
    auditLogger.audit("resumed {} deltaFiles", dids.size());
    return deltaFilesService.resume(dids, resumeMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileResume
  public List<RetryResult> resumeMatching(@InputArgument DeltaFilesFilter filter, @InputArgument List<ResumeMetadata> resumeMetadata) {
    auditLogger.audit("resumed deltaFiles by filter");
    return deltaFilesService.resume(filter, resumeMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileReplay
  public List<RetryResult> replayMatching(@InputArgument DeltaFilesFilter filter, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    auditLogger.audit("replayed deltaFiles by filter");
    return deltaFilesService.replay(filter, removeSourceMetadata, replaceSourceMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileReplay
  public List<RetryResult> replay(@InputArgument List<UUID> dids, @InputArgument List<String> removeSourceMetadata, @InputArgument List<KeyValue> replaceSourceMetadata) {
    auditLogger.audit("replayed {} deltaFiles", dids.size());
    return deltaFilesService.replay(dids, removeSourceMetadata, replaceSourceMetadata);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileAcknowledge
  public List<AcknowledgeResult> acknowledge(@InputArgument List<UUID> dids, @InputArgument String reason) {
    auditLogger.audit("acknowledged {} deltaFiles", dids.size());
    return deltaFilesService.acknowledge(dids, reason);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileAcknowledge
  public List<AcknowledgeResult> acknowledgeMatching(@InputArgument DeltaFilesFilter filter, @InputArgument String reason) {
    auditLogger.audit("acknowledging deltaFiles by filter");
    return deltaFilesService.acknowledge(filter, reason);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileCancel
  public List<CancelResult> cancel(@InputArgument List<UUID> dids) {
    auditLogger.audit("canceled {} deltaFiles", dids.size());
    return deltaFilesService.cancel(dids);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileCancel
  public List<CancelResult> cancelMatching(@InputArgument DeltaFilesFilter filter) {
    auditLogger.audit("canceling deltaFiles by filter");
    return deltaFilesService.cancel(filter);
  }

  @DgsMutation
  @NeedsPermission.DeltaFilePinning
  public List<Result> pin(@InputArgument List<UUID> dids) {
    auditLogger.audit("pinning deltafiles with dids {}", CoreAuditLogger.listToString(dids));
    return deltaFilesService.pin(dids);
  }

  @DgsMutation
  @NeedsPermission.DeltaFilePinning
  public List<Result> unpin(@InputArgument List<UUID> dids) {
    auditLogger.audit("unpinning deltafiles with dids {}", CoreAuditLogger.listToString(dids));
    return deltaFilesService.unpin(dids);
  }

  @DgsMutation
  @NeedsPermission.DeltaFilePinning
  public List<PinResult> pinMatching(@InputArgument DeltaFilesFilter filter) {
    auditLogger.audit("pinning deltaFiles by filter");
    return deltaFilesService.setPinned(filter, true);
  }

  @DgsMutation
  @NeedsPermission.DeltaFilePinning
  public List<PinResult> unpinMatching(@InputArgument DeltaFilesFilter filter) {
    auditLogger.audit("unpinning deltaFiles by filter");
    return deltaFilesService.setPinned(filter, false);
  }

  @DgsMutation
  @NeedsPermission.DeltaFileMetadataWrite
  public boolean addAnnotations(UUID did, List<KeyValue> annotations, boolean allowOverwrites) {
    auditLogger.audit("annotated deltafile with did {} with {}", did, CoreAuditLogger.listToString(annotations));
    deltaFilesService.addAnnotations(did, KeyValueConverter.convertKeyValues(annotations), allowOverwrites);
    return true;
  }

  @DgsMutation
  @NeedsPermission.DeltaFileMetadataWrite
  public boolean annotateMatching(@InputArgument DeltaFilesFilter filter, List<KeyValue> annotations, boolean allowOverwrites) {
    auditLogger.audit("annotated deltaFiles by filter with {}", CoreAuditLogger.listToString(annotations));
    deltaFilesService.annotateMatching(filter, KeyValueConverter.convertKeyValues(annotations), allowOverwrites);
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
  @NeedsPermission.DeltaFileResume
  public List<RetryResult> resumeByFlow(@InputArgument FlowType flowType, @InputArgument String name, @InputArgument List<ResumeMetadata> resumeMetadata, @InputArgument Boolean includeAcknowledged) {
    return deltaFilesService.resumeByFlowTypeAndName(flowType, name, resumeMetadata, Boolean.TRUE.equals(includeAcknowledged));
  }

  @DgsMutation
  @NeedsPermission.DeltaFileResume
  public List<RetryResult> resumeByErrorCause(@InputArgument String errorCause, @InputArgument List<ResumeMetadata> resumeMetadata, @InputArgument Boolean includeAcknowledged) {
    return deltaFilesService.resumeByErrorCause(errorCause, resumeMetadata, Boolean.TRUE.equals(includeAcknowledged));
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
    auditLogger.audit("started stress test for dataSource {} using contentSize {}, numFiles {}, batchSize {}", flow, contentSize, numFiles, batchSize);
    RestDataSource restDataSource;
    try {
      restDataSource = restDataSourceService.getFlowOrThrow(flow);
    } catch (Exception e) {
      throw new IngressException(e.getMessage());
    }

    // Use a local variable for batchSize after validation
    int currentBatchSizeSetting = (batchSize == null || batchSize < 1) ? 1 : batchSize;

    if (numFiles == null || numFiles <= 0) {
      log.info("numFiles is {} (<=0), exiting stress test.", numFiles);
      return 0;
    }

    int remainingFiles = numFiles;
    int totalFilesProcessedOffset = 0;

    ExecutorService executorService = Executors.newFixedThreadPool(8);

    try {
      while (remainingFiles > 0) {
        int actualBatchProcessingCount = Math.min(remainingFiles, currentBatchSizeSetting);
        List<Content> savedContentForBatch = new ArrayList<>(actualBatchProcessingCount);

        // Phase 1: Save content in parallel for the current batch
        List<Callable<Content>> saveTasks = new ArrayList<>();
        for (int i = 0; i < actualBatchProcessingCount; i++) {
          final int fileIndexInTotal = totalFilesProcessedOffset + i; // 0-based global index
          saveTasks.add(() -> {
            ThreadLocalRandom currentThreadRandom = ThreadLocalRandom.current();
            if (contentSize > 0) {
              UUID did = Generators.timeBasedEpochGenerator().generate();
              log.debug("Saving content for {} ({}/{})", did, fileIndexInTotal + 1, numFiles);
              byte[] contentBytes = new byte[contentSize];
              currentThreadRandom.nextBytes(contentBytes);
              return contentStorageService.save(did, contentBytes, "stressTestData", "application/octet-stream");
            } else {
              // Create dummy content (no actual bytes) if contentSize is 0 or less
              return new Content("stressTestData", "application/octet-stream", Collections.emptyList());
            }
          });
        }

        try {
          List<Future<Content>> saveFutures = executorService.invokeAll(saveTasks);
          for (Future<Content> future : saveFutures) {
            savedContentForBatch.add(future.get());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IngressException("Stress test interrupted during content saving: " + e.getMessage(), e);
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          if (cause instanceof ObjectStorageException) throw (ObjectStorageException) cause;
          if (cause instanceof IngressException) throw (IngressException) cause;
          if (cause instanceof RuntimeException) throw (RuntimeException) cause;
          if (cause instanceof Error) throw (Error) cause;
          throw new ObjectStorageException("Error during parallel content saving: " + cause.getMessage(), cause);
        }

        // Phase 2: Ingress metadata in parallel for the current batch
        List<Callable<Void>> ingressTasks = new ArrayList<>();
        for (int i = 0; i < actualBatchProcessingCount; i++) {
          final Content contentItem = savedContentForBatch.get(i);
          final int fileIndexInTotal = totalFilesProcessedOffset + i;
          ingressTasks.add(() -> {
            // Use existing DID from content, or generate a new random one if segments are empty
            UUID did = contentItem.getSegments().isEmpty() ? UUID.randomUUID() : contentItem.getSegments().getFirst().getDid();

            // Create a new metadata map for each task to ensure thread-safety
            Map<String, String> taskSpecificMetadata = (metadata == null) ? new HashMap<>() : new HashMap<>(metadata);

            IngressEventItem ingressEventItem = new IngressEventItem(did, "stressTestData", flow,
                    taskSpecificMetadata,
                    List.of(contentItem),
                    Map.of());

            log.debug("Ingressing metadata for {} ({}/{})", did, fileIndexInTotal + 1, numFiles);
            deltaFilesService.ingressRest(restDataSource, ingressEventItem, OffsetDateTime.now(), OffsetDateTime.now());
            return null;
          });
        }

        try {
          List<Future<Void>> ingressFutures = executorService.invokeAll(ingressTasks);
          for (Future<Void> future : ingressFutures) {
            future.get();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IngressException("Stress test interrupted during metadata ingesting: " + e.getMessage(), e);
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          if (cause instanceof IngressException) throw (IngressException) cause;
          if (cause instanceof ObjectStorageException) throw (ObjectStorageException) cause;
          if (cause instanceof RuntimeException) throw (RuntimeException) cause;
          if (cause instanceof Error) throw (Error) cause;
          throw new IngressException("Error during parallel metadata ingesting: " + cause.getMessage(), cause);
        }

        remainingFiles -= actualBatchProcessingCount;
        totalFilesProcessedOffset += actualBatchProcessingCount;
      }
    } finally {
      log.debug("Shutting down executor service...");
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
          log.warn("Executor service did not terminate in 60 seconds. Forcing shutdown...");
          executorService.shutdownNow();
          if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            log.error("Executor service did not terminate even after forced shutdown.");
          }
        }
      } catch (InterruptedException e) {
        log.warn("Shutdown of executor service was interrupted. Forcing shutdown now.");
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
      log.debug("Executor service shutdown complete.");
    }

    return numFiles;
  }

  @DgsMutation
  @NeedsPermission.StressTest
  public int stressTestAnalyticEvents(@InputArgument Integer numRecords, @InputArgument Integer hours) {
    auditLogger.audit("started analytics events stress test for {} records over {} hours", numRecords, hours);

    Random random = new Random();

    OffsetDateTime endTime = OffsetDateTime.now();
    OffsetDateTime startTime = endTime.minusHours(hours);
    long timeRangeSeconds = ChronoUnit.SECONDS.between(startTime, endTime);

    String[] flows = {"flow1", "flow2", "flow3", "flow4"};
    String[] actions = {"LoadAction", "NormalizeAction", "EnrichAction", "ValidateAction"};
    String[] dataSources = {"stressTestAIn", "stressTestBIn", "stressTestCIn", "stressTestDIn"};
    String[] dataSinks = {"stressTestAOut", "stressTestBOut", "stressTestCOut", "stressTestDOut"};

    String[][] annotationValues = {
            {"csv", "json", "xml", "binary"},
            {"raw", "normalized", "enriched"},
            {"high", "medium", "low"},
            {"system1", "system2", "system3"},
            {"valid", "invalid", "partial"},
            {"batch", "streaming", "interactive"}
    };
    String[] annotationKeys = {"dataType", "format", "priority", "source", "status", "processingType"};

    // Reusable objects
    Map<String, String> annotations = new HashMap<>();
    DeltaFile deltaFile = new DeltaFile();
    Content content = new Content("d", "x", new ArrayList<>());
    Segment segment = new Segment();
    Action egressAction = new Action();
    egressAction.setState(ActionState.COMPLETE);
    egressAction.setContent(List.of(content));
    DeltaFileFlow egressFlow = new DeltaFileFlow();
    egressFlow.setActions(List.of(egressAction));

    for (int i = 0; i < numRecords; i++) {
      OffsetDateTime baseTimestamp = startTime.plusSeconds(random.nextLong(timeRangeSeconds));

      annotations.clear();
      int numAnnotations = 3 + random.nextInt(2);
      for (int j = 0; j < numAnnotations; j++) {
        int keyIndex = random.nextInt(6);
        annotations.put(annotationKeys[keyIndex],
                annotationValues[keyIndex][random.nextInt(annotationValues[keyIndex].length)]);
      }

      // Setup base objects
      UUID did = UUID.randomUUID();
      String dataSource = dataSources[random.nextInt(dataSources.length)];
      String flow = flows[random.nextInt(flows.length)];

      deltaFile.setDid(did);
      deltaFile.setDataSource(dataSource);
      deltaFile.addAnnotations(annotations);
      DeltaFileFlow firstFlow = DeltaFileFlow.builder()
              .flowDefinition(flowDefinitionService.getOrCreateFlow(dataSource, FlowType.REST_DATA_SOURCE))
              .build();
      deltaFile.getFlows().add(firstFlow);

      // Record ingress
      long ingressBytes = 1024L + random.nextLong(10 * 1024 * 1024);
      analyticEventService.recordIngress(did, baseTimestamp, dataSource, FlowType.REST_DATA_SOURCE, ingressBytes, annotations, AnalyticIngressTypeEnum.DATA_SOURCE);

      // Most files should have 1-3 result paths
      int numBranches = random.nextInt(100) < 80 ? 1 : 2 + random.nextInt(2); // 80% single branch, 20% 2-3 branches

      for (int branch = 0; branch < numBranches; branch++) {
        OffsetDateTime branchTime = baseTimestamp.plusSeconds(random.nextInt(60));
        deltaFile.setModified(branchTime);

        // 90% success path (egress), 5% error, 4% filter, 1% cancel
        int outcome = random.nextInt(100);

        if (outcome < 90) {
          // Egress
          segment.setDid(did);
          segment.setUuid(did);
          segment.setSize(Math.max(1024L, ingressBytes - random.nextLong(ingressBytes / 2)));
          content.setSegments(List.of(segment));

          egressAction.setModified(branchTime);
          egressFlow.setModified(branchTime);
          String dataSink = dataSinks[random.nextInt(dataSinks.length)];
          egressFlow.setFlowDefinition(flowDefinitionService.getOrCreateFlow(dataSink, FlowType.DATA_SINK));

          analyticEventService.recordEgress(deltaFile, egressFlow);
        } else if (outcome < 95) {
          // Error
          String actionName = actions[random.nextInt(actions.length)];

          analyticEventService.recordError(
                  deltaFile,
                  flow,
                  FlowType.TRANSFORM,
                  actionName,
                  "Test error: Invalid data format in field " + random.nextInt(10),
                  branchTime);
        } else if (outcome < 99) {
          // Filter
          String actionName = actions[random.nextInt(actions.length)];
          analyticEventService.recordFilter(
                  deltaFile,
                  flow,
                  FlowType.TRANSFORM,
                  actionName,
                  "Filtered by " + actionName,
                  branchTime);
        } else {
          // Cancel
          analyticEventService.recordCancel(deltaFile);
        }
      }
    }

    return numRecords;
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

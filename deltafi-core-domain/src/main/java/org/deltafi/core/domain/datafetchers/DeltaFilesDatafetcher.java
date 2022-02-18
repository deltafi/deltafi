package org.deltafi.core.domain.datafetchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.DeltaFiles;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.services.DeltaFilesService;

import java.util.List;
import java.util.Objects;

@DgsComponent
public class DeltaFilesDatafetcher {
  final DeltaFilesService deltaFilesService;

  DeltaFilesDatafetcher(DeltaFilesService deltaFilesService) {
    this.deltaFilesService = deltaFilesService;
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
  public DeltaFiles deltaFiles(@InputArgument Integer offset, @InputArgument Integer limit, @InputArgument DeltaFilesFilter filter, @InputArgument DeltaFileOrder orderBy) {
    return deltaFilesService.getDeltaFiles(offset, limit, filter, orderBy);
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
  public List<RetryResult> retry(@InputArgument List<String> dids) {
    return deltaFilesService.retry(dids);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public List<AcknowledgeResult> acknowledge(@InputArgument List<String> dids, @InputArgument String reason) {
    return deltaFilesService.acknowledge(dids, reason);
  }
}
package org.deltafi.dgs.datafetchers;

import com.netflix.graphql.dgs.*;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.services.DeltaFilesService;

import java.util.List;
import java.util.Objects;

@DgsComponent
public class DeltaFilesDatafetcher {
  final DeltaFiProperties deltaFiProperties;
  final DeltaFilesService deltaFilesService;

  DeltaFilesDatafetcher(DeltaFiProperties deltaFiProperties, DeltaFilesService deltaFilesService) {
    this.deltaFiProperties = deltaFiProperties;
    this.deltaFilesService = deltaFilesService;
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public DeltaFile deltaFile(String did) {
    DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

    if (deltaFile == null) {
      throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
    }

    return deltaFile;
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public List<DeltaFile> lastCreated(Integer last) {
    return deltaFilesService.getLastCreatedDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public List<DeltaFile> lastModified(Integer last) {
    return deltaFilesService.getLastModifiedDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public List<DeltaFile> lastErrored(Integer last) {
    return deltaFilesService.getLastErroredDeltaFiles(Objects.requireNonNullElse(last, 10));
  }

  @DgsQuery
  @SuppressWarnings("unused")
  public DeltaFile lastWithFilename(String filename) {
    return deltaFilesService.getLastWithFilename(filename);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public DeltaFile actionEvent(ActionEventInput event) {
    return deltaFilesService.handleActionEvent(event);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public DeltaFile filter(String did, String fromAction, String message) {
    return deltaFilesService.filter(did, fromAction, message);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public DeltaFile error(ErrorInput error) {
    return deltaFilesService.error(error);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public DeltaFile retry(String did) {
    return deltaFilesService.retry(did);
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public List<String> delete(List<String> dids) {
    deltaFilesService.delete(dids);
    return dids;
  }
}

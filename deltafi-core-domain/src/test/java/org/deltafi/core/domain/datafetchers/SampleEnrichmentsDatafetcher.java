package org.deltafi.core.domain.datafetchers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import org.deltafi.core.domain.generated.types.SampleEnrichment;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.services.SampleEnrichmentsService;

@DgsComponent
public class SampleEnrichmentsDatafetcher {
  final SampleEnrichmentsService sampleEnrichmentsService;

  @SuppressWarnings("CdiInjectionPointsInspection")
  SampleEnrichmentsDatafetcher(SampleEnrichmentsService sampleEnrichmentsService) {
    this.sampleEnrichmentsService = sampleEnrichmentsService;
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public DeltaFile addSampleEnrichment(String did, Boolean enriched) throws JsonProcessingException {
    SampleEnrichment sampleEnrichment = SampleEnrichment.newBuilder().enriched(enriched).build();
    return sampleEnrichmentsService.addSampleEnrichment(did, sampleEnrichment);
  }
}
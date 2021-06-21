package org.deltafi.dgs.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import graphql.schema.DataFetchingEnvironment;
import org.deltafi.dgs.api.annotation.DgsDeltaFiEnrichment;
import org.deltafi.dgs.api.datafetchers.EnrichmentsDatafetcher;
import org.deltafi.dgs.generated.types.DeltaFiEnrichments;
import org.deltafi.dgs.generated.types.SampleEnrichment;
import org.deltafi.dgs.services.SampleEnrichmentsService;

@DgsComponent
public class SampleEnrichmentsDatafetcher extends EnrichmentsDatafetcher {
  final SampleEnrichmentsService sampleEnrichmentsService;

  SampleEnrichmentsDatafetcher(SampleEnrichmentsService sampleEnrichmentsService) {
    this.sampleEnrichmentsService = sampleEnrichmentsService;
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public SampleEnrichment addSampleEnrichment(String did, Boolean enriched) {
    SampleEnrichment sampleEnrichment = SampleEnrichment.newBuilder().did(did).enriched(enriched).build();
    return sampleEnrichmentsService.addSampleEnrichment(sampleEnrichment);
  }

  @DgsDeltaFiEnrichment
  @SuppressWarnings("unused")
  public SampleEnrichment sampleEnrichment(DataFetchingEnvironment dfe) {
    DeltaFiEnrichments deltaFiDomains = dfe.getSource();
    return sampleEnrichmentsService.forDid(getDid(dfe));
  }
}

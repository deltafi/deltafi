package org.deltafi.dgs.datafetchers;

import com.netflix.graphql.dgs.*;
import graphql.schema.DataFetchingEnvironment;
import org.deltafi.dgs.api.annotation.DgsDeltaFiDomain;
import org.deltafi.dgs.api.datafetchers.DomainsDatafetcher;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.services.SampleDomainsService;

@DgsComponent
public class SampleDomainsDatafetcher extends DomainsDatafetcher {
  final SampleDomainsService sampleDomainsService;

  SampleDomainsDatafetcher(SampleDomainsService sampleDomainsService) {
    this.sampleDomainsService = sampleDomainsService;
  }

  @DgsMutation
  @SuppressWarnings("unused")
  public SampleDomain addSampleDomain(String did, Boolean domained) {
    SampleDomain sampleDomain = SampleDomain.newBuilder().did(did).domained(domained).build();
    return sampleDomainsService.addSampleDomain(sampleDomain);
  }

  @DgsDeltaFiDomain
  @SuppressWarnings("unused")
  public SampleDomain sampleDomain(DataFetchingEnvironment dfe) {
    return sampleDomainsService.forDid(getDid(dfe));
  }
}

package org.deltafi.dgs.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.generated.client.*;
import org.deltafi.dgs.generated.types.DeltaFiDomains;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.deltafi.dgs.services.ErrorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;


@TestPropertySource(properties = "enableScheduling=false")
@SpringBootTest
class ErrorDatafetcherTest<ErrorDomainProjection> {

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    ErrorService errorService;

    @Autowired
    DeltaFileRepo deltaFileRepo;

    DeltaFile deltaFile(String did) {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid(did);
        deltaFile.setActions(new ArrayList<>());
        return deltaFile;
    }

    ErrorDomain errorDomain(String did, String originatorDid, String fromAction, String cause, String context) {
        DeltaFile originator = deltaFile(originatorDid);
        return ErrorDomain.newBuilder()
                .did(did)
                .originator(originator)
                .originatorDid(originatorDid)
                .cause(cause)
                .context(context)
                .fromAction(fromAction)
                .build();
    }

    DeltaFile deltaFile(String errorDid, ErrorDomain errorDomain) {
        DeltaFile retval = new DeltaFile();
        retval.setDid(errorDid);
        retval.setActions(new ArrayList<>());
        retval.setDomains(new DeltaFiDomains());
        retval.getDomains().setDid(errorDid);
        retval.getDomains().setDomainTypes(Collections.singletonList("error"));
        retval.getDomains().setError(errorDomain);

        return retval;
    }

    void loadDummyDomains() {
        List<DeltaFile> deltaFiles = new ArrayList<>();

        deltaFiles.add(deltaFile("e1-error", errorDomain("e1", "orig", "FormatAction", "Hosed", "Right there")));
        deltaFiles.add(deltaFile("e2-error", errorDomain("e2", "orig", "FormatAction", "Bombed", "Right there")));
        deltaFiles.add(deltaFile("e3-error", errorDomain("e3", "orig", "FormatAction", "Jacked", "Right there")));
        deltaFiles.add(deltaFile("e4-error", errorDomain("e4", "nope", "FormatAction", "Not bad", "Somewhere")));

        deltaFileRepo.saveAll(deltaFiles);
    }

    BaseProjectionNode projection = new GetErrorProjectionRoot()
            .did()
            .cause()
            .context()
            .fromAction()
            .originatorDid()
            .originator()
                .did()
                .actions()
                    .name()
                    .state()
                .parent()
            .parent();

    @BeforeEach
    void setUp() {
        deltaFileRepo.deleteAll();
        loadDummyDomains();
    }

    @Test
    void getError() {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new GetErrorGraphQLQuery.Builder().did("e1-error").build(),
                projection
        );

        ErrorDomain errorDomain = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data.getError",
                ErrorDomain.class
        );
        assertNotNull(errorDomain);
        assertThat(errorDomain.getDid()).isEqualTo("e1");
        assertThat(errorDomain.getOriginatorDid()).isEqualTo("orig");
        assertThat(errorDomain.getOriginator().getDid()).isEqualTo("orig");
        assertThat(errorDomain.getCause()).isEqualTo("Hosed");
        assertThat(errorDomain.getContext()).isEqualTo("Right there");
        assertThat(errorDomain.getFromAction()).isEqualTo("FormatAction");
    }

    @Test
    void getErrorsFor() {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new GetErrorsForGraphQLQuery.Builder().originatorDid("orig").build(),
                projection
        );

        List<ErrorDomain> errorDomains = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data.getErrorsFor",
                new TypeRef<>() {}
        );

        assertNotNull(errorDomains);
        assertThat(errorDomains.get(0).getDid()).isEqualTo("e1");
        assertThat(errorDomains.get(0).getOriginatorDid()).isEqualTo("orig");
        assertThat(errorDomains.get(0).getOriginator().getDid()).isEqualTo("orig");
        assertThat(errorDomains.get(0).getCause()).isEqualTo("Hosed");
        assertThat(errorDomains.get(0).getContext()).isEqualTo("Right there");
        assertThat(errorDomains.get(0).getFromAction()).isEqualTo("FormatAction");
        assertThat(errorDomains.get(1).getDid()).isEqualTo("e2");
        assertThat(errorDomains.get(2).getDid()).isEqualTo("e3");
    }

}
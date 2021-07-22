package org.deltafi.dgs.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import graphql.ExecutionResult;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.generated.client.*;
import org.deltafi.dgs.repo.ErrorRepo;
import org.deltafi.dgs.services.ErrorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
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
    ErrorRepo errorRepo;

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

    void loadDummyDomains() {
        List<ErrorDomain> errorDomainList = new ArrayList<>();

        errorDomainList.add(errorDomain("e1", "orig", "FormatAction", "Hosed", "Right there"));
        errorDomainList.add(errorDomain("e2", "orig", "FormatAction", "Bombed", "Right there"));
        errorDomainList.add(errorDomain("e3", "orig", "FormatAction", "Jacked", "Right there"));
        errorDomainList.add(errorDomain("e4", "nope", "FormatAction", "Not bad", "Somewhere"));

        errorRepo.saveAll(errorDomainList);
    }

    BaseProjectionNode projection = new GetErrorProjectionRoot()
            .did()
            .cause()
            .context()
            .fromAction()
            .originatorDid()
            .originator()
            .did()
            .parent();

    @BeforeEach
    void setUp() {
        errorRepo.deleteAll();
        loadDummyDomains();
    }

    @Test
    void getError() {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new GetErrorGraphQLQuery.Builder().did("e1").build(),
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

    @Test
    void deleteError() {

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new DeleteErrorGraphQLQuery.Builder().did("e1").build()
        );

        Boolean result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data.deleteError",
                Boolean.class
        );

        assertTrue(result);
        assertThat(errorRepo.findAll().size()).isEqualTo(3);
    }

    @Test
    void deleteErrors() {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new DeleteErrorsGraphQLQuery.Builder().dids(Arrays.asList("e1","e3","blah")).build()
        );

        List<Boolean> result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data.deleteErrors",
                new TypeRef<>() {}
        );

        assertThat(result).isEqualTo(Arrays.asList(true, true, false));
        assertThat(errorRepo.findAll().size()).isEqualTo(2);
    }
}
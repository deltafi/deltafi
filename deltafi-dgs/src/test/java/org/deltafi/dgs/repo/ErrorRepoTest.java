package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.services.ErrorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = "enableScheduling=false")
@SpringBootTest
class ErrorRepoTest {

    @Autowired
    private ErrorService errorService;

    @Autowired
    private ErrorRepo errorRepo;

    @BeforeEach
    public void setup() { errorRepo.deleteAll(); }

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

    @Test
    void deleteByDid() {
        ErrorDomain ed = errorDomain("me", "bugger", "Action", "Cause", "Context");

        errorRepo.save(ed);

        assertEquals(1, errorRepo.findAll().size());
        assertEquals(0L, errorRepo.deleteByDid("moo"));
        assertEquals(1, errorRepo.findAll().size());
        assertEquals(1L, errorRepo.deleteByDid("me"));
        assertEquals(0, errorRepo.findAll().size());
    }

    @Test
    void findAllByOriginatorDid() {
        loadDummyDomains();

        List<ErrorDomain> result = errorRepo.findAllByOriginatorDid("orig");

        assertEquals(3, result.size());

        result = errorRepo.findAllByOriginatorDid("nope");

        assertEquals(1, result.size());
    }
}
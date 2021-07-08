package org.deltafi.dgs.repo;

import org.deltafi.dgs.Util;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.Action;
import org.deltafi.dgs.generated.types.ActionEvent;
import org.deltafi.dgs.generated.types.ActionState;
import org.deltafi.dgs.generated.types.DeltaFileStage;
import org.deltafi.dgs.services.DeltaFilesService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = "enableScheduling=false")
@SpringBootTest
class DeltaFileRepoTest {

    @Autowired
    private DeltaFilesService deltaFilesService;

    @Autowired
    private DeltaFileRepo deltaFileRepo;

    @BeforeEach
    public void setup() {
        deltaFileRepo.deleteAll();
    }

    @Test
    void testUpdateForRequeue() {
        DeltaFile hit = deltaFile("did");
        DeltaFile miss = deltaFile("did2");

        // mongo eats microseconds, jump through hoops
        OffsetDateTime now = OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);
        Action shouldRequeue = Action.newBuilder().name("hit").modified(now.minusSeconds(1000)).state(ActionState.QUEUED).history(new ArrayList<>()).build();
        Action shouldStay = Action.newBuilder().name("miss").modified(now.plusSeconds(1000)).state(ActionState.QUEUED).history(new ArrayList<>()).build();

        hit.setActions(Arrays.asList(shouldRequeue, shouldStay));
        miss.setActions(Arrays.asList(shouldStay, shouldStay));

        deltaFileRepo.save(hit);
        deltaFileRepo.save(miss);

        List<DeltaFile> hits = deltaFileRepo.updateForRequeue(now);
        assertEquals(1, hits.size());
        assertEquals(hit.getDid(), hits.get(0).getDid());

        DeltaFile hitAfter = deltaFilesService.getDeltaFile("did");
        DeltaFile missAfter = deltaFilesService.getDeltaFile("did2");

        assertEquals(miss, missAfter);
        assertNotEquals(hit.getActions().get(0).getModified(), hitAfter.getActions().get(0).getModified());
        assertEquals(hit.getActions().get(1).getModified(), hitAfter.getActions().get(1).getModified());
    }

    @Test
    void testMarkForDeleteCreatedBefore() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFilesService.addDeltaFile(deltaFile);
        deltaFilesService.markForDelete(OffsetDateTime.now(), null,null, "policy");
        DeltaFile after = deltaFilesService.getDeltaFile(deltaFile.getDid());
        assertThat(after.getStage()).isEqualTo(DeltaFileStage.DELETE.name());
    }

    @Test
    void testMarkForDeleteNotCreatedBefore() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setCreated(OffsetDateTime.now().plusHours(1));
        deltaFilesService.addDeltaFile(deltaFile);
        deltaFilesService.markForDelete(OffsetDateTime.now(),null, null, "policy");
        DeltaFile after = deltaFilesService.getDeltaFile(deltaFile.getDid());
        assertThat(after.getStage()).isNotEqualTo(DeltaFileStage.DELETE.name());
    }

    @Test
    void testMarkForDeleteCompletedBefore() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFile.setModified(OffsetDateTime.now());
        deltaFile.setStage(DeltaFileStage.COMPLETE.name());
        deltaFilesService.addDeltaFile(deltaFile);
        deltaFilesService.markForDelete(null, OffsetDateTime.now(), null, "policy");
        DeltaFile after = deltaFilesService.getDeltaFile(deltaFile.getDid());
        assertThat(after.getStage()).isEqualTo(DeltaFileStage.DELETE.name());
    }

    @Test
    void testMarkForDeleteNotCompletedBefore() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFilesService.addDeltaFile(deltaFile);
        deltaFilesService.markForDelete(null, OffsetDateTime.now(), null, "policy");
        DeltaFile after = deltaFilesService.getDeltaFile(deltaFile.getDid());
        assertThat(after.getStage()).isNotEqualTo(DeltaFileStage.DELETE.name());
    }

    @Test
    void testMarkForDeleteFlowMatches() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFilesService.addDeltaFile(deltaFile);
        deltaFilesService.markForDelete(OffsetDateTime.now(), null,"flow", "policy");
        DeltaFile after = deltaFilesService.getDeltaFile(deltaFile.getDid());
        assertThat(after.getStage()).isEqualTo(DeltaFileStage.DELETE.name());
    }

    @Test
    void testMarkForDeleteFlowDoesntMatch() {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "flow");
        deltaFilesService.addDeltaFile(deltaFile);
        deltaFilesService.markForDelete(OffsetDateTime.now(), null,"not the flow", "policy");
        DeltaFile after = deltaFilesService.getDeltaFile(deltaFile.getDid());
        assertThat(after.getStage()).isNotEqualTo(DeltaFileStage.DELETE.name());
    }

    @Test
    void deleteByDidIn() {
        List<DeltaFile> deltaFiles = Stream.of("a", "b", "c").map(this::deltaFile).collect(Collectors.toList());
        deltaFileRepo.saveAll(deltaFiles);

        assertEquals(3, deltaFileRepo.count());

        deltaFileRepo.deleteByDidIn(Arrays.asList("a", "c"));

        assertEquals(1, deltaFileRepo.count());
        assertEquals("b", deltaFileRepo.findAll().get(0).getDid());
    }

    DeltaFile deltaFile(String did) {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid(did);
        deltaFile.setActions(new ArrayList<>());
        return deltaFile;
    }
}
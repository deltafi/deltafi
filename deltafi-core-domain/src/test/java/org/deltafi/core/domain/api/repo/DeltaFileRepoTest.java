package org.deltafi.core.domain.api.repo;

import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.deltafi.core.domain.generated.types.DeltaFileStage;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class DeltaFileRepoTest {
    @Autowired
    private DeltaFileRepo deltaFileRepo;

    @BeforeEach
    public void setup() {
        deltaFileRepo.deleteAll();
    }

    @Test
    void testUpdateForRequeue() {
        // mongo eats microseconds, jump through hoops
        OffsetDateTime now = OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);
        Action shouldRequeue = Action.newBuilder().name("hit").modified(now.minusSeconds(1000)).state(ActionState.QUEUED).build();
        Action shouldStay = Action.newBuilder().name("miss").modified(now.plusSeconds(1000)).state(ActionState.QUEUED).build();

        DeltaFile hit = Util.buildDeltaFile("did", null, null, now, now);
        hit.setActions(Arrays.asList(shouldRequeue, shouldStay));
        deltaFileRepo.save(hit);

        DeltaFile miss = Util.buildDeltaFile("did2", null, null, now, now);
        miss.setActions(Arrays.asList(shouldStay, shouldStay));
        deltaFileRepo.save(miss);

        List<DeltaFile> hits = deltaFileRepo.updateForRequeue(now, 30);

        assertEquals(1, hits.size());
        assertEquals(hit.getDid(), hits.get(0).getDid());

        DeltaFile hitAfter = loadDeltaFile("did");
        DeltaFile missAfter = loadDeltaFile("did2");

        assertEquals(miss, missAfter);
        assertNotEquals(hit.getActions().get(0).getModified(), hitAfter.getActions().get(0).getModified());
        assertEquals(hit.getActions().get(1).getModified(), hitAfter.getActions().get(1).getModified());
    }

    @Test
    void deleteByDidIn() {
        List<DeltaFile> deltaFiles = Stream.of("a", "b", "c").map(Util::buildDeltaFile).collect(Collectors.toList());
        deltaFileRepo.saveAll(deltaFiles);

        assertEquals(3, deltaFileRepo.count());

        deltaFileRepo.deleteByDidIn(Arrays.asList("a", "c"));

        assertEquals(1, deltaFileRepo.count());
        assertEquals("b", deltaFileRepo.findAll().get(0).getDid());
    }

    @Test
    void testMarkForDeleteCreatedBefore() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile2);
        DeltaFile deltaFile3 = Util.buildDeltaFile("3", null, DeltaFileStage.INGRESS, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
        deltaFileRepo.save(deltaFile3);

        deltaFileRepo.markForDelete(OffsetDateTime.now().plusSeconds(1), null, null, "policy");

        DeltaFile after = loadDeltaFile(deltaFile1.getDid());
        assertEquals(DeltaFileStage.DELETE.name(), after.getStage());
        after = loadDeltaFile(deltaFile2.getDid());
        assertEquals(DeltaFileStage.DELETE.name(), after.getStage());
        after = loadDeltaFile(deltaFile3.getDid());
        assertEquals(DeltaFileStage.INGRESS.name(), after.getStage());
    }

    @Test
    void testMarkForDeleteCompletedBefore() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
        deltaFileRepo.save(deltaFile2);
        DeltaFile deltaFile3 = Util.buildDeltaFile("3", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile3);

        deltaFileRepo.markForDelete(null, OffsetDateTime.now().plusSeconds(1), null, "policy");

        DeltaFile after = loadDeltaFile(deltaFile1.getDid());
        assertEquals(DeltaFileStage.DELETE.name(), after.getStage());
        after = loadDeltaFile(deltaFile2.getDid());
        assertEquals(DeltaFileStage.COMPLETE.name(), after.getStage());
        after = loadDeltaFile(deltaFile3.getDid());
        assertEquals(DeltaFileStage.ERROR.name(), after.getStage());
    }

    @Test
    void testMarkForDeleteWithFlow() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile2);

        deltaFileRepo.markForDelete(OffsetDateTime.now().plusSeconds(1), null, "a", "policy");

        DeltaFile after = loadDeltaFile(deltaFile1.getDid());
        assertEquals(DeltaFileStage.DELETE.name(), after.getStage());
        after = loadDeltaFile(deltaFile2.getDid());
        assertEquals(DeltaFileStage.ERROR.name(), after.getStage());
    }

    @Test
    void testMarkForDelete_alreadyMarkedDeleted() {
        OffsetDateTime oneSecondAgo = OffsetDateTime.now().minusSeconds(1);

        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.DELETE, oneSecondAgo, oneSecondAgo);
        deltaFileRepo.save(deltaFile1);

        deltaFileRepo.markForDelete(OffsetDateTime.now(), null, null, "policy");

        DeltaFile after = loadDeltaFile(deltaFile1.getDid());
        assertEquals(DeltaFileStage.DELETE.name(), after.getStage());
        assertEquals(oneSecondAgo.toEpochSecond(), after.getModified().toEpochSecond());
    }

    private DeltaFile loadDeltaFile(String did) {
        return deltaFileRepo.findById(did).orElse(null);
    }
}
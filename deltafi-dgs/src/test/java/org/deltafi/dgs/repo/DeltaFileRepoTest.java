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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
    void testFindAndDispatchedQueued() {
        DeltaFile deltaFile = deltaFile("did");

        Action shouldDispatch = Action.newBuilder().name("hit").state(ActionState.QUEUED).history(new ArrayList<>()).build();
        Action shouldStay = Action.newBuilder().name("miss").state(ActionState.QUEUED).history(new ArrayList<>()).build();

        List<Action> actions = Arrays.asList(shouldDispatch, shouldStay);
        deltaFile.setActions(actions);

        deltaFileRepo.save(deltaFile);

        deltaFileRepo.findAndDispatchForAction("hit", 10, false);

        DeltaFile after = deltaFilesService.getDeltaFile("did");

        Action hitAction = after.getActions().stream().filter(a -> "hit".equals(a.getName())).findFirst().orElseThrow(() -> new RuntimeException("test failed"));
        assertEquals(ActionState.DISPATCHED, hitAction.getState());

        ActionEvent history = hitAction.getHistory().get(0);
        assertEquals(ActionState.DISPATCHED, history.getState());

        Action missAction = after.getActions().stream().filter(a -> "miss".equals(a.getName())).findFirst().orElseThrow(() -> new RuntimeException("test failed"));
        assertTrue(missAction.getHistory().isEmpty());
        assertEquals(ActionState.QUEUED, missAction.getState());
    }

    @Test
    void testFindAndDispatchedDryRun() {
        DeltaFile deltaFile = deltaFile("did");

        Action shouldDispatch = Action.newBuilder().name("hit").state(ActionState.QUEUED).history(new ArrayList<>()).build();
        Action shouldStay = Action.newBuilder().name("miss").state(ActionState.QUEUED).history(new ArrayList<>()).build();

        List<Action> actions = Arrays.asList(shouldDispatch, shouldStay);
        deltaFile.setActions(actions);

        deltaFileRepo.save(deltaFile);

        deltaFileRepo.findAndDispatchForAction("hit", 10, true);

        DeltaFile after = deltaFilesService.getDeltaFile("did");

        Action hitAction = after.getActions().stream().filter(a -> "hit".equals(a.getName())).findFirst().orElseThrow(() -> new RuntimeException("test failed"));
        assertEquals(ActionState.QUEUED, hitAction.getState());
        assertTrue(hitAction.getHistory().isEmpty());
    }

    @Test
    void testFindAndDispatchTimedOutDispatched() {
        DeltaFile deltaFile = deltaFile("did");

        OffsetDateTime modified = OffsetDateTime.now().minusSeconds(31);
        Action shouldDispatch = Action.newBuilder().name("hit").state(ActionState.DISPATCHED).errorMessage("old dispatch").modified(modified).history(new ArrayList<>()).build();
        Action shouldStay = Action.newBuilder().name("miss").state(ActionState.QUEUED).history(new ArrayList<>()).build();

        List<Action> actions = Arrays.asList(shouldDispatch, shouldStay);
        deltaFile.setActions(actions);

        deltaFileRepo.save(deltaFile);

        deltaFileRepo.findAndDispatchForAction("hit", 10, false);

        DeltaFile after = deltaFilesService.getDeltaFile("did");

        Action hitAction = after.getActions().stream().filter(a -> "hit".equals(a.getName())).findFirst().orElseThrow(() -> new RuntimeException("test failed"));
        assertEquals(ActionState.DISPATCHED, hitAction.getState());
        assertTrue(hitAction.getModified().compareTo(modified) > 0);
        assertNull(hitAction.getErrorMessage());

        assertTrue(after.getModified().compareTo(modified) > 0);
        assertEquals(2L, after.getVersion());

        ActionEvent history = hitAction.getHistory().get(0);
        assertEquals(ActionState.DISPATCHED, history.getState());

        Action missAction = after.getActions().stream().filter(a -> "miss".equals(a.getName())).findFirst().orElseThrow(() -> new RuntimeException("test failed"));
        assertTrue(missAction.getHistory().isEmpty());
        assertEquals(ActionState.QUEUED, missAction.getState());
    }

    @Test
    void testFindAndDispatchNoMatches() {
        DeltaFile original = deltaFile("did");
        OffsetDateTime time = OffsetDateTime.now().minusMinutes(5);
        original.setCreated(time);
        original.setModified(time);
        Action completeAction = Action.newBuilder().state(ActionState.COMPLETE).history(new ArrayList<>()).name("myTestAction").build();
        Action queuedAction = Action.newBuilder().state(ActionState.QUEUED).history(new ArrayList<>()).name("otherAction").build();
        original.setActions(Arrays.asList(completeAction, queuedAction));
        original = deltaFileRepo.save(original);

        deltaFileRepo.findAndDispatchForAction("myTestAction", 10, false);

        DeltaFile after = deltaFilesService.getDeltaFile("did");

        Assertions.assertTrue(Util.equalIgnoringDates(original, after));
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
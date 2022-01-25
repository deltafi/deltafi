package org.deltafi.core.domain.api.repo;

import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.DeltaFiles;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {"enableScheduling=false"})
class DeltaFileRepoTest {
    @Autowired
    private DeltaFileRepo deltaFileRepo;

    // mongo eats microseconds, jump through hoops
    private final OffsetDateTime MONGO_NOW =  OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);

    @BeforeEach
    public void setup() {
        deltaFileRepo.deleteAll();
    }

    @Test
    void testExpirationIndexUpdate() {
        final Duration newTtlValue = Duration.ofSeconds(123456);

        List<IndexInfo> oldIndexList = deltaFileRepo.getIndexes();
        deltaFileRepo.setExpirationIndex(newTtlValue);
        List<IndexInfo> newIndexList = deltaFileRepo.getIndexes();

        assertEquals(oldIndexList.size(), newIndexList.size());
        assertEquals(newTtlValue.getSeconds(), deltaFileRepo.getTtlExpiration().getSeconds());
    }

    @Test
    void testReadDids() {
        Set<String> dids = Set.of("a", "b", "c");
        List<DeltaFile> deltaFiles = dids.stream().map(Util::buildDeltaFile).collect(Collectors.toList());
        deltaFileRepo.saveAll(deltaFiles);

        Set<String> didsRead = deltaFileRepo.readDids();

        assertEquals(3, didsRead.size());
        assertTrue(didsRead.containsAll(dids));
    }

    @Test
    void testUpdateForRequeue() {
        Action shouldRequeue = Action.newBuilder().name("hit").modified(MONGO_NOW.minusSeconds(1000)).state(ActionState.QUEUED).build();
        Action shouldStay = Action.newBuilder().name("miss").modified(MONGO_NOW.plusSeconds(1000)).state(ActionState.QUEUED).build();

        DeltaFile hit = Util.buildDeltaFile("did", null, null, MONGO_NOW, MONGO_NOW);
        hit.setActions(Arrays.asList(shouldRequeue, shouldStay));
        deltaFileRepo.save(hit);

        DeltaFile miss = Util.buildDeltaFile("did2", null, null, MONGO_NOW, MONGO_NOW);
        miss.setActions(Arrays.asList(shouldStay, shouldStay));
        deltaFileRepo.save(miss);

        List<DeltaFile> hits = deltaFileRepo.updateForRequeue(MONGO_NOW, 30);

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
        assertEquals(DeltaFileStage.DELETE, after.getStage());
        after = loadDeltaFile(deltaFile2.getDid());
        assertEquals(DeltaFileStage.DELETE, after.getStage());
        after = loadDeltaFile(deltaFile3.getDid());
        assertEquals(DeltaFileStage.INGRESS, after.getStage());
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
        assertEquals(DeltaFileStage.DELETE, after.getStage());
        after = loadDeltaFile(deltaFile2.getDid());
        assertEquals(DeltaFileStage.COMPLETE, after.getStage());
        after = loadDeltaFile(deltaFile3.getDid());
        assertEquals(DeltaFileStage.ERROR, after.getStage());
    }

    @Test
    void testMarkForDeleteWithFlow() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
        deltaFileRepo.save(deltaFile2);

        deltaFileRepo.markForDelete(OffsetDateTime.now().plusSeconds(1), null, "a", "policy");

        DeltaFile after = loadDeltaFile(deltaFile1.getDid());
        assertEquals(DeltaFileStage.DELETE, after.getStage());
        after = loadDeltaFile(deltaFile2.getDid());
        assertEquals(DeltaFileStage.ERROR, after.getStage());
    }

    @Test
    void testMarkForDelete_alreadyMarkedDeleted() {
        OffsetDateTime oneSecondAgo = OffsetDateTime.now().minusSeconds(1);

        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.DELETE, oneSecondAgo, oneSecondAgo);
        deltaFileRepo.save(deltaFile1);

        deltaFileRepo.markForDelete(OffsetDateTime.now(), null, null, "policy");

        DeltaFile after = loadDeltaFile(deltaFile1.getDid());
        assertEquals(DeltaFileStage.DELETE, after.getStage());
        assertEquals(oneSecondAgo.toEpochSecond(), after.getModified().toEpochSecond());
    }

    @Test
    void testDeltaFiles_all() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
        deltaFileRepo.save(deltaFile2);

        DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null);
        assertEquals(deltaFiles.getDeltaFiles(), List.of(deltaFile2, deltaFile1));
    }

    @Test
    void testDeltaFiles_limit() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
        deltaFileRepo.save(deltaFile2);

        DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 1, new DeltaFilesFilter(), null);
        assertEquals(1, deltaFiles.getCount());
        assertEquals(2, deltaFiles.getTotalCount());

        deltaFiles = deltaFileRepo.deltaFiles(null, 2, new DeltaFilesFilter(), null);
        assertEquals(2, deltaFiles.getCount());
        assertEquals(2, deltaFiles.getTotalCount());

        deltaFiles = deltaFileRepo.deltaFiles(null, 100, new DeltaFilesFilter(), null);
        assertEquals(2, deltaFiles.getCount());
        assertEquals(2, deltaFiles.getTotalCount());
    }

    @Test
    void testDeltaFiles_offset() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
        deltaFileRepo.save(deltaFile2);

        DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(0, 50, new DeltaFilesFilter(), null);
        assertEquals(0, deltaFiles.getOffset());
        assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(1, 50, new DeltaFilesFilter(), null);
        assertEquals(1, deltaFiles.getOffset());
        assertEquals(List.of(deltaFile1), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(2, 50, new DeltaFilesFilter(), null);
        assertEquals(2, deltaFiles.getOffset());
        assertEquals(Collections.emptyList(), deltaFiles.getDeltaFiles());
    }

    @Test
    void testDeltaFiles_sort() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
        deltaFileRepo.save(deltaFile2);

        DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field("created").build());
        assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field("created").build());
        assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field("modified").build());
        assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field("modified").build());
        assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());
    }

    @Test
    void testDeltaFiles_filter() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
        deltaFile1.setDomains(List.of(new Domain("domain1", null, null)));
        deltaFile1.setEnrichment(List.of(new Enrichment("enrichment1", null, null)));
        deltaFile1.setMarkedForDelete(MONGO_NOW);
        deltaFile1.setSourceInfo(new SourceInfo("filename1", "flow1", List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))));
        deltaFile1.setActions(List.of(Action.newBuilder().name("action1").build()));
        deltaFile1.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename1").formatAction("formatAction1").metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue2"))).egressActions(List.of("EgressAction1", "EgressAction2")).build()));
        deltaFile1.setErrorAcknowledged(MONGO_NOW);
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
        deltaFile2.setDomains(List.of(new Domain("domain1", null, null), new Domain("domain2", null, null)));
        deltaFile2.setEnrichment(List.of(new Enrichment("enrichment1", null, null), new Enrichment("enrichment2", null, null)));
        deltaFile2.setSourceInfo(new SourceInfo("filename2", "flow2", List.of()));
        deltaFile2.setActions(List.of(Action.newBuilder().name("action1").build(), Action.newBuilder().name("action2").build()));
        deltaFile2.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename2").formatAction("formatAction2").egressActions(List.of("EgressAction1")).build()));
        deltaFileRepo.save(deltaFile2);

        testFilter(DeltaFilesFilter.newBuilder().createdAfter(MONGO_NOW).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().createdBefore(MONGO_NOW).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().domains(Collections.emptyList()).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1")).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1", "domain2")).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().enrichment(Collections.emptyList()).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1")).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1", "enrichment2")).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().isMarkedForDelete(true).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().isMarkedForDelete(false).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().modifiedAfter(MONGO_NOW).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().modifiedBefore(MONGO_NOW).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.COMPLETE).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().filename("filename1").build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().flow("flow2").build()).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"))).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value1"))).build()).build());
        testFilter(DeltaFilesFilter.newBuilder().actions(Collections.emptyList()).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1")).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1", "action2")).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().filename("formattedFilename1").build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().formatAction("formatAction2").build()).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"))).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue2"))).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue1"))).build()).build());
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1")).build()).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1", "EgressAction2")).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().dids(Collections.emptyList()).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().dids(Collections.singletonList("1")).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "3")).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "2")).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().dids(List.of("3", "4")).build());
        testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(true).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(false).build(), deltaFile2);
    }

    private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
        DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null);
        assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());
    }

    private DeltaFile loadDeltaFile(String did) {
        return deltaFileRepo.findById(did).orElse(null);
    }
}

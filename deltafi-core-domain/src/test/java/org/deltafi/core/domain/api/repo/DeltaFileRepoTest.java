package org.deltafi.core.domain.api.repo;

import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.DeltaFiles;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.generated.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class DeltaFileRepoTest {
    @Autowired
    private DeltaFileRepo deltaFileRepo;

    @Autowired
    private DeltaFiProperties deltaFiProperties;

    // mongo eats microseconds, jump through hoops
    private final OffsetDateTime MONGO_NOW =  OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);

    @BeforeEach
    public void setup() {
        deltaFileRepo.deleteAll();
    }

    @Test
    void testExpirationIndexConstructor() {
        assertEquals(deltaFiProperties.getDbFileAgeOffSeconds(), deltaFileRepo.getTtlExpiration().getSeconds());
    }

    @Test
    void testExpirationIndexUpdate() {
        final long newTtlValue = 123456;

        List<IndexInfo> oldIndexList = deltaFileRepo.getIndexes();
        deltaFileRepo.setExpirationIndex(newTtlValue);
        List<IndexInfo> newIndexList = deltaFileRepo.getIndexes();

        assertEquals(oldIndexList.size(), newIndexList.size());
        assertEquals(newTtlValue, deltaFileRepo.getTtlExpiration().getSeconds());
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
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field(DeltaFileField.created).build());
        assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field(DeltaFileField.created).build());
        assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field(DeltaFileField.modified).build());
        assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

        deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
                DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field(DeltaFileField.modified).build());
        assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());
    }

    @Test
    void testDeltaFiles_filter() {
        DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
        deltaFile1.setDomains(List.of(KeyValue.newBuilder().key("domain1").build()));
        deltaFile1.setEnrichment(List.of(KeyValue.newBuilder().key("enrichment1").build()));
        deltaFile1.setMarkedForDelete(MONGO_NOW);
        deltaFile1.setSourceInfo(SourceInfo.newBuilder().flow("flow1").filename("filename1").metadata(List.of(KeyValue.newBuilder().key("key1").value("value1").build(), KeyValue.newBuilder().key("key2").value("value2").build())).build());
        deltaFile1.setActions(List.of(Action.newBuilder().name("action1").build()));
        deltaFile1.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename1").formatAction("formatAction1").metadata(List.of(KeyValue.newBuilder().key("formattedKey1").value("formattedValue1").build(), KeyValue.newBuilder().key("formattedKey2").value("formattedValue2").build())).egressActions(List.of("EgressAction1", "EgressAction2")).build()));
        deltaFileRepo.save(deltaFile1);
        DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
        deltaFile2.setDomains(List.of(KeyValue.newBuilder().key("domain1").build(), KeyValue.newBuilder().key("domain2").build()));
        deltaFile2.setEnrichment(List.of(KeyValue.newBuilder().key("enrichment1").build(), KeyValue.newBuilder().key("enrichment2").build()));
        deltaFile2.setSourceInfo(SourceInfo.newBuilder().flow("flow2").filename("filename2").build());
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
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(KeyValueInput.newBuilder().key("key1").value("value1").build())).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(KeyValueInput.newBuilder().key("key1").value("value1").build(), KeyValueInput.newBuilder().key("key2").value("value2").build())).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(KeyValueInput.newBuilder().key("key1").value("value1").build(), KeyValueInput.newBuilder().key("key2").value("value1").build())).build()).build());
        testFilter(DeltaFilesFilter.newBuilder().actions(Collections.emptyList()).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1")).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1", "action2")).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().filename("formattedFilename1").build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().formatAction("formatAction2").build()).build(), deltaFile2);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(KeyValueInput.newBuilder().key("formattedKey1").value("formattedValue1").build())).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(KeyValueInput.newBuilder().key("formattedKey1").value("formattedValue1").build(), KeyValueInput.newBuilder().key("formattedKey2").value("formattedValue2").build())).build()).build(), deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(KeyValueInput.newBuilder().key("formattedKey1").value("formattedValue1").build(), KeyValueInput.newBuilder().key("formattedKey2").value("formattedValue1").build())).build()).build());
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1")).build()).build(), deltaFile2, deltaFile1);
        testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1", "EgressAction2")).build()).build(), deltaFile1);
    }

    private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
        DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null);
        assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());
    }

    private DeltaFile loadDeltaFile(String did) {
        return deltaFileRepo.findById(did).orElse(null);
    }
}

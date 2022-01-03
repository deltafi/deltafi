package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.DeltaFileStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeltaFileRepo extends MongoRepository<DeltaFile, String>, DeltaFileRepoCustom {

    void deleteByDidIn(List<String> dids);

    Page<DeltaFile> findAllByOrderByCreatedDesc(Pageable pageable);
    Page<DeltaFile> findAllByOrderByModifiedDesc(Pageable pageable);
    Page<DeltaFile> findByStageOrderByModifiedDesc(DeltaFileStage stage, Pageable pageable);
    Page<DeltaFile> findBySourceInfoFilenameOrderByCreatedDesc(String filename, Pageable pageable);
}
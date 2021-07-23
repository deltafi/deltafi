package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.DeltaFile;
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
    Page<DeltaFile> findByStageOrderByModifiedDesc(String stage, Pageable pageable);
    Page<DeltaFile> findBySourceInfoFilenameOrderByCreatedDesc(String filename, Pageable pageable);
    List<DeltaFile> findAllByDomainsErrorOriginatorDid(String originatorDid);
}
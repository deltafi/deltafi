package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.ErrorDomain;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ErrorRepo extends MongoRepository<ErrorDomain, String> {

    long deleteByDid(String did);

    List<ErrorDomain> findAllByOriginatorDid(String did);
}
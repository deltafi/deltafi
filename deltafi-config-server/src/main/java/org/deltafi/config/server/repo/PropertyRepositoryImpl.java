package org.deltafi.config.server.repo;

import com.mongodb.bulk.BulkWriteResult;
import org.deltafi.config.server.domain.PropertySet;
import org.deltafi.config.server.domain.PropertyUpdate;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class PropertyRepositoryImpl implements PropertyCustomRepository {
    public static final String ID = "_id";
    private final MongoTemplate mongoTemplate;

    public PropertyRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Set<String> getIds() {
        return new HashSet<>(mongoTemplate.findDistinct(ID, PropertySet.class, String.class));
    }

    @Override
    public int updateProperties(List<PropertyUpdate> updates) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PropertySet.class);
        bulkOps.updateOne(updates.stream().map(this::toMongoUpdate).collect(Collectors.toList()));
        BulkWriteResult writeResult = bulkOps.execute();
        return writeResult.getModifiedCount();
    }

    private Pair<Query, Update> toMongoUpdate(PropertyUpdate update) {
        Query query = Query.query(Criteria.where(ID).is(update.getPropertySetId())
                .and("properties")
                .elemMatch(Criteria.where("key").is(update.getKey()).and("editable").is(true)));
        Update mongoUpdate = Update.update("properties.$.value", update.getValue());
        return Pair.of(query, mongoUpdate);
    }
}

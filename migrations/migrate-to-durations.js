db.deltaFiProperties.updateOne({"_id":"deltafi-properties", "requeueSeconds": { $exists: true }}, [{"$set":{"requeueDuration": { $concat: ["PT",{$toString:"$requeueSeconds"},"S"]}}}])
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {$unset:{"requeueSeconds":""}})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "REQUEUE_DURATION"}}, {"arrayFilters":[{"filter": "REQUEUE_SECONDS"}]})

db.systemSnapshot.updateMany({"deltaFiProperties.requeueSeconds": {$exists: true}}, [{"$set":{"deltaFiProperties.requeueDuration": { $concat: ["PT",{$toString:"$deltaFiProperties.requeueSeconds"},"S"]}}}])
db.systemSnapshot.updateMany({}, {$unset:{"deltaFiProperties.requeueSeconds":""}})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "REQUEUE_DURATION"}}, {"arrayFilters":[{"filter": "REQUEUE_SECONDS"}]})

db.deltaFiProperties.updateOne({"_id":"deltafi-properties", "deltaFileCache.syncSeconds": { $exists: true }}, [{"$set":{"deltaFileCache.syncDuration": { $concat: ["PT",{$toString:"$deltaFileCache.syncSeconds"},"S"]}}}])
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {$unset:{"deltaFileCache.syncSeconds":""}})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "DELTA_FILE_CACHE_SYNC_DURATION"}}, {"arrayFilters":[{"filter": "DELTAFILE_CACHE_SYNC_SECONDS"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "DELTA_FILE_CACHE_SYNC_DURATION"}}, {"arrayFilters":[{"filter": "DELTA_FILE_CACHE_SYNC_SECONDS"}]})

db.systemSnapshot.updateMany({"deltaFiProperties.deltaFileCache.syncSeconds": { $exists: true }}, [{"$set":{"deltaFiProperties.deltaFileCache.syncDuration": { $concat: ["PT",{$toString:"$deltaFiProperties.deltaFileCache.syncSeconds"},"S"]}}}])
db.systemSnapshot.updateMany({}, {$unset:{"deltaFiProperties.deltaFileCache.syncSeconds":""}})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "DELTA_FILE_CACHE_SYNC_DURATION"}}, {"arrayFilters":[{"filter": "DELTAFILE_CACHE_SYNC_SECONDS"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "DELTA_FILE_CACHE_SYNC_DURATION"}}, {"arrayFilters":[{"filter": "DELTA_FILE_CACHE_SYNC_SECONDS"}]})

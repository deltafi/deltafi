db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "CHECKS_ACTION_QUEUE_SIZE_THRESHOLD"}}, {"arrayFilters":[{"filter": "ACTION_QUEUE_THRESHOLD"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "CHECKS_CONTENT_STORAGE_PERCENT_THRESHOLD"}}, {"arrayFilters":[{"filter": "CONTENT_STORAGE_THRESHOLD"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "DELETE_AGE_OFF_DAYS"}}, {"arrayFilters":[{"filter": "AGE_OFF_DAYS"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "DELETE_POLICY_BATCH_SIZE"}}, {"arrayFilters":[{"filter": "DELETE_BATCH_SIZE"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "DELTA_FILE_CACHE_ENABLED"}}, {"arrayFilters":[{"filter": "DELTAFILE_CACHE_ENABLED"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "DELTA_FILE_CACHE_SYNC_SECONDS"}}, {"arrayFilters":[{"filter": "DELTAFILE_CACHE_SYNC_SECONDS"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "INGRESS_DISK_SPACE_REQUIREMENT_IN_MB"}}, {"arrayFilters":[{"filter": "INGRESS_DISK_SPACE_REQUIRED"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "PLUGINS_IMAGE_PULL_SECRET"}}, {"arrayFilters":[{"filter": "IMAGE_PULL_SECRET"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "PLUGINS_IMAGE_REPOSITORY_BASE"}}, {"arrayFilters":[{"filter": "IMAGE_REPOSITORY_BASE"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "SCHEDULED_SERVICE_THREADS"}}, {"arrayFilters":[{"filter": "SCHEDULER_POOL_SIZE"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "UI_SECURITY_BANNER_BACKGROUND_COLOR"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_BACKGROUND_COLOR"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "UI_SECURITY_BANNER_ENABLED"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_ENABLED"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "UI_SECURITY_BANNER_TEXT"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_TEXT"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "UI_SECURITY_BANNER_TEXT_COLOR"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_TEXT_COLOR"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "UI_TOP_BAR_BACKGROUND_COLOR"}}, {"arrayFilters":[{"filter": "TOP_BAR_BACKGROUND_COLOR"}]})
db.deltaFiProperties.updateOne({"_id":"deltafi-properties"}, {"$set":{"setProperties.$[filter]": "UI_TOP_BAR_TEXT_COLOR"}}, {"arrayFilters":[{"filter": "TOP_BAR_TEXT_COLOR"}]})

db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "CHECKS_ACTION_QUEUE_SIZE_THRESHOLD"}}, {"arrayFilters":[{"filter": "ACTION_QUEUE_THRESHOLD"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "CHECKS_CONTENT_STORAGE_PERCENT_THRESHOLD"}}, {"arrayFilters":[{"filter": "CONTENT_STORAGE_THRESHOLD"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "DELETE_AGE_OFF_DAYS"}}, {"arrayFilters":[{"filter": "AGE_OFF_DAYS"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "DELETE_POLICY_BATCH_SIZE"}}, {"arrayFilters":[{"filter": "DELETE_BATCH_SIZE"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "DELTA_FILE_CACHE_ENABLED"}}, {"arrayFilters":[{"filter": "DELTAFILE_CACHE_ENABLED"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "DELTA_FILE_CACHE_SYNC_SECONDS"}}, {"arrayFilters":[{"filter": "DELTAFILE_CACHE_SYNC_SECONDS"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "INGRESS_DISK_SPACE_REQUIREMENT_IN_MB"}}, {"arrayFilters":[{"filter": "INGRESS_DISK_SPACE_REQUIRED"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "PLUGINS_IMAGE_PULL_SECRET"}}, {"arrayFilters":[{"filter": "IMAGE_PULL_SECRET"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "PLUGINS_IMAGE_REPOSITORY_BASE"}}, {"arrayFilters":[{"filter": "IMAGE_REPOSITORY_BASE"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "SCHEDULED_SERVICE_THREADS"}}, {"arrayFilters":[{"filter": "SCHEDULER_POOL_SIZE"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "UI_SECURITY_BANNER_BACKGROUND_COLOR"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_BACKGROUND_COLOR"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "UI_SECURITY_BANNER_ENABLED"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_ENABLED"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "UI_SECURITY_BANNER_TEXT"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_TEXT"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "UI_SECURITY_BANNER_TEXT_COLOR"}}, {"arrayFilters":[{"filter": "SECURITY_BANNER_TEXT_COLOR"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "UI_TOP_BAR_BACKGROUND_COLOR"}}, {"arrayFilters":[{"filter": "TOP_BAR_BACKGROUND_COLOR"}]})
db.systemSnapshot.updateMany({}, {"$set":{"deltaFiProperties.setProperties.$[filter]": "UI_TOP_BAR_TEXT_COLOR"}}, {"arrayFilters":[{"filter": "TOP_BAR_TEXT_COLOR"}]})

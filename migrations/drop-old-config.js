// Remove old action schemas that may have outdated schemas
db.actionSchema.remove({});
// Remove the old flow configuration table that is no longer used
db.deltafiConfig.drop();
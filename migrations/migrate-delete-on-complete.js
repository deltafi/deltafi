db.deltaFiProperties.updateOne({}, {$unset: {"delete.onCompletion": 1}, $pull: {"setProperties": "DELETE_ON_COMPLETION"}});
db.systemSnapshot.updateMany({}, {$unset: {"deltaFiProperties.delete.onCompletion": 1}, $pull: {"deltaFiProperties.setProperties": "DELETE_ON_COMPLETION"}});

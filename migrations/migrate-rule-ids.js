db.flowAssignmentRule.updateMany({name: null}, [{$set: {"name": "$_id"}}])

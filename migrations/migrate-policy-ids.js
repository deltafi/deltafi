db.deletePolicy.updateMany({name: null}, [{$set: {"name": "$_id"}}])

db.resumePolicy.updateMany({priority: {$exists: false}}, {"$set": {"priority": 50}});

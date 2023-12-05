db.resumePolicy.updateMany({name: {$exists: false}}, [
    {"$set": {"name": "$_id"}}
]);

db.systemSnapshot.find({"resumePolicies": {$exists: true}, "resumePolicies.name": {$exists: false}}).forEach(function(snapshot) {
    for(i = 0; i !== snapshot.resumePolicies.length; ++i) {
        snapshot.resumePolicies[i].name = snapshot.resumePolicies[i]._id;
    }
    db.systemSnapshot.replaceOne({_id: snapshot._id}, snapshot);
});

db.deltaFile.updateMany({nextAutoResume: { $exists: true }, nextAutoResumeReason: { $exists: false}}, {$set: { nextAutoResumeReason:"auto resume policy"}});

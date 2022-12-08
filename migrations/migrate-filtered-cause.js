db.deltaFile.find({"filtered": {$eq: true}, "actions.errorCause": {$exists: true}}).forEach(function(deltaFile) {
    for(i = 0; i !== deltaFile.actions.length; ++i) {
        if(deltaFile.actions[i].state === "FILTERED" && !deltaFile.actions[i].filteredCause) {
            deltaFile.actions[i].filteredCause = deltaFile.actions[i].errorCause;
            deltaFile.actions[i].errorCause = null;
        }
    }

    db.deltaFile.update({_id: deltaFile._id}, deltaFile);
});

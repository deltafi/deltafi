db.deltaFile.find({"protocolStack.content.contentReference.segments": {$exists: false}}).forEach(function(deltaFile) {
    for(i = 0; i !== deltaFile.protocolStack.length; ++i) {
        for(j = 0; j !== deltaFile.protocolStack[i].content.length; ++j) {
            cr = deltaFile.protocolStack[i].content[j].contentReference;
            cr.segments = [{uuid: cr.uuid, offset: cr.offset, size: cr.size, did: cr.did}];
            delete cr.uuid;
            delete cr.offset;
            delete cr.size;
            delete cr.did;
        }
    }

    for(i = 0; i !== deltaFile.formattedData.length; ++i) {
        cr = deltaFile.formattedData[i].contentReference;
        cr.segments = [{uuid: cr.uuid, offset: cr.offset, size: cr.size, did: cr.did}];
        delete cr.uuid;
        delete cr.offset;
        delete cr.size;
        delete cr.did;
    }

    db.deltaFile.update({_id: deltaFile._id}, deltaFile);
});
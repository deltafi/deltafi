db.deltaFile.find({"protocolStack.content": {$exists: false}}).forEach(
    function (doc) {
        for (const protocol of doc.protocolStack) {
            var content = {};
            content.contentReference = protocol.contentReference;
            protocol.content = [];
            protocol.content.push(content);
            delete protocol.contentReference;
        }
        db.deltaFile.save(doc);
    }
)

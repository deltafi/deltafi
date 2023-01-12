let convertMetadata = function (parent) {
    if (parent.metadata) {
        parent.metadata = new Map(parent.metadata.map((obj) => [obj.key, obj.value]));
        delete parent.metadata._data;
    }
}

db.deltaFile.find({"sourceInfo.metadata": {$type: "array"} }).forEach(function(deltaFile) {
    convertMetadata(deltaFile.sourceInfo);
    for (const formattedData of deltaFile.formattedData) {
        convertMetadata(formattedData);
    }
    for (const protocolLayer of deltaFile.protocolStack) {
        convertMetadata(protocolLayer);
        for (const content of protocolLayer.content) {
            convertMetadata(content);
        }
    }
    db.deltaFile.update({_id: deltaFile._id}, deltaFile);
});

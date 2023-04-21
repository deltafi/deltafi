db.deltaFile.updateMany({"protocolStack.content.metadata": {$exists: true}}, {$unset: {"protocolStack.content.metadata": ""}})

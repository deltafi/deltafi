db.deltaFile.updateMany({requeueCount: null}, {$set: {"requeueCount": NumberInt("0")}})

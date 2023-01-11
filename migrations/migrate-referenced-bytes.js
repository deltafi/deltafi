db.deltaFile.updateMany({referencedBytes: {$exists: false}}, [
    {"$set": {"referencedBytes": "$totalBytes"}}
])
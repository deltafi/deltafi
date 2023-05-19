db.deltaFile.updateMany(
    { "indexedMetadata": { $exists: true } },
    {
        $rename: {
            "indexedMetadata": "annotations",
            "indexedMetadataKeys": "annotationKeys"
        }
    }
);

db.deltaFile.updateMany({annotationKeys: { $exists: false }}, {$set: { annotationKeys: [] }});
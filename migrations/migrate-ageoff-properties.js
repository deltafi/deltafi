let oldPropertyCollectionExists = function() {
    return db.propertySet.count() > 0
}

let alreadyRun = function() {
    return db.propertySet.count({properties: {$elemMatch: {"key": "deltafi.delete.ageOffDays"}}}) > 0
}

let buildAgeOffProperty = function(value) {
    return {
        "key": "deltafi.delete.ageOffDays",
        "description": "Number of days that a DeltaFile should live, any records older will be removed.",
        "value": value.toString(),
        "defaultValue": "13",
        "hidden": false,
        "refreshable": true,
        "editable": true
    }
}

let readValue = function(properties, key) {
    let property = properties.find(property => property.key === key)
    if (property) {
        return property.value ? property.value : property.defaultValue
    }

    return null
}

let readValueOrDefault = function (properties, key, defaultValue) {
    let value = readValue(properties, key)
    return value != null ? value : defaultValue
}

let readDuration = function(value) {
    return parseInt(value.match(/\d+/)[0])
}

// create the new ageOffDays property from the existing TTLs using whichever value is most restrictive
let migratePropertySetValue = function() {
    let properties = db.propertySet.findOne({_id: "deltafi-common"})['properties']

    let metadataAgeOff = readValueOrDefault(properties, 'deltafi.deltaFileTtl', "13")
    let contentAgeOff = readValueOrDefault(properties, 'minio.expiration-days', "13")

    metadataAgeOff = readDuration(metadataAgeOff)
    contentAgeOff = parseInt(contentAgeOff)

    // use the most restrictive age off value
    let ageOffValue = contentAgeOff < metadataAgeOff ? contentAgeOff : metadataAgeOff
    let ageOffProperty = buildAgeOffProperty(ageOffValue)

    db.propertySet.updateOne({_id: "deltafi-common"}, {$push: {"properties": ageOffProperty}})
}

// check if the snapshot contained the TTLs, if so add the new ageOffDays property using the most restrictive value in the snapshot
let migrateSnapshotProperties = function(snapshot) {
    let propertySets = snapshot['propertySets']
    let commonProps = propertySets.find(ps => ps._id === "deltafi-common")

    if (commonProps == null) {
        return
    }

    let properties = commonProps['properties']

    let metadataAgeOff = readValue(properties, 'deltafi.deltaFileTtl')
    let contentAgeOff = readValue(properties, 'minio.expiration-days')


    let ageOffProperty = null
    if (metadataAgeOff != null) {
        let ageOff = readDuration(metadataAgeOff)
        if (contentAgeOff != null) {
            contentAgeOff = parseInt(contentAgeOff)
            ageOff = contentAgeOff < ageOff ? contentAgeOff : ageOff
        }

        ageOffProperty = buildAgeOffProperty(ageOff)
    } else if (contentAgeOff != null) {
        ageOffProperty = buildAgeOffProperty(contentAgeOff);
    }

    if (ageOffProperty != null) {
        properties.push(ageOffProperty)
        db.systemSnapshot.save(snapshot)
    }
}

let migrateSnapshots = function() {
    let snapshots = db.systemSnapshot.find()
    snapshots.forEach(snapshot => migrateSnapshotProperties(snapshot))
}

let runMigrations = function() {
    if (!oldPropertyCollectionExists()) {
        return;
    }

    if (alreadyRun()) {
        return;
    }
    migratePropertySetValue()
    migrateSnapshots()
}

runMigrations()

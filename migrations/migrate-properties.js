let defaultProperties = {
    "_id": "deltafi-properties",
    "systemName": "DeltaFi",
    "requeueSeconds": 300,
    "coreServiceThreads": 16,
    "scheduledServiceThreads": 32,
    "delete": {
        "ageOffDays": 13,
        "frequency": "PT10M",
        "policyBatchSize": 1000
    },
    "ingress": {
        "enabled": true,
        "diskSpaceRequirementInMb": NumberLong("1000")
    },
    "plugins": {
        "imageRepositoryBase": "docker.io/deltafi/"
    },
    "checks": {
        "actionQueueSizeThreshold": 10,
        "contentStoragePercentThreshold": 90
    },
    "setProperties": [],
    "_class": "org.deltafi.core.configuration.DeltaFiProperties"
}

let getCopyOfDefaultProperties = function() {
    let props = {...defaultProperties}
    let copyOfProps = {...defaultProperties}
    copyOfProps["delete"] = {...props["delete"]}
    copyOfProps["ingress"] = {...props["ingress"]}
    copyOfProps["plugins"] = {...props["plugins"]}
    copyOfProps["checks"] = {...props["checks"]}
    copyOfProps["setProperties"] = []
    return copyOfProps
}

let propertySetMapping = [
    {
        "propertySetKey": "deltafi.metrics.enabled",
        "propertyType": "METRICS_ENABLED",
        "setter": (deltaFiProps, value) => { deltaFiProps["metrics"]["enabled"] = value === 'true' }
    }, {
        "propertySetKey": "spring.task.scheduling.pool.size",
        "propertyType": "SCHEDULED_SERVICE_THREADS",
        "setter": (deltaFiProps, value) => { deltaFiProps["scheduledServiceThreads"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.scheduledServiceThreads",
        "propertyType": "SCHEDULED_SERVICE_THREADS",
        "setter": (deltaFiProps, value) => { deltaFiProps["scheduledServiceThreads"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.checks.actionQueue.sizeThreshold",
        "propertyType": "CHECKS_ACTION_QUEUE_SIZE_THRESHOLD",
        "setter": (deltaFiProps, value) => { deltaFiProps["checks"]["actionQueueSizeThreshold"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.checks.contentStorage.percentThreshold",
        "propertyType": "CHECKS_CONTENT_STORAGE_PERCENT_THRESHOLD",
        "setter": (deltaFiProps, value) => { deltaFiProps["checks"]["contentStoragePercentThreshold"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.requeueSeconds",
        "propertyType": "REQUEUE_SECONDS",
        "setter": (deltaFiProps, value) => { deltaFiProps["requeueSeconds"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.coreServiceThreads",
        "propertyType": "CORE_SERVICE_THREADS",
        "setter": (deltaFiProps, value) => { deltaFiProps["coreServiceThreads"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.delete.ageOffDays",
        "propertyType": "DELETE_AGE_OFF_DAYS",
        "setter": (deltaFiProps, value) => { deltaFiProps["delete"]["ageOffDays"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.delete.policyBatchSize",
        "propertyType": "DELETE_POLICY_BATCH_SIZE",
        "setter": (deltaFiProps, value) => { deltaFiProps["delete"]["policyBatchSize"] = parseInt(value) }
    }, {
        "propertySetKey": "deltafi.delete.frequency",
        "propertyType": "DELETE_FREQUENCY",
        "setter": (deltaFiProps, value) => { deltaFiProps["delete"]["frequency"] = value }
    }, {
        "propertySetKey": "deltafi.ingress.enabled",
        "propertyType": "INGRESS_ENABLED",
        "setter": (deltaFiProps, value) => { deltaFiProps["ingress"]["enabled"] = value === 'true' }
    }, {
        "propertySetKey": "deltafi.ingress.diskSpaceRequirementInMb",
        "propertyType": "INGRESS_DISK_SPACE_REQUIREMENT_IN_MB",
        "setter": (deltaFiProps, value) => { deltaFiProps["ingress"]["diskSpaceRequirementInMb"] = new NumberLong(value) }
    }, {
        "propertySetKey": "deltafi.plugins.imageRepositoryBase",
        "propertyType": "PLUGINS_IMAGE_REPOSITORY_BASE",
        "setter": (deltaFiProps, value) => { deltaFiProps["plugins"]["imageRepositoryBase"] = value }
    }, {
        "propertySetKey": "deltafi.plugins.imagePullSecret",
        "propertyType": "PLUGINS_IMAGE_PULL_SECRET",
        "setter": (deltaFiProps, value) => { deltaFiProps["plugins"]["imagePullSecret"] = value }
    }, {
        "propertySetKey": "deltafi.systemName",
        "propertyType": "SYSTEM_NAME",
        "setter": (deltaFiProps, value) => { deltaFiProps["systemName"] = value }
    }
];

let readValue = function (properties, key) {
    let property = properties.find(property => property.key === key)
    return property && property.value ? property.value : null
}

let buildProperties = function (propertySet) {
    let deltaFiProperties = getCopyOfDefaultProperties()
    let sourceProperties = propertySet['properties']

    propertySetMapping.forEach(mapping => {
        let value = readValue(sourceProperties, mapping["propertySetKey"])
        if (value != null) {
            mapping["setter"](deltaFiProperties, value)
            deltaFiProperties["setProperties"].push(mapping["propertyType"])
        }
    });
    return deltaFiProperties
}

migratePropertySet = function() {
    let propertySet = db.propertySet.findOne({"_id": "deltafi-common"})
    let deltaFiProps =  buildProperties(propertySet)
    db.deltaFiProperties.save(deltaFiProps)
}

migrateSnapshotProperties = function(snapshot) {
    let propertySets = snapshot['propertySets']
    let commonProps = propertySets.find(ps => ps._id === "deltafi-common")

    snapshot['deltaFiProperties'] = commonProps ? buildProperties(commonProps) : getCopyOfDefaultProperties()
    delete snapshot['propertySets']
    db.systemSnapshot.save(snapshot)
}

let migrateSnapshots = function() {
    let snapshots = db.systemSnapshot.find()
    snapshots.forEach(snapshot => migrateSnapshotProperties(snapshot))
}

runMigrations = function() {
    if(db.propertySet.countDocuments() > 0 && db.systemSnapshot.countDocuments({deltaFiProperties: {$exists: 0}}) > 0) {
        migratePropertySet()
        migrateSnapshots()
    }
}

runMigrations()

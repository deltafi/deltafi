let policyNameExists = function(value) {
    return db.resumePolicy.countDocuments({name: value}) > 0
}

let actionAlreadyExists = function(value) {
    return db.resumePolicy.countDocuments({flow: null, action: value}) > 0
}

let errorAlreadyExists = function(value) {
    return db.resumePolicy.countDocuments({flow: null, action: null, errorSubstring: value}) > 0
}

let checkNameAndAction = function(name, action) {
    if (policyNameExists(name)) {
        return;
    }

    if (actionAlreadyExists(action)) {
        return;
    }
    db.resumePolicy.insert({
        name: name,
        action: action,
        maxAttempts: 20,
        priority: 10,
        backOff: {
            delay: 60,
            maxDelay: 300,
            random: true
        }
    })
}

let checkNameAndError = function(name, error) {
    if (policyNameExists(name)) {
        return;
    }

    if (errorAlreadyExists(error)) {
        return;
    }
    db.resumePolicy.insert({
        name: name,
        errorSubstring: error,
        maxAttempts: 20,
        priority: 10,
        backOff: {
            delay: 60,
            maxDelay: 300,
            random: true
        }
    })
}

let runMigrations = function() {
    checkNameAndAction("System Default: No egress flow configured", "NoEgressFlowConfiguredAction")
    checkNameAndError("System Default: Storage read error", "Failed to load content from storage")
}

runMigrations()

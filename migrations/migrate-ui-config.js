let defaultUiProps = {"useUTC":true,"topBar":{},"securityBanner":{"enabled":false},"deltaFileLinks":[],"externalLinks":[]}

addUiPropsToDeltaFiProperties = function(uiProps, setProperties) {
    let deltaFiProps = db.deltaFiProperties.findOne()
    deltaFiProps['ui'] = uiProps
    deltaFiProps['setProperties'].push(...setProperties)
    db.deltaFiProperties.save(deltaFiProps)
}

addUIPropsToSnapshot = function(snapshot, uiProps, setProperties) {
    let deltaFiProps = snapshot['deltaFiProperties']
    deltaFiProps['ui'] = uiProps
    deltaFiProps['setProperties'].push(...setProperties)
    db.systemSnapshot.save(snapshot)
}

addIfSet = function(obj, key, propertyType, setProperties) {
    if (obj.hasOwnProperty(key)) {
        setProperties.push(propertyType)
    }
}

getSetProperties = function(uiConfig) {
    let setProperties = []

    addIfSet(uiConfig, 'useUTC', 'UI_USE_UTC', setProperties)

    let topBar = uiConfig['topBar'] || {}
    addIfSet(topBar, 'textColor', 'UI_TOP_BAR_TEXT_COLOR', setProperties)
    addIfSet(topBar, 'backgroundColor', 'UI_TOP_BAR_BACKGROUND_COLOR', setProperties)

    let securityBanner = uiConfig['securityBanner'] || {}
    addIfSet(securityBanner, 'enabled', 'UI_SECURITY_BANNER_ENABLED', setProperties)
    addIfSet(securityBanner, 'text', 'UI_SECURITY_BANNER_TEXT', setProperties)
    addIfSet(securityBanner, 'textColor', 'UI_SECURITY_BANNER_TEXT_COLOR', setProperties)
    addIfSet(securityBanner, 'backgroundColor', 'UI_SECURITY_BANNER_BACKGROUND_COLOR', setProperties)

    return setProperties
}

let addUiPropsToSnapshots = function(uiProps, setProperties) {
    db.systemSnapshot.find().forEach(snapshot => addUIPropsToSnapshot(snapshot, uiProps, setProperties))
}

addUiProps = function() {
    try {
        let uiConfigMapData = JSON.parse(cat('/tmp/migrations/ui-config.json'))
        let setProperties = getSetProperties(uiConfigMapData)

        // merge the default properties with values from the config map, configmap entries will overwrite defaults
        let uiConfig = {...defaultUiProps, ...uiConfigMapData}

        addUiPropsToDeltaFiProperties(uiConfig, setProperties)
        addUiPropsToSnapshots(uiConfig, setProperties)
    } catch (err) {
        print("No UI configuration was found to migrate")
    }
}

addUiProps()
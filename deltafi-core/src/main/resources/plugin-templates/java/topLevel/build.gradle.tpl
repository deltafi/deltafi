plugins {
    id 'org.deltafi.version-reckoning' version "${deltafiVersion}"
    id "org.deltafi.plugin-convention" version "${deltafiVersion}"
    id 'org.deltafi.test-summary' version "${deltafiVersion}"
}

group '{{packageName}}'

ext.pluginDescription = '{{description}}'

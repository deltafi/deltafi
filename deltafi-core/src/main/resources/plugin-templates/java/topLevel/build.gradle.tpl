plugins {
    id 'org.deltafi.version-reckoning' version "1.0"
    id 'org.deltafi.plugin-convention' version "${deltafiVersion}"
    id 'org.deltafi.test-summary' version "1.0"
}

group '{{packageName}}'

ext.pluginDescription = '{{description}}'

plugins {
    id 'org.deltafi.git-version' version "2.0.0"
    id 'org.deltafi.plugin-convention' version "${deltafiVersion}"
    id 'org.deltafi.test-summary' version "1.0"
}

group '{{packageName}}'

ext.pluginDescription = '{{description}}'

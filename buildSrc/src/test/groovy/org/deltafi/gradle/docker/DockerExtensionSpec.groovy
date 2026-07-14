package org.deltafi.gradle.docker

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DockerExtensionSpec extends Specification {

    DockerExtension extension = new DockerExtension(ProjectBuilder.builder().build())

    def "label adds a single entry"() {
        when:
        extension.label('org.deltafi.version', '1.0.0')

        then:
        extension.labels == ['org.deltafi.version': '1.0.0']
    }

    def "labels adds all entries preserving order"() {
        when:
        extension.labels(['org.deltafi.version': '1.0.0', 'maintainer': 'deltafi'])

        then:
        extension.labels == ['org.deltafi.version': '1.0.0', 'maintainer': 'deltafi']
    }

    def "labels appends to entries added via label"() {
        given:
        extension.label('a', '1')

        when:
        extension.labels(['b': '2', 'c': '3'])

        then:
        extension.labels == ['a': '1', 'b': '2', 'c': '3']
    }

    def "label rejects invalid key '#key'"() {
        when:
        extension.label(key, 'value')

        then:
        thrown(IllegalArgumentException)

        where:
        key << ['', 'Uppercase', 'has space', '.leading', 'trailing-', 'under_score']
    }

    def "label accepts valid key '#key'"() {
        when:
        extension.label(key, 'value')

        then:
        extension.labels[key] == 'value'

        where:
        key << ['a', 'org.deltafi.version', 'com.example.some-label', 'a1']
    }
}

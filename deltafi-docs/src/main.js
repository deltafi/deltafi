/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
import Docute from 'docute';

new Docute({
  target: '#docs',
  title: '',
  highlight: ['java', 'yaml'],
  sourcePath: 'docs',
  detectSystemDarkTheme: true,
  darkThemeToggler: true,
  headerBackground: '#2f3136',
  headerTextColor: '#ffffffe0',
  headerHeight: '66px',
  cssVariables(theme) {
    return theme === 'dark' ? { headerHeight: '66px', accentColor: 'rgb(12,123,192)', logo: 'url(\'./logo-dark.png\')' } : { headerHeight: '66px', accentColor: 'rgb(12,123,192)', logo: 'url(\'./logo.png\')' }
  },
  nav: [
    {
      title: 'Home',
      link: '/'
    },
    {
      title: 'GitLab',
      link: 'https://gitlab.com/deltafi/deltafi'
    },
    {
      title: 'DeltaFi.org',
      link: 'https://deltafi.org'
    }
  ],
  sidebar: [
    {
      title: 'Introduction',
      link: '/'
    },
    {
      title: 'Concepts',
      link: '/concepts'
    },
    {
      title: 'Getting Started',
      children: [
        {
          title: 'Getting Started with a Demo Cluster',
          link: '/getting-started/cluster'
        },
        {
          title: 'Getting Started Developing a Simple Plugin',
          link: '/getting-started/simple-plugin'
        }
      ]
    },
    {
      title: 'Installation',
      children: [
        {
          title: 'Prerequisites',
          link: '/install/prerequisites'
        },
        {
          title: 'Ansible Setup',
          link: '/install/ansible'
        },
        {
          title: 'Install Kubernetes',
          link: '/install/kubernetes'
        },
        {
          title: 'Install DeltaFi Core',
          link: '/install/core'
        },
        {
          title: 'Install Plugins',
          link: '/install/plugins'
        }
      ]
    },
    {
      title: 'Configuration',
      children: [
        {
          title: 'Authentication',
          link: '/config/authentication'
        }
      ]
    },
    {
      title: 'Plugins',
      children: [
        {
          title: 'Creating a Plugin',
          link: '/plugins'
        },
        {
          title: 'Actions',
          link: '/actions'
        },
        {
          title: 'Timed Ingress Action',
          link: '/actions/timed_ingress'
        },
        {
          title: 'Transform Action',
          link: '/actions/transform'
        },
        {
          title: 'Egress Action',
          link: '/actions/egress'
        },
        {
          title: 'Flows',
          link: '/flows'
        },
        {
          title: "Action Parameters",
          link: '/action_parameters'
        },
        {
          title: "Action Unit Testing",
          link: '/unit-test'
        }
      ]
    },
    {
      title: 'Core Actions',
      children: [
        {
          title: 'Annotate',
          link: '/core-actions/org.deltafi.core.action.annotate.Annotate'
        },
        {
          title: 'Compress',
          link: '/core-actions/org.deltafi.core.action.compress.Compress'
        },
        {
          title: 'Convert',
          link: '/core-actions/org.deltafi.core.action.convert.Convert'
        },
        {
          title: 'Decompress',
          link: '/core-actions/org.deltafi.core.action.compress.Decompress'
        },
        {
          title: 'Delay',
          link: '/core-actions/org.deltafi.core.action.delay.Delay'
        },
        {
          title: 'DeleteContent',
          link: '/core-actions/org.deltafi.core.action.delete.DeleteContent.md'
        },
        {
          title: 'Error',
          link: '/core-actions/org.deltafi.core.action.error.Error'
        },
        {
          title: 'ExtractJson',
          link: '/core-actions/org.deltafi.core.action.extract.ExtractJson'
        },
        {
          title: 'ExtractXml',
          link: '/core-actions/org.deltafi.core.action.extract.ExtractXml'
        },
        {
          title: 'Filter',
          link: '/core-actions/org.deltafi.core.action.filter.Filter'
        },
        {
          title: 'HttpEgress',
          link: '/core-actions/org.deltafi.core.action.egress.HttpEgress.md'
        },
        {
          title: 'JoltTransform',
          link: '/core-actions/org.deltafi.core.action.jolt.JoltTransform'
        },
        {
          title: 'Merge',
          link: '/core-actions/org.deltafi.core.action.merge.Merge'
        },
        {
          title: 'MetadataToContent',
          link: '/core-actions/org.deltafi.core.action.metadata.MetadataToContent'
        },
        {
          title: 'ModifyMediaType',
          link: '/core-actions/org.deltafi.core.action.mediatype.ModifyMediaType'
        },
        {
          title: 'ModifyMetadata',
          link: '/core-actions/org.deltafi.core.action.metadata.ModifyMetadata'
        },
        {
          title: 'Split',
          link: '/core-actions/org.deltafi.core.action.split.Split'
        },
        {
          title: 'XmlEditor',
          link: '/core-actions/org.deltafi.core.action.xml.XmlEditor'
        },
        {
          title: 'XsltTransform',
          link: '/core-actions/org.deltafi.core.action.xslt.XsltTransform'
        }
      ]
    },
    {
      title: 'Operating',
      children: [
        {
          title: 'Ingress',
          link: '/operating/ingress'
        },
        {
          title: 'CLI',
          link: '/operating/CLI'
        },
        {
          title: 'GUI',
          link: '/operating/GUI'
        },
        {
          title: 'Configuration',
          link: '/operating/configuration'
        },
        {
          title: 'Error Handling',
          link: '/operating/errors'
        },
        {
          title: 'Events API',
          link: '/operating/events_api'
        },
        {
          title: 'Metrics',
          link: '/operating/metrics'
        },
        {
          title: 'DeltaFile Analytics and Survey',
          link: '/operating/deltafile_analytics'
        },
      ]
    },
    {
      title: 'Advanced Topics',
      children: [
        {
          title: 'Architecture',
          link: '/advanced/architecture'
        },
        {
          title: 'Automatic Resume',
          link: '/advanced/auto_resume'
        },
        {
          title: 'Advanced Routing',
          link: '/advanced/advanced_routing'
        },
        {
          title: 'Data Retention',
          link: '/advanced/data_retention'
        },
        {
          title: 'DeltaFile Annotations',
          link: '/advanced/deltafile_annotations'
        },
        {
          title: 'Multithreading Java Actions',
          link: '/advanced/multithreading_java_action_kit'
        }
      ]
    },
    {
      title: 'KinD Cluster for Demo, Dev, and Test',
      link: '/kind'
    },
    {
      title: 'Contributing to Core Development',
      link: '/contributing'
    },
    {
      title: 'DeltaFi Changelog',
      link: '/CHANGELOG'
    }
  ]
})

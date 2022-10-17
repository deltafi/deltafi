/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
  title: 'DeltaFi Documentation',
  highlight: ['java', 'yaml'],
  sourcePath: 'docs',
  detectSystemDarkTheme: true,
  darkThemeToggler: true,
  nav: [
    {
      title: 'Home',
      link: '/'
    },
    {
      title: 'GitLab',
      link: 'https://gitlab.com/systolic/deltafi/deltafi'
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
      title: 'Plugins',
      children: [
        {
          title: 'Building Plugins',
          link: '/plugins'
        },
        {
          title: 'Actions',
          link: '/actions'
        },
        {
          title: 'Transform Action',
          link: '/actions/transform'
        },
        {
          title: 'Load Action',
          link: '/actions/load'
        },
        {
          title: 'Domain Action',
          link: '/actions/domain'
        },
        {
          title: 'Enrich Action',
          link: '/actions/enrich'
        },
        {
          title: 'Format Action',
          link: '/actions/format'
        },
        {
          title: 'Validate Action',
          link: '/actions/validate'
        },
        {
          title: 'Egress Action',
          link: '/actions/egress'
        },
        {
          title: 'Flows',
          link: '/flows'
        }
      ]
    },
    {
      title: 'Operating',
      children: [
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
          title: 'Advanced Routing',
          link: '/advanced/advanced_routing'
        },
        {
          title: 'Cluster Integration Testing with KinD',
          link: '/advanced/kind'
        }
      ]
    }
  ]
})

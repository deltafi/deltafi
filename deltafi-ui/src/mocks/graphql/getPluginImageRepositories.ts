/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

const generateData = () => {
  const data = {
    getPluginImageRepositories: [
      {
        "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/deltafi-json-validation/",
        "pluginGroupIds": [
          "org.deltafi.jsonvalidations"
        ],
        "imagePullSecret": "docker-secret"
      },
      {
        "imageRepositoryBase": "docker-images.com/images",
        "pluginGroupIds": [
          "org.ray.stuff"
        ],
        "imagePullSecret": "docker-secret"
      },
      {
        "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/plugins/deltafi-passthrough/",
        "pluginGroupIds": [
          "org.deltafi.passthrough"
        ],
        "imagePullSecret": "docker-secret"
      },
      {
        "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/deltafi-stix/",
        "pluginGroupIds": [
          "org.deltafi.stix"
        ],
        "imagePullSecret": "docker-secret"
      },
      {
        "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/plugins/deltafi-testjig/",
        "pluginGroupIds": [
          "org.deltafi.testjig"
        ],
        "imagePullSecret": "docker-secret"
      },
      {
        "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/plugins/deltafi-xml/",
        "pluginGroupIds": [
          "org.deltafi.xml"
        ],
        "imagePullSecret": "docker-secret"
      },
      {
        "imageRepositoryBase": "localhost:31333/",
        "pluginGroupIds": [
          "local"
        ],
        "imagePullSecret": null
      },
      {
        "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/plugins/deltafi-java-hello-world/",
        "pluginGroupIds": [
          "org.deltafi.helloworld"
        ],
        "imagePullSecret": "docker-secret"
      },
      {
        "imageRepositoryBase": "registry.gitlab.com/systolic/deltafi/plugins/deltafi-python-hello-world/",
        "pluginGroupIds": [
          "org.deltafi.python-hello-world"
        ],
        "imagePullSecret": "docker-secret"
      }
    ]
  };

  return data;
};

export default generateData();

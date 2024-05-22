stages:
  - build
  - docker
  - ci

default:
  timeout: 45m

services:
  - name: docker:18.09.7-dind
    entrypoint: ["dockerd-entrypoint.sh"]
    command: ["--max-concurrent-downloads", "10"]

.gradle:
  image: deltafi/deltafi-build:jdk17
  variables:
    GRADLE_USER_HOME: /cache/.gradle.${CI_CONCURRENT_ID}
    GRADLE_OPTS: "-Dorg.gradle.daemon=false"
    GRADLE: "gradle -s --no-daemon --build-cache -PgitLabTokenType=Job-Token -PgitLabToken=${CI_JOB_TOKEN}"

# Main build job (builds all the gradle things)
build:
  extends:
    - .gradle
  stage: build
  script:
    - ${GRADLE} --rerun-tasks assemble test
  artifacts:
    when: always
    reports:
      junit: "**/build/test-results/test/**/TEST-*.xml"
    paths:
      - "**/build"
      - ".gradle"

docker:
  stage: docker
  needs:
    - job: "build"
  image: docker:latest
  variables:
    PROJECT_NAME: {{projectName}}
    DOCKER_TAG: ${CI_REGISTRY_IMAGE}/${PROJECT_NAME}:${CI_COMMIT_SHA}
  script:
    - export DOCKER_NAMED_TAG=${CI_REGISTRY_IMAGE}/${PROJECT_NAME}:${CI_COMMIT_REF_NAME//\//_}
    - docker login -u gitlab-ci-token -p $CI_JOB_TOKEN registry.gitlab.com
    - echo "Building the following image ${DOCKER_NAMED_TAG}"
    - echo "Building the following image ${DOCKER_TAG}"
    # pull latest image if available (performance optimization)
    - docker pull ${DOCKER_NAMED_TAG} || true
    - >
      docker build --file build/Dockerfile
      --build-arg VCS_REF=$CI_COMMIT_SHA
      --build-arg VCS_URL=$CI_PROJECT_URL
      --cache-from ${DOCKER_NAMED_TAG}
      --tag ${DOCKER_TAG}
      .
    - docker push ${DOCKER_TAG}
    - docker tag ${DOCKER_TAG} ${DOCKER_NAMED_TAG}
    - docker push ${DOCKER_NAMED_TAG}
    - docker images

# To enable CD add the appropriate DELTAFI_CD_SYSTEM setting and variables to the repo for $DELTAFI_USERNAME and $DELTAFI_PASSWORD
.upgrade-plugin:
  stage: ci
  only:
    refs:
      - main
  image: badouralix/curl-jq
  variables:
    PROJECT_NAME: {{projectName}}
    DELTAFI_CD_SYSTEM: deltafi-cd-system
    GROUP_ID: {{packageName}}
  script: |-
    echo "Installing $GROUP_ID:$PROJECT_NAME:$CI_COMMIT_SHA as $DELTAFI_USERNAME on https://$DELTAFI_CD_SYSTEM"

    TMPFILE="/tmp/.deltafi-install-plugin"
    POST_QUERY='{ "query": "mutation { installPlugin(pluginCoordinates: { groupId: \"'$GROUP_ID'\" artifactId: \"'$PROJECT_NAME'\" version: \"'${CI_COMMIT_SHA}'\"}) { success info errors }}","variables":null}'
    RESPONSE_CODE=$(curl -s -u "$DELTAFI_USERNAME:$DELTAFI_PASSWORD" -X POST -o ${TMPFILE} -w "%{http_code}" -H "Content-Type: application/json" -d "$POST_QUERY" https://$DELTAFI_CD_SYSTEM/api/v2/graphql)

    if [[ "$RESPONSE_CODE" != "200" ]]; then
      echo -e "${RESPONSE_CODE} Error: $(cat ${TMPFILE})"
      exit 1
    fi

    SUCCESS=$(cat ${TMPFILE} | jq '.data.installPlugin.success // false')
    if [[ $SUCCESS == "true" ]]; then
      echo -e "Successfully upgrade to $GROUP_ID:$PROJECT_NAME:$CI_COMMIT_SHA"
      exit 0
    else
      ERRORS=$(cat ${TMPFILE} | jq '.errors // .data.installPlugin.errors')
      ERRORS=$(cat ${TMPFILE} | jq '.errors // .data.installPlugin.errors')
      echo "Failed to upgrade to $GROUP_ID:$PROJECT_NAME:$CI_COMMIT_SHA"
      echo "$ERRORS"
      exit 1
    fi

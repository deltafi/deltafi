include:
  - local: .gitlab-ci.plugins.yml

# -------------------------------------
build and test:
  extends:
    - .gradle-build
  variables:
    GRADLE_TASKS: assemble test

🐳 Plugin image (GitLab):
  extends:
    - .docker-publish-gitlab
  needs:
    - job: "build and test"
  rules:
    - if: $CI_COMMIT_TAG
      when: never
    - if: $CI_COMMIT_BRANCH
  variables:
    DOCKER_BUILD_FLAGS: --target release
    DOCKERFILE: Dockerfile

🐳 Plugin image (docker.io):
  extends:
    - .docker-multiarch
  needs:
    - job: "build and test"
  rules:
    - if: $CI_COMMIT_TAG
      variables:
        DOCKER_TAG: $CI_COMMIT_TAG
  variables:
    DOCKER_BUILD_FLAGS: --platform ${DOCKER_PLATFORMS} --sbom=true --target release
    DOCKERFILE: Dockerfile

# -------------------------------------
install on dev:
  extends:
    .dev-install

# -------------------------------------
# integration-test:example:
#   extends:
#     - .integration-test
#   variables:
#     TEST_NAME: example.example-test
#   needs:
#     - job: "install on dev"


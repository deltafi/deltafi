include:
  - local: .gitlab-ci.plugins.yml

# -------------------------------------
build and test:
  extends:
    - .gradle-build
  variables:
    GRADLE_TASKS: assemble test

# -------------------------------------
dev:kaniko:
  extends:
    - .kaniko
  needs:
    - job: "build and test"
  rules:
    - if: $CI_COMMIT_TAG
      when: never
    - if: $CI_COMMIT_BRANCH
  variables:
    DOCKERFILE: build/Dockerfile

# -------------------------------------
release:gitlab:
  extends:
    - .docker-publish-gitlab
  needs:
    - job: "build and test"
  rules:
    - if: $CI_COMMIT_TAG
      variables:
        DOCKER_TAG: $CI_COMMIT_TAG
  variables:
    DOCKERFILE: build/Dockerfile

# -------------------------------------
release:dockerhub:
  extends:
    - .docker-publish-dockerhub
  needs:
    - job: "build and test"
  rules:
    - if: $CI_COMMIT_TAG
      variables:
        DOCKER_TAG: $CI_COMMIT_TAG
  variables:
    DOCKERFILE: build/Dockerfile

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


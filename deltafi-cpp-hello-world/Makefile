#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.

.DEFAULT_GOAL := build
APP_NAME := deltafi-cpp-hello-world
DOCKER_REPO := deltafi/deltafi-cpp-hello-world
DESCRIPTION := C++ Hello World reference plugin for DeltaFi
ADDITIONAL_DOCKER_ARGS ?=
SBOM ?= true

GIT_REPO := https://gitlab.com/deltafi/deltafi.git

# Build info
BUILD_DIR ?= $(realpath $(CURDIR)/..)
VERSION ?= alpha

# Action kit source: set DELTAFI_ACTION_KIT_PATH to use a local copy,
# otherwise the action kit is cloned from git at ACTION_KIT_VERSION.
# Set LOCAL_ACTION_KIT=true to auto-detect a sibling deltafi repo.
DELTAFI_ACTION_KIT_PATH ?=
ACTION_KIT_VERSION ?= local
LOCAL_ACTION_KIT ?=

# ifeq ($(LOCAL_ACTION_KIT),true)
ifeq (true,true)
	ACTION_KIT_VERSION := local
  ifndef DELTAFI_ACTION_KIT_PATH
    ifneq (,$(wildcard $(CURDIR)/../deltafi/deltafi-cpp-action-kit/CMakeLists.txt))
      DELTAFI_ACTION_KIT_PATH := $(realpath $(CURDIR)/../deltafi/deltafi-cpp-action-kit)
    else ifneq (,$(wildcard $(CURDIR)/../../deltafi/deltafi-cpp-action-kit/CMakeLists.txt))
      DELTAFI_ACTION_KIT_PATH := $(realpath $(CURDIR)/../../deltafi/deltafi-cpp-action-kit)
    else
      $(error LOCAL_ACTION_KIT=true but cannot find deltafi-cpp-action-kit in ../deltafi/ or ../../deltafi/)
    endif
  endif
endif

# Docker build staging directory
DOCKER_BUILD_DIR := $(CURDIR)/.docker-build

HAS_DOCKER := $(shell command -v docker 2> /dev/null)

.PHONY: all build test clean docker docker-build docker-tag publish install help stage-docker

all: build

build:
ifdef DELTAFI_ACTION_KIT_PATH
	cmake -B build \
		-DDELTAFI_ACTION_KIT_PATH=$(DELTAFI_ACTION_KIT_PATH) \
		-DVERSION=$(VERSION)
else
	cmake -B build \
		-DVERSION=$(VERSION) \
		-DDELTAFI_ACTION_KIT_TAG=$(ACTION_KIT_VERSION)
endif
	cmake --build build -j

USE_DOCKER ?=

ifeq ($(USE_DOCKER),true)
test: stage-docker
	docker build -f Dockerfile \
		--build-arg VERSION=$(VERSION) \
		--target tester \
		$(DOCKER_BUILD_DIR)
else
test: build
	ctest --test-dir build --output-on-failure
endif

clean:
	rm -rf build $(DOCKER_BUILD_DIR)

help:
	@echo "Version:             $(VERSION)"
	@echo "Action Kit Version:  $(ACTION_KIT_VERSION)"
	@echo "Action Kit Path:     $(or $(DELTAFI_ACTION_KIT_PATH),(not set - will clone from git))"
	@echo ""
	@echo "Available targets:"
	@echo "  build            - Build binary locally"
	@echo "  test             - Run unit tests"
	@echo "  clean            - Remove build artifacts"
	@echo "  docker           - Build Docker image (tagged :latest and :VERSION)"
	@echo "  docker-build     - Build Docker image (tagged :latest)"
	@echo "  docker-tag       - Tag Docker image with version"
	@echo "  publish          - Build and push multi-arch Docker image"
	@echo "  install          - Build Docker image and install plugin"
	@echo ""
	@echo "Build options:"
	@echo "  VERSION=x.y.z                  - Plugin and action kit version"
	@echo "  DELTAFI_ACTION_KIT_PATH=/path   - Use local action kit (skips git clone)"
	@echo "  LOCAL_ACTION_KIT=true           - Auto-detect local action kit in ../deltafi/ or ../../deltafi/"
	@echo "  ACTION_KIT_VERSION=tag          - Git tag to clone (default: VERSION)"
	@echo "  USE_DOCKER=true                - Run tests in Docker instead of native toolchain"

# Stage the docker build context with action kit and plugin source
stage-docker:
	@rm -rf $(DOCKER_BUILD_DIR)
	@mkdir -p $(DOCKER_BUILD_DIR)
ifdef DELTAFI_ACTION_KIT_PATH
	@echo "Using local action kit: $(DELTAFI_ACTION_KIT_PATH)"
	@cp -r $(DELTAFI_ACTION_KIT_PATH) $(DOCKER_BUILD_DIR)/deltafi-cpp-action-kit
	@git -C $(DELTAFI_ACTION_KIT_PATH) describe --tags --always > \
		$(DOCKER_BUILD_DIR)/deltafi-cpp-action-kit/VERSION 2>/dev/null || true
else
	@echo "Cloning action kit at tag $(ACTION_KIT_VERSION)..."
	@git clone --depth 1 --branch $(ACTION_KIT_VERSION) \
		--filter=blob:none --sparse \
		$(GIT_REPO) $(DOCKER_BUILD_DIR)/.clone
	@cd $(DOCKER_BUILD_DIR)/.clone && \
		git sparse-checkout set deltafi-cpp-action-kit
	@mv $(DOCKER_BUILD_DIR)/.clone/deltafi-cpp-action-kit $(DOCKER_BUILD_DIR)/deltafi-cpp-action-kit
	@echo "$(ACTION_KIT_VERSION)" > $(DOCKER_BUILD_DIR)/deltafi-cpp-action-kit/VERSION
	@rm -rf $(DOCKER_BUILD_DIR)/.clone
endif
	@cp -r $(CURDIR) $(DOCKER_BUILD_DIR)/deltafi-cpp-hello-world
	@rm -rf $(DOCKER_BUILD_DIR)/deltafi-cpp-hello-world/.docker-build \
		$(DOCKER_BUILD_DIR)/deltafi-cpp-hello-world/build

# Docker targets
docker: docker-build docker-tag

docker-build: stage-docker
	@echo "Building Docker image $(DOCKER_REPO):latest"
	docker build -f Dockerfile \
		--build-arg VERSION=$(VERSION) \
		-t $(DOCKER_REPO):latest \
		$(DOCKER_BUILD_DIR)

docker-tag: docker-build
	@echo "Tagging $(DOCKER_REPO):$(VERSION)"
	docker tag $(DOCKER_REPO):latest $(DOCKER_REPO):$(VERSION)

publish: stage-docker
	@echo "Publishing docker image for $(DOCKER_REPO):$(VERSION)"
	docker buildx build --no-cache $(ADDITIONAL_DOCKER_ARGS) \
		--build-arg VERSION=$(VERSION) \
		--provenance=$(SBOM) --sbom=$(SBOM) \
		--push --platform linux/amd64,linux/arm64 \
		--tag $(DOCKER_REPO):$(VERSION) \
		$(DOCKER_BUILD_DIR) -f Dockerfile

install: docker
	@echo "Installing $(DOCKER_REPO):$(VERSION)"
	deltafi plugin install --wait "$(DOCKER_REPO):$(VERSION)"

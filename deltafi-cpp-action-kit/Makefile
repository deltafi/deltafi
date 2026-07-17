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

.DEFAULT_GOAL := test

CLANG_FORMAT ?= clang-format
CLANG_TIDY ?= clang-tidy
LINT_IMAGE ?= silkeh/clang:21
CPP_SOURCES := $(shell find include tests \( -name '*.hpp' -o -name '*.cpp' \) 2>/dev/null)
TIDY_SOURCES := $(shell find tests -name '*.cpp' 2>/dev/null)

# Static-analysis tools run natively when installed, else inside the CI
# container ($(LINT_IMAGE)) so results match CI without a host toolchain.
HAS_CLANG_FORMAT := $(shell command -v $(CLANG_FORMAT) 2>/dev/null)
HAS_CLANG_TIDY := $(shell command -v $(CLANG_TIDY) 2>/dev/null)

# On macOS, a native (Homebrew) clang-tidy needs the Xcode SDK sysroot to find
# system headers (e.g. TargetConditionals.h); the container path does not.
ifeq ($(shell uname -s),Darwin)
  TIDY_EXTRA := --extra-arg=-isysroot --extra-arg=$(shell xcrun --show-sdk-path 2>/dev/null)
endif

# Builder-base image: toolchain + kit source, one image per libc flavor. `docker` builds locally
# (tagging both the git-describe version and a moving latest-<flavor>); `publish` pushes only the
# git-describe version to $(REGISTRY) for both flavors.
REGISTRY ?= docker.io/deltafi
IMAGE_NAME ?= cpp-action-kit
IMAGE := $(REGISTRY)/$(IMAGE_NAME)
VERSION ?= $(shell git describe --tags --always --dirty=-local 2>/dev/null || echo latest)
BUILD_FLAVORS := glibc musl
BUILDX_BUILDER ?= deltafi-cpp-plugin
SBOM ?= true
# BuildKit registry layer cache for `publish` (pushes intermediate layers mode=max and reuses them
# on the next publish). CACHE=true (default) enables it; set CACHE=false to disable.
CACHE ?= true
PUSH ?=

.PHONY: build test clean help fmt format-check lint analyze docker publish buildx-builder trivy grype scan

build:
	cmake -B build -DBUILD_TESTING=ON
	cmake --build build -j

test: build
	ctest --test-dir build --output-on-failure

fmt:
ifdef HAS_CLANG_FORMAT
	@echo "Formatting (local: $(HAS_CLANG_FORMAT))"
	@$(CLANG_FORMAT) -i --style=file $(CPP_SOURCES)
else
	@echo "clang-format not found locally; running in $(LINT_IMAGE)"
	@docker run --rm -v $(CURDIR):$(CURDIR) -w $(CURDIR) $(LINT_IMAGE) \
		clang-format -i --style=file $(CPP_SOURCES)
endif

format-check:
ifdef HAS_CLANG_FORMAT
	@$(CLANG_FORMAT) --dry-run --Werror --style=file $(CPP_SOURCES)
else
	@echo "clang-format not found locally; running in $(LINT_IMAGE)"
	@docker run --rm -v $(CURDIR):$(CURDIR) -w $(CURDIR) $(LINT_IMAGE) \
		clang-format --dry-run --Werror --style=file $(CPP_SOURCES)
endif

lint:
ifdef HAS_CLANG_TIDY
	@echo "Running clang-tidy (local: $(HAS_CLANG_TIDY))"
	@rm -rf build-lint
	@cmake -B build-lint -DBUILD_TESTING=ON >/dev/null
	@$(CLANG_TIDY) -p build-lint --config-file=.clang-tidy $(TIDY_EXTRA) $(TIDY_SOURCES)
else
	@echo "clang-tidy not found locally; running in $(LINT_IMAGE)"
	@docker run --rm -v $(CURDIR):$(CURDIR) -w $(CURDIR) $(LINT_IMAGE) bash -c '\
		apt-get update >/dev/null 2>&1 && apt-get install -y --no-install-recommends cmake make git libssl-dev >/dev/null 2>&1 && \
		rm -rf build-lint && cmake -B build-lint -DBUILD_TESTING=ON >/dev/null && \
		clang-tidy -p build-lint --config-file=.clang-tidy $(TIDY_SOURCES)'
endif

analyze: format-check lint

clean:
	rm -rf build build-lint

# Build both flavors locally, tagging the git-describe version and a moving latest-<flavor>.
docker:
	@for flavor in $(BUILD_FLAVORS); do \
		echo ">> Building $(IMAGE):$(VERSION)-$$flavor (and latest-$$flavor)"; \
		docker build -f Dockerfile $(PUSH) \
			--build-arg BUILD_FLAVOR=$$flavor \
			--build-arg VERSION=$(VERSION) \
			-t $(IMAGE):$(VERSION)-$$flavor \
			-t $(IMAGE):latest-$$flavor \
			. || exit 1; \
	done

# Build both flavors multi-arch and push only the git-describe version (never latest) to $(REGISTRY).
publish: buildx-builder
	@for flavor in $(BUILD_FLAVORS); do \
		CACHE_ARGS=""; \
		if [ "$(CACHE)" = "true" ]; then \
			CACHE_ARGS="--cache-from type=registry,ref=$(IMAGE):buildcache-$$flavor --cache-to type=registry,ref=$(IMAGE):buildcache-$$flavor,mode=max"; \
		fi; \
		echo ">> Publishing $(IMAGE):$(VERSION)-$$flavor to $(REGISTRY)"; \
		docker buildx build --builder $(BUILDX_BUILDER) $$CACHE_ARGS \
			--build-arg BUILD_FLAVOR=$$flavor \
			--build-arg VERSION=$(VERSION) \
			--provenance=$(SBOM) --sbom=$(SBOM) \
			--push --platform linux/amd64,linux/arm64 \
			-t $(IMAGE):$(VERSION)-$$flavor \
			. || exit 1; \
	done

buildx-builder:
	@docker buildx inspect $(BUILDX_BUILDER) >/dev/null 2>&1 || docker buildx create --name $(BUILDX_BUILDER) --driver docker-container >/dev/null

# Vulnerability scans of the built builder-base images (build them first with `make docker`). Both
# gate on fixable CVEs only: trivy via trivy.yaml (ignore-unfixed), grype via --only-fixed. These
# are build-only images, so unfixed toolchain CVEs (binutils/linux-libc-dev) are expected and not
# actionable; see trivy.yaml for the rationale.
trivy:
	@command -v trivy >/dev/null 2>&1 || { echo "ERROR: trivy not installed (brew install trivy)"; exit 1; }
	@for flavor in $(BUILD_FLAVORS); do \
		echo ">> trivy $(IMAGE):$(VERSION)-$$flavor (fixable only)"; \
		trivy image --config trivy.yaml $(IMAGE):$(VERSION)-$$flavor || exit 1; \
	done

grype:
	@command -v grype >/dev/null 2>&1 || { echo "ERROR: grype not installed (brew install grype)"; exit 1; }
	@for flavor in $(BUILD_FLAVORS); do \
		echo ">> grype $(IMAGE):$(VERSION)-$$flavor (fixable only)"; \
		grype $(IMAGE):$(VERSION)-$$flavor --only-fixed || exit 1; \
	done

scan: trivy grype

help:
	@echo "Available targets:"
	@echo "  build         - Configure and build tests"
	@echo "  test          - Build and run unit tests (default)"
	@echo "  fmt           - Format sources with clang-format (native or $(LINT_IMAGE))"
	@echo "  format-check  - Check formatting without modifying files"
	@echo "  lint          - Run clang-tidy static analysis (native or $(LINT_IMAGE))"
	@echo "  analyze       - format-check + lint"
	@echo "  clean         - Remove build artifacts"
	@echo "  docker        - Build builder-base images locally ($(IMAGE):$(VERSION)-{glibc,musl} + latest-*)"
	@echo "  publish       - Build multi-arch and push $(IMAGE):$(VERSION)-{glibc,musl} to $(REGISTRY)"
	@echo "  trivy         - Scan the built images with trivy (fixable CVEs only)"
	@echo "  grype         - Scan the built images with grype (fixable CVEs only)"
	@echo "  scan          - Run both trivy and grype"
	@echo ""
	@echo "Options:"
	@echo "  CLANG_FORMAT=<path> / CLANG_TIDY=<path>  - use a specific tool binary"
	@echo "  CACHE=true|false                         - registry layer cache on publish (default: true)"
	@echo "  LINT_IMAGE=<image>                       - container image for fallback (default: $(LINT_IMAGE))"
	@echo "  REGISTRY=<host/namespace>                - image registry (default: $(REGISTRY))"
	@echo "  VERSION=<tag>                            - image version tag (default: git describe)"

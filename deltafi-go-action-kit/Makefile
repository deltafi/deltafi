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

# Build info
BUILD_DIR ?= $(realpath $(CURDIR)/..)
VERSION ?= $(shell cat ${BUILD_DIR}/VERSION)

# Auto-detect if go is installed
HAS_GO := $(shell command -v go 2> /dev/null)
USE_DOCKER ?= $(if $(HAS_GO),false,true)

# Environment checks
NATIVE_ARCH ?= $(strip $(shell uname -m | tr A-Z a-z))
ifneq ($(NATIVE_ARCH),arm64)
  ifneq ($(NATIVE_ARCH),amd64)
    NATIVE_ARCH := amd64
  endif
endif

NATIVE_OS := $(shell basename $(shell uname -o | tr A-Z a-z))
ifneq ($(NATIVE_OS),darwin)
  NATIVE_OS := linux
endif

HAS_DOCKER := $(shell command -v docker 2> /dev/null)
MIN_GO_VERSION := 1.26
GO_VERSION_CHECK := $(shell go version 2>/dev/null | grep -q "go$(MIN_GO_VERSION)" && echo 1 || echo 0)

GOPATH ?= $(CURDIR)/.go
export GOPATH

GOBINPATH = $(GOPATH)/bin

# Docker settings
DOCKER_IMAGE := golang:1.26-alpine
DOCKER_SOURCE := /go/src/deltafi
DOCKER_WORKDIR := $(DOCKER_SOURCE)/deltafi-go-action-kit
DOCKER_GOPATH := $(DOCKER_WORKDIR)/.go

DOCKER_USER := $(shell id -u):$(shell id -g)

DOCKER_CMD := docker run --rm \
              --user $(DOCKER_USER) \
              -v $(BUILD_DIR):$(DOCKER_SOURCE) \
              -v $(GOPATH):$(DOCKER_GOPATH) \
              -e GOPATH=$(DOCKER_GOPATH) \
              -e GOFLAGS=-modcacherw \
              -e CGO_ENABLED=0 \
              -e GOCACHE=/tmp/.cache \
              -w $(DOCKER_WORKDIR)

DOCKER_TOOLING_CMD := docker run --rm --platform linux/$(NATIVE_ARCH) \
              --user $(DOCKER_USER) \
              -v $(BUILD_DIR):$(DOCKER_SOURCE) \
              -v $(GOPATH):$(DOCKER_GOPATH) \
              -e GOPATH=$(DOCKER_GOPATH) \
              -e GOFLAGS=-modcacherw \
              -e CGO_ENABLED=0 \
              -e GOCACHE=/tmp/.cache \
              -w $(DOCKER_WORKDIR)

# Conditionally prefix commands with docker
ifeq ($(USE_DOCKER),true)
  ifeq ($(HAS_DOCKER),)
    $(error Docker or golang installation is required)
  endif
  CMD_PREFIX := $(DOCKER_CMD) $(DOCKER_IMAGE)
  TOOLING_CMD_PREFIX := $(DOCKER_TOOLING_CMD) $(DOCKER_IMAGE)
  GOBINPATH := $(DOCKER_GOPATH)/bin
else
  ifeq ($(HAS_GO),false)
    $(error Go installation or docker is required)
  endif
  ifeq ($(GO_VERSION_CHECK), 0)
    $(warning Go $(MIN_GO_VERSION) or higher is required. Consider using USE_DOCKER=true)
  endif
  CMD_PREFIX :=
  TOOLING_CMD_PREFIX :=
  GOBINPATH := $(GOPATH)/bin
endif

# Go commands
GOCMD     = $(CMD_PREFIX) go
GOTOOLCMD = $(TOOLING_CMD_PREFIX) go
GOBUILD   = $(GOCMD) build -modcacherw
GOCLEAN   = $(GOCMD) clean
GOTEST    = $(GOTOOLCMD) test -modcacherw
GOGET     = $(GOCMD) get -modcacherw
GOVET     = $(GOCMD) vet -modcacherw
GOFMT     = $(GOCMD) fmt -modcacherw
GOFIX     = $(GOCMD) fix -modcacherw
GOTIDY    = $(GOCMD) mod tidy -modcacherw
GOLINT    = $(CMD_PREFIX) $(GOBINPATH)/golangci-lint
GOINSTALL = $(GOCMD) install -modcacherw

# Extra modules
LINT_MODULE = github.com/golangci/golangci-lint/cmd/golangci-lint@latest

.PHONY: all build clean test lint fmt fix vet deps tidy very-clean help check-env

all: check-env fix vet fmt build test

check-env:
	@if [ "$(USE_DOCKER)" = "false" ] && [ "$(GO_VERSION_CHECK)" = "0" ]; then \
		echo "WARNING: Go $(MIN_GO_VERSION) is required. Consider using USE_DOCKER=true"; \
	fi
	@if [ "$(USE_DOCKER)" = "true" ]; then \
		echo "Using Docker GO environment [ $(NATIVE_OS) $(NATIVE_ARCH) ]"; \
	fi
	@mkdir -p $(GOPATH)

help:
	@echo "Version:     " $(VERSION)
	@echo ""
	@echo "Build environment:"
	@echo "  Has Go:      " $(if $(HAS_GO),yes,no)
	@echo "  Has Docker:  " $(if $(HAS_DOCKER),yes,no)
	@echo "  Using Docker:" $(USE_DOCKER)
	@echo "  GOPATH:      " $(GOPATH)
	@echo ""
	@echo "Available targets:"
	@echo "  all              - Build and run tests"
	@echo "  build            - Build library"
	@echo "  clean            - Remove build artifacts"
	@echo "  very-clean       - Remove build artifacts and cached modules"
	@echo "  test             - Run tests"
	@echo "  lint             - Run linter and vet"
	@echo "  fmt              - Format code"
	@echo "  tidy             - Format code and tidy modules"
	@echo "  deps             - Install dependencies"
	@echo ""
	@echo "Build options:"
	@echo "  USE_DOCKER=true|false - Use Docker for building (auto-detected: $(USE_DOCKER))"

build: check-env fmt fix
	@echo "Building deltafi-go-action-kit"
	@$(GOBUILD) ./...

test: check-env
	@echo "Running all tests"
	@$(GOTEST) -v ./...

clean:
	$(GOCLEAN)

very-clean: clean
	$(GOCLEAN) -modcache
	rm -rf $(GOPATH)

lint: deps vet fix fmt
	@echo "Running static analysis"
	@$(GOLINT) run

vet:
	@$(GOVET) ./...

fix:
	@$(GOFIX) ./...

fmt:
	@$(GOFMT) ./...

tidy: fmt
	@$(GOTIDY)

deps:
	@mkdir -p $(GOPATH)
	@$(GOGET) ./...
	@$(GOINSTALL) $(LINT_MODULE)

.PHONY: upgrade
upgrade: check-env
	@$(GOGET) ./...
	@$(GOTIDY)
	@$(GOVET) ./...
	@$(GOFIX) ./...
	@$(GOFMT) ./...

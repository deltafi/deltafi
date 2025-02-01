#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
APP_NAME := deltafi2

# Binary names
BUILD_DIR ?= $(realpath $(CURDIR)/..)
BIN_DIR ?= $(realpath $(BUILD_DIR)/../..)

# Build info
VERSION ?= $(shell cat ${BUILD_DIR}/VERSION)
BUILD_TIME := $(shell date -u '+%Y-%m-%d_%H:%M:%S')
LDFLAGS := -ldflags "-X main.Version=${VERSION} -X main.BuildTime=${BUILD_TIME} -w -s" -modcacherw

# Auto-detect if go is installed
HAS_GO := $(shell command -v go 2> /dev/null)
USE_DOCKER ?= $(if $(HAS_GO),false,true)

# Environment checks
HAS_DOCKER := $(shell command -v docker 2> /dev/null)
MIN_GO_VERSION := 1.23
GO_VERSION_CHECK := $(shell go version 2>/dev/null | grep -q "go$(MIN_GO_VERSION)" && echo 1 || echo 0)

GOPATH ?= $(CURDIR)/.go
export GOPATH

GOBINPATH = $(GOPATH)/bin

# Docker settings
DOCKER_IMAGE := golang:1.23.5
DOCKER_WORKDIR := /go/src/deltafi2
DOCKER_GOPATH := $(DOCKER_WORKDIR)/.go
DOCKER_CMD := docker run --rm \
              -v $(CURDIR):$(DOCKER_WORKDIR) \
              -v $(GOPATH):$(DOCKER_GOPATH) \
              -e GOPATH=$(DOCKER_GOPATH) \
              -e CGO_ENABLED=0 \
              -e GOCACHE=/tmp/.cache \
              -w $(DOCKER_WORKDIR)

GENERATED_GO := graphql/generated.go
GENERATED_FILES := $(GENERATED_GO)

# Conditionally prefix commands with docker
ifeq ($(USE_DOCKER),true)
  ifeq ($(HAS_DOCKER),)
    $(error Docker is required but not installed)
  endif
  CMD_PREFIX := $(DOCKER_CMD) $(DOCKER_IMAGE)
  GOBINPATH := $(DOCKER_GOPATH)/bin
else
  ifeq ($(GO_VERSION_CHECK), 0)
    $(warning Go $(MIN_GO_VERSION) or higher is required. Consider using USE_DOCKER=true)
  endif
  CMD_PREFIX :=
  GOBINPATH := $(GOPATH)/bin
endif

# Build tags
BUILD_TAGS ?=

# Go commands
GOCMD     = $(CMD_PREFIX) go
GOBUILD   = $(GOCMD) build -modcacherw $(if $(BUILD_TAGS),-tags $(BUILD_TAGS),)
GOCLEAN   = $(GOCMD) clean
GOTEST    = $(GOCMD) test -modcacherw $(if $(BUILD_TAGS),-tags $(BUILD_TAGS),)
GOGET     = $(GOCMD) get -modcacherw
GOVET     = $(GOCMD) vet -modcacherw
GOFMT     = $(CMD_PREFIX) gofmt
GOTIDY    = $(GOCMD) mod tidy -modcacherw
GOLINT    = $(CMD_PREFIX) $(GOBINPATH)/golangci-lint
GOINSTALL = $(GOCMD) install -modcacherw
GOGEN     = $(GOCMD) run github.com/Khan/genqlient

# Extra modules
LINT_MODULE = github.com/golangci/golangci-lint/cmd/golangci-lint@latest
GENQLIENT_MODULE = github.com/Khan/genqlient@v0.7.0

# Directories
DIST_DIR  = $(CURDIR)/dist

# Cross-compilation targets
PLATFORMS := linux/amd64 linux/arm64 darwin/amd64 darwin/arm64

.PHONY: all build clean test lint fmt deps dist tidy very-clean debug-make help check-env $(COMMANDS)

all: check-env build test

check-env:
	@if [ "$(USE_DOCKER)" = "false" ] && [ "$(GO_VERSION_CHECK)" = "0" ]; then \
		echo "WARNING: Go $(MIN_GO_VERSION) is required. Consider using USE_DOCKER=true"; \
	fi
	@mkdir -p $(GOPATH)

help:
	@echo "Version:     " $(VERSION)
	@echo "Build time:  " $(BUILD_TIME)
	@echo ""
	@echo "Build environment:"
	@echo "  Has Go:      " $(if $(HAS_GO),yes,no)
	@echo "  Has Docker:  " $(if $(HAS_DOCKER),yes,no)
	@echo "  Using Docker:" $(USE_DOCKER)
	@echo "  GOPATH:      " $(GOPATH)
	@echo ""
	@echo "Available targets:"
	@echo "  all              - Run tests and build everything"
	@echo "  build            - Build app"
	@echo "  clean            - Remove build artifacts"
	@echo "  very-clean       - Remove build artifacts and cached modules"
	@echo "  test             - Run tests"
	@echo "  lint             - Run linter"
	@echo "  fmt              - Format code"
	@echo "  deps             - Install dependencies"
	@echo "  cross            - Build all commands for all platforms"
	@echo ""
	@echo "Build options:"
	@echo "  USE_DOCKER=true|false - Use Docker for building (auto-detected: $(USE_DOCKER))"
	@echo "  BUILD_TAGS=tag1,tag2  - Add build tags"

# Build a specific command
build: check-env fmt $(GENERATED_FILES)
	@echo "Building $(BIN_DIR)/$(APP_NAME)"
	@$(GOBUILD) $(LDFLAGS) -o $(BIN_DIR)/$(APP_NAME) main.go

GENQLIENT_YAML := graphql/genqlient.yaml
GRAPHQL_SCHEMAS := $(wildcard graphql/schema/*.graphql)
GRAPHQL_QUERIES := $(wildcard graphql/*.graphql)

$(GENERATED_GO): $(GENQLIENT_YAML) $(GRAPHQL_SCHEMAS) $(GRAPHQL_QUERIES)
	@echo "Generating GraphQL client ($@)"
	@$(GOGEN) $(GENQLIENT_YAML)

dist: $(GENERATED_FILES)
	@$(foreach p,$(PLATFORMS), \
			$(eval GOOS=$(word 1,$(subst /, ,$p))) \
			$(eval GOARCH=$(word 2,$(subst /, ,$p))) \
			$(shell mkdir -p $(DIST_DIR)/$(GOOS)/$(GOARCH)) \
			GOOS=$(GOOS) GOARCH=$(GOARCH) $(GOBUILD) $(LDFLAGS) \
				-o $(DIST_DIR)/$(GOOS)/$(GOARCH)/deltafi2 ./main.go; \
	)

test: check-env build
	@echo "Running all tests"
	@$(GOTEST) -v ./...

clean:
	# $(GOCLEAN)
	rm -rf $(GENERATED_FILES) $(DIST_DIR)

very-clean: clean
	$(GOCLEAN) -modcache
	rm -rf $(GOPATH) $(BIN_DIR)/deltafi2 

lint: deps
	@echo "Pulling dependencies"
	@$(GOVET) ./...
	@$(GOLINT) run

fmt:
	@$(GOFMT) -w cmd internal

tidy: fmt
	@$(GOTIDY)

deps:
	@mkdir -p $(GOPATH)
	@$(GOGET) ./...
	@$(GOINSTALL) $(LINT_MODULE)
	@$(GOINSTALL) $(GENQLIENT_MODULE)

# Development targets
dev: deps fmt lint test build
watch:
	watchexec -w . -e go "make dev"

debug-make:
	@echo "Version:    " $(VERSION)
	@echo "Build time: " $(BUILD_TIME)
	@echo "Commands:   " $(COMMANDS)
	@echo "PATH:       " $(PATH)
	@echo "DOCKER:     " $(USE_DOCKER)
	@echo "GOPATH:     " $(GOPATH)
	@echo "CMD_PREFIX: " $(CMD_PREFIX)
	@echo "Has Go:     " $(if $(HAS_GO),yes,no)
	@echo "Has Docker: " $(if $(HAS_DOCKER),yes,no)
	@echo "Build Tags: " $(BUILD_TAGS)

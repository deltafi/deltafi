.PHONY: build test docker docker-test clean

# Go parameters
GOCMD=go
GOBUILD=$(GOCMD) build
GOCLEAN=$(GOCMD) clean
GOTEST=$(GOCMD) test
BINARY_NAME=deltafi-dirwatcher

all: test build

build:
	$(GOBUILD) -o $(BINARY_NAME) cmd/deltafi-dirwatcher/main.go

test:
	$(GOTEST) -v ./...

clean:
	$(GOCLEAN)
	rm -f $(BINARY_NAME)

docker:
	docker build -t deltafi-dirwatcher .

docker-test:
	docker build --target test .

run:
	./$(BINARY_NAME)

# Development helpers
deps:
	$(GOCMD) mod download

tidy:
	$(GOCMD) mod tidy

fmt:
	$(GOCMD) fmt ./... 

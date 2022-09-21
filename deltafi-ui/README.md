# DeltaFi UI

Web front-end for DeltaFi. Written in Vue.js.

- [Development](#development)
  - [Prerequisites](#prerequisites)
  - [Install Dependencies](#install-dependencies)
  - [Visual Studio Code](#visual-studio-code)
  - [Run Dev Server](#run-dev-server)
    - [API/GraphQL Proxy](#apigraphql-proxy)
    - [Mocking](#mocking)
  - [ESLint](#eslint)
- [Production](#production)
  - [Prerequisites](#prerequisites-1)
  - [Build Docker Image](#build-docker-image)

## Development

This section describes how to set up a deltafi-ui development environment.

#### Prerequisites

- Node.js >= 16.x
- [Visual Studio Code](https://code.visualstudio.com/)
  - [Volar](https://marketplace.visualstudio.com/items?itemName=johnsoncodehk.volar)
  - [SCSS Formatter](https://marketplace.visualstudio.com/items?itemName=sibiraj-s.vscode-scss-formatter)

#### Install Dependencies

    npm install

#### Visual Studio Code

- Start **Visual Studio Code**.
- Select **File** > **Open Workspace from File...**
- Select the file named `deltafi-ui.code-workspace` at the root of this repo.

#### Run Dev Server

To start the dev server, run:

    npm run dev

The app should then be available at http://localhost:8080/.

##### API/GraphQL Proxy

By default, the internal Dev Server is configured to proxy `/api` and `/graphql` requests to dev.deltafi.org. This can be overridden using the environment variables described in the table below.

| Path                 | Default Proxy Destination       | Override Environment Variable |
| -------------------- | ------------------------------- | ----------------------------- |
| `/api`               | https://dev.deltafi.org         | `DELTAFI_API_URL`             |
| `/graphql`           | https://gateway.dev.deltafi.org | `DELTAFI_GATEWAY_URL`         |
| `/deltafile/ingress` | https://ingress.dev.deltafi.org | `DELTAFI_INGRESS_URL`         |

For example, to run the app and point the proxy at your local DeltaFi instance, you could run:

    export DELTAFI_API_URL="http://$(deltafi serviceip deltafi-api)"
    export DELTAFI_GATEWAY_URL="http://$(deltafi serviceip deltafi-gateway)"
    export DELTAFI_INGRESS_URL="http://$(deltafi serviceip deltafi-ingress)"
    npm run dev

##### Mocking

`TODO`

#### ESLint

To run the linter, run:

    npm run lint

To run the linter and try to automatically fix issues, run:

    npm run lint:fix

## Production

This section describes how to build deltafi-ui for production.

#### Prerequisites

- Docker

#### Build Base Image

This step is only required if the `package.json` is updated.

    export VERSION=<version>
    docker buildx build --push --platform linux/amd64,linux/arm64 \
      -t deltafi/deltafi-ui-base:$VERSION -f Dockerfile.build .

And be sure to update the base image version in the main `Dockerfile`.

#### Build UI Image

    docker build -t deltafi-ui:latest .

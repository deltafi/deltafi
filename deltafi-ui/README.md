# DeltaFi UI

Web front-end for DeltaFi. Written in Vue.js.

## Development

This section describes how to set up a deltafi-ui development environment.

#### Prerequisites

- Node.js >= 16.x
- [Visual Studio Code](https://code.visualstudio.com/)
  - [Vetur](https://marketplace.visualstudio.com/items?itemName=octref.vetur)

#### Install Dependencies

    npm install

#### Visual Studio Code

- Start __Visual Studio Code__.
- Select __File__ > __Open Workspace from File...__
- Select the file named `deltafi-ui.code-workspace` at the root of this repo.

#### Run Dev Server

To start the dev server, run:

    npm run development

The app should then be available at http://localhost:8080/.

##### API/GraphQL Proxy

By default, the internal Dev Server is configured to proxy `/api` and `/graphql` requests to dev.deltafi.org. This can be overridden using the environment variables described in the table below.

| Path       | Default Proxy Destination       | Override Environment Variable |
| ---------- | ------------------------------- | ----------------------------- |
| `/api`     | https://dev.deltafi.org         | `DELTAFI_API_URL`             |
| `/graphql` | https://gateway.dev.deltafi.org | `DELTAFI_GATEWAY_URL`         |

For example, to run the app and point the proxy at your local DeltaFi instance, you could run:

    export DELTAFI_API_URL="http://$(deltafi serviceip deltafi-api)"
    export DELTAFI_GATEWAY_URL="http://$(deltafi serviceip deltafi-gateway)"
    npm run development

## Production

This section describes how to build deltafi-ui for production.

#### Prerequisites

- Docker

#### Build Docker Image

    docker build -t deltafi-ui:latest .

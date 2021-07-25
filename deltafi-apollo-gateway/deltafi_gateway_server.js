import { ApolloServerPluginLandingPageGraphQLPlayground } from 'apollo-server-core';
import { ApolloServer, gql } from "apollo-server";
import { ApolloGateway } from "@apollo/gateway";
import Request from 'requestretry';

export class DeltaFiGatewayServer {
  constructor(host = '0.0.0.0', port, serviceList, maxRetries = 5, retryDelay = 5000) {
    this.host = host;
    this.port = port;
    this.serviceList = serviceList;
    this.maxRetries = maxRetries;
    this.retryDelay = retryDelay;
  }
  
  async start() {
    const timeout = (this.maxRetries * this.retryDelay) / 1000;
    
    console.log("Service List: ");
    console.log(this.serviceList);

    console.log("Waiting for services to be up...");
    await Promise.all(
      this.serviceList.map((service) => {
        return Request({
          url: service.url,
          maxAttempts: this.maxRetries,
          retryDelay: this.retryDelay,
          retryStrategy: Request.RetryStrategies.HTTPOrNetworkError
        });
      })
    ).catch((error) => {
      console.error(error);
      console.error(
        `One or more services failed to respond in time (${timeout} seconds). Exiting.`
      );
      process.exit(1);
    });

    const gateway = new ApolloGateway({
      serviceList: this.serviceList,
    });

    const server = new ApolloServer({
      gateway,
      subscriptions: false,
      plugins: [
        ApolloServerPluginLandingPageGraphQLPlayground({
          settings: {
            "request.credentials": "same-origin",
          },
        })
      ]
    });

    console.log("Starting server...");
    server.listen({ host: this.host, port: this.port }).then(({ url }) => {
      console.log(`Server running on ${url}`);
    });

    return server;
  };
}

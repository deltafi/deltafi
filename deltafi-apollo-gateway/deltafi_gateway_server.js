import { ApolloServer } from "apollo-server-express";
import { ApolloGateway } from "@apollo/gateway";
import Request from 'requestretry';
import express from 'express';
import expressPlayground from '@apollographql/graphql-playground-middleware-express';
import path from 'path';

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
      subscriptions: false
    });

    const app = express()
    await server.start()
    server.applyMiddleware({ app })

    const staticRoot = path.resolve('node_modules');
    app.use('/node_modules', express.static(staticRoot));

    app.get('/', expressPlayground.default({
      endpoint: '/graphql',
      cdnUrl: './node_modules/@apollographql',
      settings: {
        "request.credentials": "same-origin",
      }
    }))

    console.log("Starting server...");
    app.listen(this.port, this.host)
    console.log(`Server running on http://${this.host}:${this.port}`);

    return server;
  };
}

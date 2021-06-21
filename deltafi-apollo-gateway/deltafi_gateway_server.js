const { ApolloServer, gql } = require("apollo-server");
const { ApolloGateway } = require("@apollo/gateway");
const request = require("requestretry");

const deltaFiGatewayServer = async function (port, serviceList, maxRetries = 5, retryDelay = 5000) {
  const timeout = (maxRetries * retryDelay) / 1000;

  console.log("Service List: ");
  console.log(serviceList);

  console.log("Waiting for services to be up...");
  await Promise.all(
    serviceList.map((service) => {
      return request({
        url: service.url,
        maxAttempts: maxRetries,
        retryDelay: retryDelay,
        retryStrategy: request.RetryStrategies.HTTPOrNetworkError,
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
    serviceList: serviceList,
  });

  const server = new ApolloServer({
    gateway,
    subscriptions: false,
    playground: {
      settings: {
        "request.credentials": "same-origin",
      },
    },
  });

  console.log("Starting server...");
  server.listen({ port: port }).then(({ url }) => {
    console.log(`Server running on ${url}`);
  });

  return server;
};

exports.DeltaFiGatewayServer = deltaFiGatewayServer;

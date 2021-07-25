import { DeltaFiGatewayServer } from './deltafi_gateway_server.js';

const { HOST = '0.0.0.0' } = process.env;
const { PORT = 4000 } = process.env;
const { RETRY_DELAY = 5000 } = process.env;
const { MAX_RETRIES = 5 } = process.env;
const defaultServiceList = [
  { name: "deltafi", url: "http://localhost:8080/graphql" },
  { name: "stix", url: "http://localhost:8081/graphql" },
];
const SERVICE_LIST =
  process.env.SERVICE_LIST === "null" || process.env.SERVICE_LIST === undefined
    ? defaultServiceList
    : JSON.parse(process.env.SERVICE_LIST);

var deltaFiGatewayServer = new DeltaFiGatewayServer(HOST, PORT, SERVICE_LIST, MAX_RETRIES, RETRY_DELAY);
deltaFiGatewayServer.start();

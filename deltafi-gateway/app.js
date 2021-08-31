import { DeltaFiGatewayServer } from './deltafi_gateway_server.js';

const { HOST = '0.0.0.0' } = process.env;
const { PORT = 4000 } = process.env;
const { RETRY_DELAY = 5000 } = process.env;
const { MAX_RETRIES = 5 } = process.env;

if (process.env.SERVICE_LIST === "null" || process.env.SERVICE_LIST === undefined) {
  console.log('Running in Kubernetes Service auto-discovery mode.')
  const deltaFiGatewayServer = new DeltaFiGatewayServer(HOST, PORT, [], MAX_RETRIES, RETRY_DELAY);
  await deltaFiGatewayServer.loadServiceListFromKubernetes();
  await deltaFiGatewayServer.start(true);
  deltaFiGatewayServer.watchKubernetesForServiceListChanges();
} else {
  console.log('Running in environment variable mode.');
  console.log(`SERVICE_LIST=${process.env.SERVICE_LIST}`);
  const SERVICE_LIST = JSON.parse(process.env.SERVICE_LIST);
  const deltaFiGatewayServer = new DeltaFiGatewayServer(HOST, PORT, SERVICE_LIST, MAX_RETRIES, RETRY_DELAY);
  deltaFiGatewayServer.start(true);
}
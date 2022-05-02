/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
import { DeltaFiGatewayServer } from './deltafi_gateway_server.js';

const { HOST = '0.0.0.0' } = process.env;
const { PORT = 4000 } = process.env;
const { RETRY_DELAY = 5000 } = process.env;
const { MAX_RETRIES = 5 } = process.env;

var deltaFiGatewayServer;

if (process.env.SERVICE_LIST === "null" || process.env.SERVICE_LIST === undefined) {
  console.log('Running in Kubernetes Service auto-discovery mode.')
  deltaFiGatewayServer = new DeltaFiGatewayServer(HOST, PORT, [], MAX_RETRIES, RETRY_DELAY);
  await deltaFiGatewayServer.loadServiceListFromKubernetes();
  await deltaFiGatewayServer.start(true);
  deltaFiGatewayServer.watchKubernetesForServiceListChanges();
} else {
  console.log('Running in environment variable mode.');
  console.log(`SERVICE_LIST=${process.env.SERVICE_LIST}`);
  const SERVICE_LIST = JSON.parse(process.env.SERVICE_LIST);
  deltaFiGatewayServer = new DeltaFiGatewayServer(HOST, PORT, SERVICE_LIST, MAX_RETRIES, RETRY_DELAY);
  deltaFiGatewayServer.start(true);
}

process.once('SIGTERM', async (code) => {
  console.log('SIGTERM received...');
  await deltaFiGatewayServer.stop();
  process.exit();
});

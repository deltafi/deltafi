import { ApolloServer } from "apollo-server-express";
import { ApolloGateway } from "@apollo/gateway";
import Request from 'requestretry';
import express from 'express';
import expressPlayground from '@apollographql/graphql-playground-middleware-express';
import path from 'path';
import * as k8s from '@kubernetes/client-node';

const DOMAIN_SERVICE_LABEL_KEY = 'deltafi-domain-service';

export class DeltaFiGatewayServer {
  constructor(host = '0.0.0.0', port, serviceList, maxRetries = 5, retryDelay = 5000) {
    this.host = host;
    this.port = port;
    this.serviceList = serviceList;
    this.maxRetries = maxRetries;
    this.retryDelay = retryDelay;
  }

  hasService(service) {
    let name = service.metadata.labels[DOMAIN_SERVICE_LABEL_KEY];
    let knownServiceNames = this.serviceList.map(s => s.name);
    return knownServiceNames.includes(name);
  }

  async restart(reloadServiceListFromKubernetes=false) {
    if (reloadServiceListFromKubernetes) {
      await this.loadServiceListFromKubernetes();
    }
    await this.testServiceList();
    await this.stop();
    await this.start();
  }

  async stop() {
    console.log('Stopping Gateway...');
    clearInterval(this.timer);
    this.apolloServer.stop();
    this.expressServer.close();
  }
  
  async start(checkServices=false) {
    console.log("Starting Gateway...");
    if (this.serviceList.length == 0) { throw new Error('Must have at least one service.'); }

    if (checkServices) { await this.testServiceList(); }

    let gateway = new ApolloGateway({
      serviceList: this.serviceList,
    });

    this.apolloServer = new ApolloServer({
      gateway,
      subscriptions: false
    });

    let app = express();
    await this.apolloServer.start();
    this.apolloServer.applyMiddleware({ app });

    let staticRoot = path.resolve('node_modules');
    app.use('/node_modules', express.static(staticRoot));

    app.get('/', expressPlayground.default({
      endpoint: '/graphql',
      cdnUrl: './node_modules/@apollographql',
      settings: {
        "request.credentials": "same-origin",
      }
    }))

    this.expressServer = app.listen(this.port, this.host);
    console.log(`Gateway server running on http://${this.host}:${this.port}`);
  };

  async testServiceList() {
    console.log("Testing connection to backend services: ");
    console.log(this.serviceList);

    let timeout = (this.maxRetries * this.retryDelay) / 1000;
    await Promise.all(
      this.serviceList.map((service) => {
        return Request({
          url: service.url,
          maxAttempts: this.maxRetries,
          retryDelay: this.retryDelay,
          retryStrategy: Request.RetryStrategies.HTTPOrNetworkError
        });
      })
    ).then(() => {
      console.log("All backend services are up.");
    }).catch((error) => {
      console.error(error);
      console.error(
        `One or more services failed to respond in time (${timeout} seconds).`
      );
    });
  }

  async loadServiceListFromKubernetes() {
    console.log("Loading domain services from Kubernetes...");
    const kc = new k8s.KubeConfig();
    kc.loadFromDefault();
    const k8sApi = kc.makeApiClient(k8s.CoreV1Api);

    await k8sApi.listNamespacedService('deltafi', null, null, null, null, DOMAIN_SERVICE_LABEL_KEY)
    .then((res) => {
      this.serviceList = res.body.items.map((service) => {
        return {
          name: service.metadata.labels[DOMAIN_SERVICE_LABEL_KEY],
          url: `http://${service.metadata.name}/graphql`
        }
      })
    })
  }

  async watchKubernetesForServiceListChanges() {
    const kc = new k8s.KubeConfig();
    kc.loadFromDefault();

    const k8sApi = kc.makeApiClient(k8s.CoreV1Api);

    const listFn = () => k8sApi.listNamespacedService(
      'deltafi',
      undefined,
      undefined,
      undefined,
      undefined,
      DOMAIN_SERVICE_LABEL_KEY,
    );

    const informer = k8s.makeInformer(
      kc,
      '/api/v1/namespaces/deltafi/services',
      listFn,
      DOMAIN_SERVICE_LABEL_KEY,
    );

    informer.on('add', (obj) => {
      if (!this.hasService(obj)) {
        console.log(`Domain service '${obj.metadata.name}' added.`);
        this.restart(true);
      }
    });
    informer.on('update', (obj) => {
      console.log(`Domain service '${obj.metadata.name}' modified.`)
      this.restart(true);
    });
    informer.on('delete', (obj) => {
      if (this.hasService(obj)) {
        console.log(`Domain service '${obj.metadata.name}' removed.`);
        this.restart(true);
      }
    });
    informer.on('error', (err) => {
      console.error(err);
      // Restart informer after 5sec
      setTimeout(() => {
          informer.start();
      }, 5000);
    });

    informer.start();
  }
}

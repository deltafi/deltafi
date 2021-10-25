<template>
  <div>
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">Dashboard</h1>
    </div>
    
    <h5>Helpful Links</h5>
    <ul>
      <li v-for="link in helpful_links" :key="link.subdomain">
        <a v-bind:href="subdomainUrl(link.subdomain)">{{ link.name }}</a> - {{ link.description }}
      </li>
    </ul>
  </div>
</template>

<script>
import ApiService from "../service/ApiService";

export default {
  name: "Dashboard",
  apiService: null,
  data() {
    return {
      domain: null,
      helpful_links: [
        {
          name: "GraphQL Gateway",
          subdomain: "gateway",
          description: "GraphQL query interface",
        },
        {
          name: "Kibana",
          subdomain: "kibana",
          description: "Action logging and metrics visualization",
        },
        {
          name: "Kubernetes Dashboard",
          subdomain: "k8s",
          description: "Kubernetes admin interface",
        },
        {
          name: "MinIO",
          subdomain: "minio",
          description: "MinIO storage dashboard",
        },
        {
          name: "NiFi",
          subdomain: "nifi",
          description: "Internal NiFi dashboard",
        },
        {
          name: "Zipkin",
          subdomain: "zipkin",
          description: "DeltaFile tracing dashboard",
        },
      ],
    };
  },
  methods: {
    async fetchConfig() {
      let response = await this.apiService.getConfig();
      this.domain = response.config.system.domain;
    },
    subdomainUrl(subdomain) {
      return `https://${subdomain}.${this.domain}`;
    },
  },
  created() {
    this.apiService = new ApiService();
    this.fetchConfig();
  },
};
</script>
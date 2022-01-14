export default class ApiService {
  basePath: String;

  constructor(version: String = "v1") {
    this.basePath = `/api/${version}`;
  }

  buildURL(path: String, params: URLSearchParams = new URLSearchParams()) {
    let url = `${this.basePath}${path}`;
    if (Array.from(params.keys()).length > 0) {
      url += `?${params.toString()}`;
    }
    return url;
  }

  get(path: String, params: URLSearchParams = new URLSearchParams()) {
    const url = this.buildURL(path, params);
    const req = new Request(url, { referrer: "" });
    return fetch(req);
  }

  post(path: String, options: any = {}) {
    const url = this.buildURL(path)
    options.method = 'POST';
    options.referrer = '';
    const req = new Request(url, options);
    return fetch(req);
  }

  getNodes() {
    return this.get("/metrics/system/nodes")
      .then(res => res.json());
  }

  getConfig() {
    return this.get("/config")
      .then(res => res.json());
  }

  getStatus() {
    return this.get("/status")
      .then(res => res.json())
      .catch(error => {
        return {
          status: {
            code: 2,
            state: "Error",
            checks: [
              {
                description: "Unable to communicate with API",
                code: 2,
                message: error.message
              }
            ]
          }
        }
      });
  }

  getVersions() {
    return this.get("/versions")
      .then(res => res.json());
  }

  getContent(contentReference: any) {
    const params = new URLSearchParams(contentReference);
    return this.get('/content', params);
  }

  contentUrl(contentReference: any) {
    const params = new URLSearchParams(contentReference);
    return this.buildURL('/content', params);
  }
}

export default class ApiService {
    basePath:String;

    constructor(version: String = 'v1') {
        this.basePath = `/api/${version}`;
    }

    get(path: String, params: URLSearchParams = new URLSearchParams()) {
        const url = `${this.basePath}${path}?${params.toString()}`;
        const req = new Request(url, { referrer: "" });

        return fetch(req).then((res) => { return res.json() });
    }

    getNodes() {
        return this.get('/metrics/system/nodes');
    }
}
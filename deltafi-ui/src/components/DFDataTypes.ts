class DFMetadata {
  constructor(public selfLink: String) {
    this.selfLink = selfLink;
  }
}

class DFPodMetadata extends DFMetadata {
  constructor(
    public name: String,
    public namespace: String,
    public selfLink: String,
    public creationTimestamp: String
  ) {
    super(selfLink);
    this.name = name;
    this.namespace = namespace;
    this.creationTimestamp = creationTimestamp;
  }
}

class DFNodeMetadata extends DFMetadata {
  constructor(
    public name: String,
    public selfLink: String,
    public creationTimestamp: String
  ) {
    super(selfLink);
    this.name = name;
    this.creationTimestamp = creationTimestamp;
  }
}

// value from JSON expressed in billionths, i.e. ######n
// TODO convert to Mi
class DFMetric {
  constructor(public cpu: String, public memory: String) {
    this.cpu = cpu;
    this.memory = memory;
  }
}

class DFContainer {
  constructor(public name: String, public metric: DFMetric) {
    this.name = name;
    this.metric = metric;
  }
}

class DFData {
  constructor(
    public timestamp: String,
    public window: String,
    public metric: DFMetric
  ) {
    this.timestamp = timestamp;
    this.window = window;
    this.metric = metric; //this will be sum of metrics for all pods
  }
}

export class DFNode extends DFData {
  constructor(
    public metadata: DFNodeMetadata,
    public timestamp: String,
    public window: String,
    public metric: DFMetric
  ) {
    super(timestamp, window, metric);
    this.metadata = metadata;
  }
}

export class DFPod {
  public metric: DFMetric;

  constructor(
    public metadata: DFPodMetadata,
    public timestamp: String,
    public window: String,
    public containers: DFContainer[]
  ) {
    this.metric = new DFMetric("0.0", "0.0"); //uninitialize metric
    this.metadata = metadata;
    this.timestamp = timestamp;
    this.window = window;
    this.containers = new Array<DFContainer>();
  }

  getMetrics() {
      // TODO get metrics summed from all containers in this.containers in this pod
    return this.metric;
  }
}

// List of all kube item types - currently nodes and pods
export class DFDataList {
  constructor(
    public kind: String,
    public apiVersion: String,
    public metadata: DFMetadata,
    public items: DFData[]
  ) {
    this.kind = kind;
    this.apiVersion = apiVersion;
    this.metadata = metadata;
    items = new Array<DFData>(); //initialized, ready to add
  }
}

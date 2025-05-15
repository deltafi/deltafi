import { w } from "@faker-js/faker/dist/airline-D6ksJFwG";
import { CytoscapeOptions } from "cytoscape";

const style: any = [
  {
    selector: "node",
    style: {
      width: 80,
      height: 40,
      "font-size": 9,
      "font-weight": "bold",
      "min-zoomed-font-size": 4,
      label: "data(label)",
      // label: function (node: any) {
      //   if (node.data("label").length > 20) {
      //     return `${node.data("label").substring(0, 20)}...`;
      //   } else {
      //     return `${node.data("label")}`;
      //   }
      // },
      "text-valign": "center",
      "text-halign": "center",
      "text-events": "yes",
      color: "#000",
      "text-outline-width": 1,
      "text-outline-color": "#fff",
      "text-outline-opacity": 1,
      "overlay-color": "#fff",
      padding: "4",
      "border-width": 1.5,
      "border-color": "#555",
      "text-wrap": "wrap",
      "text-max-width": "70",
      "text-overflow-wrap": "anywhere",
    },
  },
  {
    selector: 'node[type="REST_DATA_SOURCE"]',
    style: {
      shape: "round-rectangle",
      "background-color": "#c7def0",
      "text-outline-color": "#c7def0",
    },
  },
  {
    selector: 'node[type="REST_DATA_SOURCE"][state="STOPPED"]',
    style: {
      shape: "round-octagon",
      "background-color": "LightCoral",
      "text-outline-color": "LightCoral",
    },
  },
  {
    selector: 'node[type="TIMED_DATA_SOURCE"]',
    style: {
      shape: "round-rectangle",
      "background-color": "#c7def0",
      "text-outline-color": "#c7def0",
    },
  },
  {
    selector: 'node[type="TIMED_DATA_SOURCE"][state="STOPPED"]',
    style: {
      shape: "round-octagon",
      "background-color": "LightCoral",
      "text-outline-color": "LightCoral",
    },
  },
  {
    selector: 'node[type="TOPIC"]',
    style: {
      shape: "round-rectangle",
      "background-color": "#EFEFEF",
      "text-outline-color": "#EFEFEF",
    },
  },
  {
    selector: 'node[type="TRANSFORM"]',
    style: {
      shape: "round-rectangle",
      "background-color": "#D8D3E7",
      "text-outline-color": "#D8D3E7",
    },
  },
  {
    selector: 'node[type="TRANSFORM"][state="STOPPED"]',
    style: {
      shape: "round-octagon",
      "background-color": "LightCoral",
      "text-outline-color": "LightCoral",
    },
  },
  {
    selector: 'node[type="DATA_SINK"]',
    style: {
      shape: "round-rectangle",
      "background-color": "#d4e5ce",
      "text-outline-color": "#d4e5ce",
    },
  },
  {
    selector: 'node[type="DATA_SINK"][state="STOPPED"]',
    style: {
      shape: "round-octagon",
      "background-color": "LightCoral",
      "text-outline-color": "LightCoral",
    },
  },
  {
    selector: "edge",
    style: {
      "curve-style": "bezier",
      "target-arrow-shape": "triangle",
      "line-color": "black",
      "target-arrow-color": "black",
      width: 2,
      "z-index": 0,
      "overlay-opacity": 0,
      events: "no",
      "source-endpoint": "outside-to-node",
      "target-endpoint": "outside-to-node",
      "line-style": "solid",
      // "line-style": "dashed",
      // "line-dash-pattern": [6, 3],
      // "line-dash-offset": 24,
    },
  },
  {
    selector: "edge.not-path",
    style: {
      opacity: 0.1,
      "z-index": 0,
    },
  },
  {
    selector: "node.not-path",
    style: {
      opacity: 0.1,
      "z-index": 0,
    },
  },
];

export { style };

import { NodeSingular, EdgeSingular } from "cytoscape";

import _ from "lodash";

class Controller {
  private cy: any;

  constructor(cy: any) {
    this.cy = cy;
  }

  showPathFromDataSource(node: NodeSingular): void {
    setTimeout(() => {
      const { cy } = this;
      const allEles = cy.elements();
      const viewNodeList: Set<string> = new Set();

      const highlightPath = (node: NodeSingular, visited = new Set<string>()) => {
        visited.add(node.id());
        viewNodeList.add(node.id());

        node.removeClass("not-path");

        node.connectedEdges().forEach((edge: EdgeSingular) => {
          const target = edge.target();
          const source = edge.source();

          if (source.id() === node.id()) {
            edge.removeClass("not-path");
            highlightPath(target, visited);
          }
        });
      };
      const startNode = cy.getElementById(node.id);

      allEles.addClass("not-path");
      highlightPath(startNode);

      const viewNodeListIds = Array.from(viewNodeList).map((id) => `#${id}`);
      cy.fit(cy.$(viewNodeListIds.join(",")), 30);

      cy.endBatch();
    }, 300);
  }

  showPathToDataSink(node: NodeSingular) {
    const { cy } = this;
    cy.startBatch();

    setTimeout(function () {
      const allEles = cy.elements();
      const viewNodeList = new Set();

      // Recursive function to highlight nodes and edges in a path
      function highlightPath(node: NodeSingular, visitedNodes = new Set()) {
        visitedNodes.add(node.id());
        viewNodeList.add(node.id());

        // Highlight the current node
        node.removeClass("not-path");

        // Traverse the outgoing edges and highlight them
        node.connectedEdges().forEach((edge: EdgeSingular) => {
          // Recursively highlight the next node in the path
          if (edge.target().id() == node.id()) {
            edge.removeClass("not-path");
            highlightPath(edge.source(), visitedNodes);
          }
          // Recursively move to the next node
        });
      }

      // Select the starting node (change this to your desired node)
      const startNode = cy.getElementById(`${node.id}`); // Example starting node (Node 'a')

      // Call the function to start highlighting recursively
      allEles.addClass("not-path");
      highlightPath(startNode);

      let viewNodeListIds = [...viewNodeList];
      viewNodeListIds = viewNodeListIds.map((i) => "#" + i);
      cy.fit(cy.$(viewNodeListIds.join(",")), 30);

      cy.endBatch();
    }, 300);
  }

  showAllPaths(node: NodeSingular) {
    const { cy } = this;
    cy.startBatch();

    setTimeout(function () {
      const allEles = cy.elements();
      const viewNodeList = new Set();

      function highlightPathForward(node: any, visitedNodes = new Set()) {
        visitedNodes.add(node.id());

        viewNodeList.add(node.id());

        // Highlight the current node
        node.removeClass("not-path");

        // Traverse the outgoing edges and highlight them
        node.connectedEdges().forEach((edge: EdgeSingular) => {
          // Recursively highlight the next node in the path
          if (edge.source().id() == node.id()) {
            edge.removeClass("not-path");
            highlightPathForward(edge.target(), visitedNodes);
          }
          // Recursively move to the next node
        });
      }

      function highlightPathBehind(node: NodeSingular, visitedNodes = new Set()) {
        visitedNodes.add(node.id());
        viewNodeList.add(node.id());

        // Highlight the current node
        node.removeClass("not-path");

        // Traverse the outgoing edges and highlight them
        node.connectedEdges().forEach((edge) => {
          // Recursively highlight the next node in the path
          const nextNode = edge.source().id() === node.id() ? edge.target() : edge.source();
          if (edge.target().id() == node.id()) {
            edge.removeClass("not-path");
            highlightPathBehind(edge.source(), visitedNodes);
          }
          // Recursively move to the next node
        });
      }

      // Select the starting node (change this to your desired node)
      const startNode = cy.getElementById(`${node.id}`); // Example starting node (Node 'a')

      // Call the function to start highlighting recursively
      allEles.addClass("not-path");
      highlightPathForward(startNode);
      highlightPathBehind(startNode);

      let viewNodeListIds = [...viewNodeList];
      viewNodeListIds = viewNodeListIds.map((i) => "#" + i);
      cy.fit(cy.$(viewNodeListIds.join(",")), 30);

      cy.endBatch();
    }, 300);
  }

  clearPaths(): void {
    this.cy.elements().removeClass("path not-path highlighted highlighted-edge");
  }
}

export default Controller;
export { Controller };

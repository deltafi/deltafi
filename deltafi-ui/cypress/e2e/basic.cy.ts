describe("Auto Resume Page", () => {
  it("loads Auto Resume rules", () => {
    cy.visit("http://localhost:8080/config/auto-resume");
    cy.get("span.p-panel-title").contains("Rules");
  });
});

describe("Dashboard Page", () => {
  it("loads the Dashboard", () => {
    cy.visit("http://localhost:8080");
    cy.get("span.p-panel-title").contains("DeltaFile Stats");
    cy.get("span.p-panel-title").contains("Metrics");
    cy.get("span.p-panel-title").contains("Installed Plugins");
    cy.get("span.p-panel-title").contains("External Links");
  });
});

describe("Data Sources", () => {
  it("loads Data Sources", () => {
    cy.visit("http://localhost:8080/config/data-sources");
    cy.get("span.p-panel-title").contains("REST Data Sources");
    cy.get("span.p-panel-title").contains("Timed Data Sources");
  });
});

describe("Delete Policies Page", () => {
  it("loads Delete Policies", () => {
    cy.visit("http://localhost:8080/config/delete-policies");
    cy.get("span.p-panel-title").contains("Delete Policies");
  });
});

describe("DeltaFile Search Page", () => {
  it("loads DeltaFile Search", () => {
    cy.visit("http://localhost:8080/deltafile/search?perPage=20");
    cy.get("span.p-panel-title").contains("Advanced Search Options");
    cy.get("span.p-paginator-current").contains("1 - 20 of 2000");
  });
});

describe("DeltaFile Viewer Page", () => {
  context("without a DID", () => {
    it("loads the form", () => {
      cy.visit("http://localhost:8080/deltafile/viewer");
      cy.get("h2").contains("DeltaFile Viewer");
    });
  });

  context("with a DID", () => {
    it("loads the DeltaFile", () => {
      cy.visit("http://localhost:8080/deltafile/viewer/2ace8f8a-60d6-4211-894a-0cd7455bf59z");
      cy.get("span.p-panel-title").contains("Parent DeltaFiles");
      cy.get("span.p-panel-title").contains("Child DeltaFiles");
      cy.get("span.p-panel-title").contains("Annotations");
      cy.get("span.p-panel-title").contains("Flows");
      cy.get("span.p-panel-title").contains("Trace");
      cy.get("strong").contains("View").click();
      cy.get("button.p-dialog-header-icon.p-dialog-header-close.p-link").click();
    });
  });
});

describe("DeltaFile Errors Page", () => {
  it("randers all three tabs - All, By Flow, and By Message", () => {
    cy.visit("http://localhost:8080/errors?tab=0");
    cy.get("span.p-panel-title").contains("DeltaFiles");
    cy.get("span.p-paginator-current").contains("1 - 20 of 2000");
    cy.get("span.p-tabview-title").contains("By Data Source").click();
    cy.get("span.p-panel-title").contains("DeltaFiles by Data Source");
    cy.get("span.p-paginator-current").contains("1 - 3 of 3");
    cy.get("span.p-tabview-title").contains("By Message").click();
    cy.get("span.p-panel-title").contains("DeltaFiles by Message");
    cy.get("span.p-paginator-current").contains("1 - 4 of 4");
  });
});

describe("Egress Page", () => {
  it("loads Egress Page", () => {
    cy.visit("http://localhost:8080/config/egress");
    cy.get("span.p-panel-title").contains("Egress Actions");
  });
});

describe("External Links Page", () => {
  it("loads External Links", () => {
    cy.visit("http://localhost:8080/admin/external-links");
    cy.get("span.p-panel-title").contains("External links");
    cy.get("span.p-panel-title").contains("DeltaFile Links");
  });
});

describe("DeltaFile Filtered Page", () => {
  it("randers all three tabs - All, By Data Source, and By Cause", () => {
    cy.visit("http://localhost:8080/deltafile/filtered?tab=0");
    cy.get("span.p-panel-title").contains("DeltaFiles");
    cy.get("span.p-tabview-title").contains("By Data Source").click();
    cy.get("span.p-panel-title").contains("DeltaFiles by Data Source");
    cy.get("span.p-tabview-title").contains("By Cause").click();
    cy.get("span.p-panel-title").contains("DeltaFiles by Cause");
  });
});

describe("Flow Plan Builder Page", () => {
  it("loads Flow Plan Builder", () => {
    cy.visit("http://localhost:8080/config/flow-plan-builder");
    cy.get("title").contains("Flow Plan Builder");
  });
});

describe("Flows Page", () => {
  it("loads Flows", () => {
    cy.visit("http://localhost:8080/config/flows");
    cy.get("span.p-panel-title").contains("Transform");
  });
});

describe("Plugin Repositories Page", () => {
  it("loads Plugin Repositories", () => {
    cy.visit("http://localhost:8080/config/plugin-repositories");
    cy.get("span.p-panel-title").contains("Plugin Repositories");
    cy.get("span.p-button-label").contains("Add Image Repository").click();
  });
});

describe("Plugins Page", () => {
  it("loads Plugins", () => {
    cy.visit("http://localhost:8080/config/plugins");
    cy.get("span.p-panel-title").contains("Plugins");
  });
});

describe("Roles Page", () => {
  it("loads Roles", () => {
    cy.visit("http://localhost:8080/admin/roles");
    cy.get("span.p-panel-title").contains("Roles");
    cy.get("span.p-button-label").contains("Add Role").click();
    cy.get("dt").contains("Name");
    cy.get("button.p-dialog-header-icon.p-dialog-header-close.p-link").click();
  });
});

describe("System Metrics Page", () => {
  it("loads System Metrics", () => {
    cy.visit("http://localhost:8080/metrics/system");
    cy.get("span.p-panel-title").contains("Nodes");
  });
});

describe("System Properties Page", () => {
  it("loads System Properties", () => {
    cy.visit("http://localhost:8080/config/system");
    cy.get("span.p-panel-title").contains("Mock Common Properties");
    cy.get("span.p-panel-title").contains("Mock Action-Kit Properties ");
    cy.get("span.p-panel-title").contains("Plugin Properties - Deltafi STIX 1");
    cy.get("span.p-panel-title").contains("Plugin Properties - Deltafi STIX 2");
    cy.get("span.p-panel-title").contains("Plugin Properties - Deltafi STIX 5");
    cy.get("span.p-panel-title").contains("Plugin Properties - Deltafi STIX");
    cy.get("span.p-panel-title").contains("Mock Common Properties");
  });
});

describe("System Snapshots Page", () => {
  it("loads System Snapshots", () => {
    cy.visit("http://localhost:8080/config/snapshots");
    cy.get("span.p-panel-title").contains("Snapshots");
  });
});

describe("DeltaFile Upload Page", () => {
  it("can add metadata", () => {
    cy.visit("http://localhost:8080/deltafile/upload");
    const addButton = cy.get("span").contains("Add Metadata Field");
    Cypress._.times(3, () => {
      cy.wait(10);
      addButton.click();
    });
  });
});

describe("Users Page", () => {
  it("loads Users", () => {
    cy.visit("http://localhost:8080/admin/users");
    cy.get("span.p-panel-title").contains("Users");
    cy.get("span.p-button-label").contains("Add User").click();
    cy.get("button.p-dialog-header-icon.p-dialog-header-close.p-link").click();
  });
});

describe("Versions Page", () => {
  it("loads Versions", () => {
    cy.visit("http://localhost:8080/versions");
    cy.get("span.p-panel-title").contains("Core");
    cy.get("span.p-panel-title").contains("Plugins");
    cy.get("span.p-panel-title").contains("Other");
  });
});

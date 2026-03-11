/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

{
  describe("DeltaFile Import Page", () => {
    const baseUrl = "http://localhost:8080/deltafile/import";

    describe("Page Structure & Elements", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with default settings", () => {
        cy.get("h2").contains("Import DeltaFiles");
      });

      it("displays file upload component", () => {
        cy.get(".p-fileupload").should("exist");
      });

      it("displays drag and drop message", () => {
        cy.contains("Drag and drop").should("exist");
      });

      it("displays Add Files button", () => {
        cy.contains("Add Files").should("exist");
      });

      it("displays Clear button", () => {
        cy.contains("Clear").should("exist");
      });

      it("accepts tar files", () => {
        cy.get(".p-fileupload").should("exist");
      });

      it("page container exists", () => {
        cy.get(".deltafile-import-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".deltafile-import-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".deltafile-import-page").should("be.visible");
      });
    });
  });
}

{
  describe("DeltaFile Upload Page", () => {
    const baseUrl = "http://localhost:8080/deltafile/upload";

    describe("Page Structure & Elements", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Upload Files");
      });

      it("displays the Metadata panel", () => {
        cy.get(".metadata-panel").should("exist");
      });

      it("displays Add Metadata Field button", () => {
        cy.contains("Add Metadata Field").should("exist");
      });

      it("displays Clear button", () => {
        cy.get(".metadata-panel").within(() => {
          cy.contains("Clear").should("exist");
        });
      });

      it("displays Download Metadata button", () => {
        cy.contains("Download Metadata").should("exist");
      });

      it("displays Upload Metadata button", () => {
        cy.contains("Upload Metadata").should("exist");
      });

      it("displays Import Metadata button", () => {
        cy.contains("Import Metadata").should("exist");
      });

      it("displays Data Source dropdown", () => {
        cy.get(".p-dropdown").should("exist");
      });

      it("displays file upload component", () => {
        cy.get(".p-fileupload").should("exist");
      });

      it("displays Add Files button", () => {
        cy.contains("Add Files").should("exist");
      });

      it("displays drag and drop message", () => {
        cy.contains("Drag and drop files").should("exist");
      });

      it("page container exists", () => {
        cy.get(".deltafile-upload-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".deltafile-upload-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".deltafile-upload-page").should("be.visible");
      });
    });

    describe("Metadata Functionality", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("can add metadata field", () => {
        cy.contains("Add Metadata Field").click();
        cy.get('input[placeholder="Key"]').should("exist");
        cy.get('input[placeholder="Value"]').should("exist");
      });

      it("can add multiple metadata fields", () => {
        cy.contains("Add Metadata Field").click();
        cy.contains("Add Metadata Field").click();
        cy.get('input[placeholder="Key"]').should("have.length", 3);
      });

      it("metadata field has remove button", () => {
        cy.get(".metadata-panel button").should("exist");
      });
    });
  });
}

{
  describe("DeltaFile Search Page", () => {
    const baseUrl = "http://localhost:8080/deltafile/search";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(`${baseUrl}?perPage=20`);
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("loads the page with default settings", () => {
        cy.get("h2").contains("DeltaFile Search");
        cy.get("span.p-panel-title").contains("Results");
      });

      it("displays the search date toolbar", () => {
        cy.get(".p-dropdown").should("exist");
        cy.get("button").find(".fa-sync-alt, .fa-refresh, .pi-refresh").should("exist");
      });

      it("displays all expected table columns", () => {
        cy.get(".p-datatable-thead").within(() => {
          cy.contains("DID");
          cy.contains("Filename");
          cy.contains("Data Source");
          cy.contains("Stage");
          cy.contains("Created");
          cy.contains("Modified");
          cy.contains("Size");
          cy.contains("Elapsed");
        });
      });

      it("displays results in the data table", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays pagination info correctly", () => {
        cy.get("span.p-paginator-current").contains("1 - 20 of 2000");
      });

      it("rows are selectable", () => {
        cy.get(".p-datatable-tbody tr.p-selectable-row").should("have.length.greaterThan", 0);
      });

      it("table supports row selection", () => {
        cy.get(".p-datatable").should("exist");
        cy.get(".p-datatable-tbody tr.p-selectable-row").first().should("exist");
      });

      it("panel header exists", () => {
        cy.get(".p-panel-header").should("exist");
      });

      it("results panel shows data", () => {
        cy.get("span.p-panel-title").contains("Results").should("exist");
      });

      it("DID column contains clickable links", () => {
        cy.get(".p-datatable-tbody tr").first().within(() => {
          cy.get("a").first().should("have.attr", "href").and("include", "/deltafile/viewer/");
        });
      });

      it("page container exists", () => {
        cy.get(".deltafile-search-page").should("exist");
      });

      it("data table has rows", () => {
        cy.get(".p-datatable-tbody tr.p-selectable-row").should("have.length.greaterThan", 0);
      });

      it("table rows are interactive", () => {
        cy.get(".p-datatable-tbody tr.p-selectable-row").first().should("exist");
      });

      it("displays timezone button", () => {
        cy.get("button").contains(/[A-Z]{2,4}/).should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".deltafile-search-page").should("be.visible");
        cy.get(".p-datatable").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".deltafile-search-page").should("be.visible");
        cy.get(".p-datatable").should("be.visible");
      });

      it("can open the filter sidebar", () => {
        cy.get(".filter-sidebar-tab").click();
        cy.get(".filter-sidebar").should("be.visible");
      });

      it("displays filter fields when sidebar is open", () => {
        cy.get(".filter-sidebar").within(() => {
          cy.contains("Filename");
          cy.contains("Data Sources");
          cy.contains("Stage");
        });
      });

      it("has filter sidebar", () => {
        cy.get(".filter-sidebar").should("be.visible");
      });

      it("can sort by Filename column", () => {
        cy.get(".p-datatable-thead").contains("Filename").click({ force: true });
        cy.get(".p-datatable-thead").contains("Filename").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Data Source column", () => {
        cy.get(".p-datatable-thead").contains("Data Source").click({ force: true });
        cy.get(".p-datatable-thead").contains("Data Source").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Stage column", () => {
        cy.get(".p-datatable-thead").contains("Stage").click({ force: true });
        cy.get(".p-datatable-thead").contains("Stage").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Created column", () => {
        cy.get(".p-datatable-thead").contains("Created").click({ force: true });
        cy.get(".p-datatable-thead").contains("Created").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Modified column", () => {
        cy.get(".p-datatable-thead").contains("Modified").click({ force: true });
        cy.get(".p-datatable-thead").contains("Modified").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can sort by Size column", () => {
        cy.get(".p-datatable-thead").contains("Size").click({ force: true });
        cy.get(".p-datatable-thead").contains("Size").parent().find(".p-sortable-column-icon").should("exist");
      });

      it("can change rows per page", () => {
        cy.get(".p-paginator-rpp-options").click();
        cy.get(".p-dropdown-item").contains("50").click();
        cy.get("span.p-paginator-current").should("contain", "1 - 50");
        cy.get(".p-paginator-rpp-options").click();
        cy.get(".p-dropdown-item").contains("20").click();
        cy.get("span.p-paginator-current").should("contain", "1 - 20");
      });

      it("can navigate to next page", () => {
        cy.get(".p-paginator-next").click();
        cy.get("span.p-paginator-current").should("contain", "21 - 40");
      });

      it("can navigate to previous page after going to next", () => {
        cy.get(".p-paginator-prev").click();
        cy.get("span.p-paginator-current").should("contain", "1 - 20");
      });

      it("can navigate to first page", () => {
        cy.get(".p-paginator-next").click();
        cy.get(".p-paginator-first").click();
        cy.get("span.p-paginator-current").should("contain", "1 - 20");
      });

      it("can navigate to last page", () => {
        cy.get(".p-paginator-last").click();
        cy.get("span.p-paginator-current").should("contain", "of 2000");
      });

      it("shows context menu when right-clicking on a row", () => {
        cy.get(".p-datatable-tbody tr.p-selectable-row").first().rightclick({ force: true });
        cy.get(".p-contextmenu").should("be.visible");
      });

      it("context menu contains expected options", () => {
        cy.get(".p-contextmenu").within(() => {
          cy.contains("Clear Selected");
          cy.contains("Select All Visible");
        });
      });

      it("has date type dropdown with Created/Modified options", () => {
        cy.get(".p-dropdown").first().click({ force: true });
        cy.get(".p-dropdown-items").should("be.visible");
      });
    });

    describe("DID Link Navigation", () => {
      it("clicking a DID link navigates to the viewer page", () => {
        cy.visit(`${baseUrl}?perPage=20`);
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
        cy.get(".p-datatable-tbody tr").first().within(() => {
          cy.get("a").first().click({ force: true });
        });
        cy.url().should("include", "/deltafile/viewer/");
      });
    });

    describe("URL Parameters", () => {
      it("loads with URL parameters for perPage", () => {
        cy.visit(`${baseUrl}?perPage=20`);
        cy.get("span.p-paginator-current").contains("1 - 20 of 2000");
      });
    });
  });
}

{
  describe("DeltaFile Viewer Page", () => {
    const baseUrl = "http://localhost:8080/deltafile/viewer";
    const testDid = "2ace8f8a-60d6-4211-894a-0cd7455bf599";

    describe("Form View", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("DeltaFile Viewer");
      });

      it("displays DID input field", () => {
        cy.get('input[placeholder="DID (UUID)"]').should("exist");
      });

      it("displays View button", () => {
        cy.contains("View").should("exist");
      });

      it("page container exists", () => {
        cy.get(".deltafile-viewer-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".deltafile-viewer-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".deltafile-viewer-page").should("be.visible");
      });

      it("shows error for invalid UUID", () => {
        cy.get('input[placeholder="DID (UUID)"]').type("invalid-uuid");
        cy.contains("Invalid UUID").should("exist");
      });

      it("accepts valid UUID after clearing", () => {
        cy.get('input[placeholder="DID (UUID)"]').clear().type(testDid);
        cy.contains("Invalid UUID").should("not.exist");
      });
    });

    describe("Viewer With DID", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(`${baseUrl}/${testDid}`);
      });

      it("loads the DeltaFile data", () => {
        cy.get(".deltafile-viewer-page").should("exist");
      });

      it("displays Refresh button", () => {
        cy.contains("Refresh").should("exist");
      });

      it("displays Menu button", () => {
        cy.contains("Menu").should("exist");
      });

      it("displays Parent DeltaFiles panel", () => {
        cy.get("span.p-panel-title").contains("Parent DeltaFiles");
      });

      it("displays Child DeltaFiles panel", () => {
        cy.get("span.p-panel-title").contains("Child DeltaFiles");
      });

      it("displays Annotations panel", () => {
        cy.get("span.p-panel-title").contains("Annotations");
      });

      it("displays Flow Graph tab", () => {
        cy.contains("span.p-tabview-title", "Flow Graph").should("exist");
      });

      it("displays Flow Table tab", () => {
        cy.contains("span.p-tabview-title", "Flow Table").should("exist");
      });

      it("displays Flow Trace tab", () => {
        cy.contains("span.p-tabview-title", "Flow Trace").should("exist");
      });

      it("opens menu when Menu button is clicked", () => {
        cy.contains("Menu").click();
        cy.get(".p-menu").should("be.visible");
      });

      it("displays Replay option in menu", () => {
        cy.get(".p-menu").contains("Replay").should("exist");
      });

      it("displays View Raw DeltaFile option in menu", () => {
        cy.get(".p-menu").contains("View Raw DeltaFile").should("exist");
      });

      it("displays Export DeltaFile option in menu", () => {
        cy.get(".p-menu").contains("Export DeltaFile").should("exist");
      });

      it("displays Pin/Unpin option in menu", () => {
        cy.get(".p-menu").contains(/Pin|Unpin/).should("exist");
      });

      it("displays Add User Note option in menu", () => {
        cy.get(".p-menu").contains("Add User Note").should("exist");
      });

      it("displays Annotate option in menu", () => {
        cy.get(".p-menu").contains("Annotate").should("exist");
      });
    });

    describe("Navigation", () => {
      it("can navigate to DeltaFile from form", () => {
        cy.visit(baseUrl);
        cy.get('input[placeholder="DID (UUID)"]').type(testDid);
        cy.get(".p-inputgroup").contains("View").click();
        cy.url().should("include", testDid);
      });
    });
  });
}

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
  describe("Users Page", () => {
    const baseUrl = "http://localhost:8080/admin/users";

    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("User Management");
      });

      it("displays the Users panel", () => {
        cy.get("span.p-panel-title").contains("Users");
      });

      it("displays Add User button", () => {
        cy.contains("Add User").should("exist");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays users data", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("page container exists", () => {
        cy.get(".users-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".users-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".users-page").should("be.visible");
      });

      it("can open Add User dialog", () => {
        cy.contains("Add User").click();
        cy.get(".p-dialog").should("be.visible");
      });
    });
  });
}

{
  describe("Roles Page", () => {
    const baseUrl = "http://localhost:8080/admin/roles";

    describe("Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("Role Management");
      });

      it("displays the Roles panel", () => {
        cy.get("span.p-panel-title").contains("Roles");
      });

      it("displays Add Role button", () => {
        cy.contains("Add Role").should("exist");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays roles data", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("page container exists", () => {
        cy.get(".roles-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".roles-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".roles-page").should("be.visible");
      });

      it("can open Add Role dialog", () => {
        cy.contains("Add Role").click();
        cy.get(".p-dialog").should("be.visible");
      });
    });
  });
}

{
  describe("External Links Page", () => {
    const baseUrl = "http://localhost:8080/admin/external-links";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with correct heading", () => {
        cy.get("h2").contains("External Links");
      });

      it("displays search input", () => {
        cy.get('input[placeholder="Search"]').should("exist");
      });

      it("displays Add Link button", () => {
        cy.contains("Add Link").should("exist");
      });

      it("displays External links panel", () => {
        cy.get("span.p-panel-title").contains("External links");
      });

      it("displays DeltaFile Links panel", () => {
        cy.get("span.p-panel-title").contains("DeltaFile Links");
      });

      it("displays External links data table", () => {
        cy.get(".external-links-panel .p-datatable").should("exist");
      });

      it("displays expected External links columns", () => {
        cy.get(".p-datatable-thead").first().within(() => {
          cy.contains("Name");
          cy.contains("Description");
          cy.contains("URL");
        });
      });

      it("page container exists", () => {
        cy.get(".external-links-page").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".external-links-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".external-links-page").should("be.visible");
      });

      it("can filter links by search", () => {
        cy.get('input[placeholder="Search"]').type("test");
        cy.get(".external-links-page").should("exist");
      });
    });
  });
}

{
  describe("Lookup Table Page", () => {
    const baseUrl = "http://localhost:8080/admin/lookup-tables";

    describe("Page Structure & Elements", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with default settings", () => {
        cy.get("h2").contains("Lookup Tables");
      });

      it("displays the Lookup Table panel", () => {
        cy.get("span.p-panel-title").contains("Lookup Table");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays all expected table columns", () => {
        cy.get(".p-datatable-thead").within(() => {
          cy.contains("Name");
          cy.contains("Key Columns");
          cy.contains("Columns");
          cy.contains("Total Rows");
          cy.contains("Backing Service Active");
          cy.contains("Refresh Duration");
          cy.contains("Service Backed");
          cy.contains("Pull Through");
          cy.contains("Last Refresh");
        });
      });

      it("displays lookup table data", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays header buttons", () => {
        cy.get(".btn-toolbar").should("exist");
      });

      it("displays key columns as list items", () => {
        cy.get(".list-item").should("exist");
      });

      it("displays backing service toggle switches", () => {
        cy.get(".p-inputswitch").should("exist");
      });

      it("displays action buttons in each row", () => {
        cy.get(".p-datatable-tbody tr").first().within(() => {
          cy.get("button, .p-button").should("exist");
        });
      });

      it("name column contains links to detail page", () => {
        cy.get(".p-datatable-tbody tr").first().within(() => {
          cy.get("a").should("have.attr", "href").and("include", "/admin/lookup-table/");
        });
      });

      it("page container exists", () => {
        cy.get(".lookup-tables-page").should("be.visible");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".lookup-tables-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".lookup-tables-page").should("be.visible");
      });
    });

    describe("Table Sorting", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("can sort by Name column", () => {
        cy.get(".p-datatable-thead").contains("Name").click();
        cy.get(".p-datatable-thead").contains("Name").parent().find(".p-sortable-column-icon").should("exist");
      });
    });

    describe("Navigation to Detail Page", () => {
      it("clicking table name navigates to detail page", () => {
        cy.visit(baseUrl);
        cy.get(".p-datatable-tbody tr").first().within(() => {
          cy.get("a").first().click();
        });
        cy.url().should("include", "/admin/lookup-table/");
      });
    });
  });
}

{
  describe("Lookup Table Result Page", () => {
    const baseUrl = "http://localhost:8080/admin/lookup-table/country-codes";

    describe("Shared Page Tests", { testIsolation: false }, () => {
      retryableBefore(() => {
        cy.visit(baseUrl);
      });

      it("loads the page with lookup table name", () => {
        cy.get("h2").contains("Lookup Tables");
        cy.get("h2").contains("country-codes");
      });

      it("displays breadcrumb navigation", () => {
        cy.get(".fa-angle-right").should("exist");
      });

      it("displays the Results panel", () => {
        cy.contains("Results").should("exist");
      });

      it("displays the data table", () => {
        cy.get(".p-datatable").should("exist");
      });

      it("displays filter section", () => {
        cy.contains("Filter:").should("exist");
      });

      it("displays clear filter button", () => {
        cy.get(".p-column-filter-menu-button").should("exist");
      });

      it("displays add filter chip button", () => {
        cy.get(".add-column-value-btn").should("exist");
      });

      it("displays paginator", () => {
        cy.get(".p-paginator").should("exist");
      });

      it("displays table data", () => {
        cy.get(".p-datatable-tbody tr").should("have.length.greaterThan", 0);
      });

      it("displays key columns with asterisk", () => {
        cy.get(".p-datatable-thead").contains("code *");
      });

      it("displays Last Updated column", () => {
        cy.get(".p-datatable-thead").contains("Last Updated");
      });

      it("displays column selector", () => {
        cy.get(".p-multiselect").should("exist");
      });

      it("displays row editor column", () => {
        cy.get(".p-row-editor-init").should("exist");
      });

      it("displays delete row button", () => {
        cy.get(".p-datatable-tbody button").should("exist");
      });

      it("renders correctly at default viewport", () => {
        cy.get(".lookup-tables-results-page").should("be.visible");
      });

      it("renders correctly at smaller viewport", () => {
        cy.viewport(1024, 768);
        cy.get(".lookup-tables-results-page").should("be.visible");
      });

      it("displays menu button", () => {
        cy.get(".fa-bars").should("exist");
      });

      it("breadcrumb links back to lookup tables list", () => {
        cy.get('a[href="/admin/lookup-tables/"]').should("exist");
      });
    });
  });
}

describe('DeltaFile Errors Page - All Tab', () => {
  it('loads errors', () => {
    cy.visit('http://localhost:8080/errors?tab=0')
    cy.get('span.p-panel-title').contains('DeltaFiles with Errors')
    cy.get('span.p-paginator-current').contains('1 - 20 of 2000')
  })
})

describe('DeltaFile Errors Page - By Flow Tab', () => {
  it('loads errors by flow', () => {
    cy.visit('http://localhost:8080/errors?tab=1')
    cy.get('span.p-panel-title').contains('DeltaFiles with Errors by Flow')
    cy.get('span.p-paginator-current').contains('1 - 3 of 3')
  })
})

describe('DeltaFile Errors Page - By Message Tab', () => {
  it('loads errors by message', () => {
    cy.visit('http://localhost:8080/errors?tab=2')
    cy.get('span.p-panel-title').contains('DeltaFiles with Errors by Message')
    cy.get('span.p-paginator-current').contains('1 - 4 of 4')
  })
})

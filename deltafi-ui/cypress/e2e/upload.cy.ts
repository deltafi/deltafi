describe('DeltaFile Upload Page', () => {
  it('Can add metadata', () => {
    cy.visit('http://localhost:8080/deltafile/upload')
    const addButton = cy.get('span').contains('Add Metadata Field')
    Cypress._.times(3, () => {
      cy.wait(250)
      addButton.click()
    })
  })
})

describe('DeltaFile Viewer Page', () => {
  it('Can view a DeltaFile domain', () => {
    cy.visit('http://localhost:8080/deltafile/viewer/27186720-723a-4f82-a5ab-2fff441b2c9b')
    cy.get('strong').contains('xml').click();
  })
})

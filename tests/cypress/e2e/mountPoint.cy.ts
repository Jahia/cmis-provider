describe('Mount tests', () => {
    after(() => {
        cy.logout();
    });

    beforeEach(() => {
        cy.loginEditor();
    });

    it('It creates mount point', () => {
        // Go to editframe to avoid iframe issues
        cy.visit(`${Cypress.env('JAHIA_URL')}/cms/adminframe/default/en/settings.manageMountPoints.html?redirect=false`);
        cy.get('select[id="mountPointFactory"]').should('be.visible').select('CMIS mount point')
        cy.get('button[data-sel-role="addMountPoint"]').should('be.visible').click();

        // Cannot create empty mountpoint
        cy.get('button[name="_eventId_save"]').should('be.visible').click();
        cy.get('div').contains('Please, provide value for Name');

        // Test invalid connector
        cy.get('select[ng-model="cmisType"]').should('be.visible').select('standard CMIS connector');
        cy.get('input[name="name"]').should('be.visible').type('Alfresco');
        cy.get('input[name="user"]').should('be.visible').type('admin');
        cy.get('input[name="password"]').should('be.visible').type('admin');
        cy.get('input[name="url"]').should('be.visible').type(`${Cypress.env('ALFRESCO_URL')}/alfresco`);
        cy.get('button[name="_eventId_save"]').should('be.visible').click();
        cy.get('div').contains('Please, provide value for Repository ID');

        // Invalid username
        cy.get('select[ng-model="cmisType"]').should('be.visible').select('Alfresco impersonation connector');
        cy.get('input[name="user"]').should('be.visible').clear().type('wrong_admin');
        cy.get('button[name="_eventId_save"]').should('be.visible').click();
        cy.get('div').contains('HTTP 401 Unauthorized');

        // Invalid password
        cy.get('input[name="user"]').should('be.visible').clear().type('admin');
        cy.get('input[name="password"]').should('be.visible').clear().type('wrong_admin');
        cy.get('button[name="_eventId_save"]').should('be.visible').click();
        cy.get('div').contains('HTTP 401 Unauthorized');

        // Correct info
        cy.get('input[name="password"]').should('be.visible').clear().type('admin');
        cy.get('button[name="_eventId_save"]').should('be.visible').click();
        cy.get('span').contains('Mounted');
    });
});

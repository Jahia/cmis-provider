/**
 * NOTE: you must run create mount point test before this test as it relies on established connection
 */
describe('Content creation tests', () => {
    after(() => {
        cy.logout();
    });

    // This sets up users and groups on Alfresco and Jahia
    // Assume that mount point test has run and we have a connection to Alfresco
    // For reference see https://jahia.testrail.net/index.php?/cases/view/1088
    before(() => {
        cy.loginEditor();
        // Login
        cy.visit(`${Cypress.env('ALFRESCO_URL')}/share/page/console/admin-console/application`);
        cy.get('input[name="username"]').should('be.visible').type('admin');
        cy.get('input[name="password"]').should('be.visible').type('admin');
        cy.get('button').contains('Login').click();

        // Create Groups
        cy.get('a[title="Group Management"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-browse-button"]').click();
        cy.get('span[title="New Group"]').click();
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-shortname"]').type('contributors');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-displayname"]').type('contributors');
        cy.get('button').contains('Create and Create Another').click();

        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-shortname"]').type('readers');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-displayname"]').type('readers');
        cy.get('button').contains('Create Group').click();

        // Add Users
        cy.get('span').contains('contributors').click();
        cy.get('span[title="Add User"]').click();
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-text"]').type('blachance7');
        cy.get('button[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-button-button"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-action-blachance7"]').find('button').click();

        cy.get('span').contains('readers').click();
        cy.get('span[title="Add User"]').click();
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-text"]').type('blachance4');
        cy.get('button[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-button-button"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-action-blachance4"]').find('button').click();
    });

    after(() => {
        cy.visit(`${Cypress.env('ALFRESCO_URL')}/share/page/console/admin-console/application`);
        cy.get('input[name="username"]').should('be.visible').type('admin');
        cy.get('input[name="password"]').should('be.visible').type('admin');
        cy.get('button').contains('Login').click();

        // eslint-disable-next-line cypress/no-unnecessary-waiting
        cy.wait(500);

        cy.get('a[title="Group Management"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-browse-button"]').click();
        cy.get('span').contains('contributors').click().parent().find('.groups-delete-button').click();
        cy.get('button').contains('Delete').click();
        cy.get('span').contains('readers').click().parent().find('.groups-delete-button').click();
        cy.get('button').contains('Delete').click();
    });

    beforeEach(() => {
        cy.loginEditor();
    });

    it('It creates content', () => {
        // WILL DO
    });
});

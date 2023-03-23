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
        cy.visit(`${Cypress.env('PROXY_URL')}/share/page/console/admin-console/application`);
        cy.get('input[name="username"]').should('be.visible').type('admin');
        cy.get('input[name="password"]').should('be.visible').type('admin');
        cy.get('button').contains('Sign In').click();

        // Add users Bill
        cy.get('a[title="User Management"]').click();
        cy.get('button').contains('New User').click();
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-firstname"]').type('Bill');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-lastname"]').type('Galileo');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-email"]').type('bill@jahia.com');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-username"]').type('bill');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-password"]').type('alfresco');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-verifypassword"]').type('alfresco');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-quota"]').type('5');
        cy.get('select[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-quotatype"]').select('MB');
        cy.get('button').contains('Create and Start Another');

        // Add user Anne
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-firstname"]').clear().type('Anne');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-lastname"]').clear().type('Lovelace');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-email"]').clear().type('anne@jahia.com');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-username"]').clear().type('anne');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-password"]').clear().type('alfresco');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-verifypassword"]').clear().type('alfresco');
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-quota"]').clear().type('5');
        cy.get('select[id="page_x002e_ctool_x002e_admin-console_x0023_default-create-quotatype"]').select('MB');
        cy.get('button').contains('Create and Start Another');

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
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-text"]').type('bill galileo');
        cy.get('button[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-button-button"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-action-bill"]').find('button').click();
        cy.get('span[title="Add User"]').click();
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-text"]').type('user7');
        cy.get('button[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-button-button"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-action-user7"]').find('button').click();

        cy.get('span').contains('readers').click();
        cy.get('span[title="Add User"]').click();
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-text"]').type('anne lovelace');
        cy.get('button[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-button-button"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-action-anne"]').find('button').click();
        cy.get('span[title="Add User"]').click();
        cy.get('input[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-text"]').type('user4');
        cy.get('button[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-search-button-button"]').click();
        cy.get('span[id="page_x002e_ctool_x002e_admin-console_x0023_default-search-peoplefinder-action-user4"]').find('button').click();

    })

    beforeEach(() => {
        cy.loginEditor();
    });

    it('It creates content', () => {
        // Todo
    });
});

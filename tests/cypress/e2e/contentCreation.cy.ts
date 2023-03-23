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

    })

    beforeEach(() => {
        cy.loginEditor();
    });

    it('It creates content', () => {
        // Todo
    });
});

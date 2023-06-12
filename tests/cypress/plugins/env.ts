module.exports = (on, config) => {
    if (process.env.ALFRESCO_URL) {
        config.env.ALFRESCO_URL = process.env.ALFRESCO_URL;
    } else {
        config.env.ALFRESCO_URL = 'http://localhost:9080';
    }

    return config;
};

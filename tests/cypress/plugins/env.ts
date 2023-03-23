module.exports = (on, config) => {
    config.baseUrl = process.env.JAHIA_URL;
    config.env.AS_SITEKEY = 'digitall';
    config.env.ALFRESCO_URL = process.env.ALFRESCO_URL;
    config.env.PROXY_URL = process.env.PROXY_URL;
    return config
}
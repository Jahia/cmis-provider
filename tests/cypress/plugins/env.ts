module.exports = (on, config) => {
    if (!process.env.SHARE_URL) {
        config.env.SHARE_URL = 'http://localhost:9081';
    } else {
        config.env.SHARE_URL = process.env.SHARE_URL;
    }

    return config;
};

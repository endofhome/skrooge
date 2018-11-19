const path = require('path');

module.exports = {
    entry: "./src/main/javascript/chart",
    mode: "none",
    output: {
        path: path.resolve(__dirname, "public"),
        filename: "bundle.js",
    }
};
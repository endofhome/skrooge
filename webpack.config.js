const path = require('path');

module.exports = {
    entry: "./src/main/javascript/chart",
    output: {
        path: path.resolve(__dirname, "public"),
        filename: "bundle.js",
    }
};
const ScalaJS = require('./scalajs.webpack.config');
const Merge = require('webpack-merge');

const WebApp = Merge(ScalaJS);

module.exports = WebApp;

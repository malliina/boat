const BaseWebpack = require('./webpack.base.config');
const { merge } = require('webpack-merge');

module.exports = merge(BaseWebpack, {
  mode: 'production',
  // https://github.com/scalacenter/scalajs-bundler/issues/350#issuecomment-977554606
  performance: {
    hints: false,
  }
});

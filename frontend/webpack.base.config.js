const ScalaJS = require('./scalajs.webpack.config');
const { merge } = require('webpack-merge');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const path = require('path');
const rootDir = path.resolve(__dirname, '../../../..');
const cssDir = path.resolve(rootDir, 'src/main/resources/css');
const vendorsDir = path.resolve(rootDir, 'src/main/resources/vendors');

const WebApp = merge(ScalaJS, {
  node: {
    fs: 'empty'
  },
  entry: {
    styles: [path.resolve(cssDir, './boat.js')],
    fonts: [path.resolve(cssDir, './fonts.js')],
    vendors: [path.resolve(vendorsDir, './vendors.js')]
  },
  module: {
    rules: [
      {
        test: /\.less$/,
        use: [
          MiniCssExtractPlugin.loader,
          { loader: 'css-loader', options: { importLoaders: 1, url: true } },
          'postcss-loader',
          'less-loader'
        ]
      },
      {
        test: /\.css$/,
        use: [
          MiniCssExtractPlugin.loader,
          { loader: 'css-loader', options: { importLoaders: 1, url: true } },
          'postcss-loader'
        ]
      },
      {
        test: /\.(png|woff|woff2|eot|ttf|svg)$/,
        use: [
          { loader: 'url-loader', options: { limit: 8192, name: 'static/assets/[name]-[hash].[ext]' } }
        ],
        exclude: /node_modules/
      },
      {
        test: /\.(png|woff|woff2|eot|ttf|svg)$/,
        use: [
          { loader: 'file-loader', options: { name: 'static/fonts/[name]-[hash].[ext]' } }
        ],
        include: /node_modules/
      }
    ]
  },
  plugins: [
    new MiniCssExtractPlugin({filename: '[name].css'})
  ]
});

module.exports = WebApp;

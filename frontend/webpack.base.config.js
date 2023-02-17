const ScalaJS = require('./scalajs.webpack.config');
const { merge } = require('webpack-merge');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const path = require('path');
const rootDir = path.resolve(__dirname, '../../../..');
const resourcesDir = path.resolve(rootDir, 'src/main/resources');
const cssDir = path.resolve(resourcesDir, 'css');

const WebApp = merge(ScalaJS, {
  entry: {
    styles: [path.resolve(cssDir, './boat.js')],
    fonts: [path.resolve(cssDir, './fonts.js')]
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: [
          MiniCssExtractPlugin.loader,
          { loader: 'css-loader', options: { importLoaders: 1, url: true } }
        ],
        include: [ path.resolve(__dirname, 'node_modules') ]
      },
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
        test: /\.(woff|woff2)$/,
        type: 'asset/inline', // exports a data URI of the asset
        include: [
            path.resolve(resourcesDir, 'fonts')
        ]
      },
      {
        test: /\.(eot|ttf|svg)$/,
        type: 'asset/resource', // emits a separate file and exports the URL
        generator: {
          filename: 'static/fonts/[name]-[hash][ext]'
        }
      },
      {
        test: /\.(png|svg)$/,
        type: 'asset', // automatically chooses between exporting a data URI and emitting a separate file
        include: [
          resourcesDir
        ],
        generator: {
          filename: 'static/img/[name]-[hash][ext]'
        }
      },
      {
        test: /\.(png|svg)$/,
        type: 'asset/resource',
        include: [
          path.resolve(resourcesDir, 'images')
        ],
        generator: {
          filename: 'img/[name][ext]'
        }
      }
    ]
  },
  plugins: [
    new MiniCssExtractPlugin({filename: '[name].css'})
  ]
});

module.exports = WebApp;

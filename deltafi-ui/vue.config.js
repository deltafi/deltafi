const DELTAFI_DOMAIN = process.env.DELTAFI_DOMAIN || "dev.deltafi.org";
const execSync = require('child_process').execSync;

module.exports = {
  devServer: {
    host: "localhost",
    compress: false,
    proxy: {
      "^/api": {
        target: `https://${DELTAFI_DOMAIN}`,
        bypass: (req, res) => {
          if (req.originalUrl === '/api/v1/local-git-branch' && process.env.NODE_ENV === "development") {
            const branch = execSync('git rev-parse --abbrev-ref HEAD').slice(0, -1).toString()
            res.json({ branch: branch })
            return null;
          }
        },
      },
      "^/deltafile/ingress": {
        target: `https://ingress.${DELTAFI_DOMAIN}`,
      },
      "^/deltafile/annotate": {
        target: `https://${DELTAFI_DOMAIN}`,
      },
    },
  },
  pwa: {
    manifestOptions: {
      icons: [],
    },
  },
  transpileDependencies: ["@jsonforms/core", "@jsonforms/vue", "@jsonforms/vue-vanilla"],
  configureWebpack: (config) => {
    config.module.rules = [
      {
        test: /\.(ts|tsx)$/,
        exclude: /node_modules/,
        use: {
          loader: "ts-loader",
        },
      },
      {
        test: /\.worker\.(j|t)s$/i,
        use: [
          {
            loader: "comlink-loader-webpack5",
            options: {
              singleton: true,
            },
          },
        ],
      },
      ...config.module.rules,
    ];
  },
  chainWebpack: (config) => {
    config.plugin("copy").tap(([options]) => {
      [options][0].patterns[0].globOptions.ignore.push("mockServiceWorker.js");
      return [options];
    });
  },
};

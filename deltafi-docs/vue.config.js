/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

const fs = require("fs");
const path = require("path");
const glob = require('glob');

function getLatestVersion(changelogPath) {
  const content = fs.readFileSync(changelogPath, "utf8");

  // Match the first occurrence of a version line like: ## [2.31.0] - 2025-09-03
  const match = content.match(/^## \[(\d+\.\d+\.\d+)\]/m);
  if (!match) {
    throw new Error("No version found in CHANGELOG.md");
  }

  return match[1]; // just the version number
}

module.exports = {
  runtimeCompiler: true,
  publicPath: process.env.VUE_APP_EMBEDDED === 'true' ? '/docs/' : '/',
  configureWebpack: {
    plugins: [
      // Plugin to extract version from CHANGELOG
      new (require('webpack')).DefinePlugin({
        'process.env': {
          VUE_APP_VERSION: process.env.VUE_APP_EMBEDDED === 'true' ? null : JSON.stringify(getLatestVersion(path.resolve(__dirname, '../CHANGELOG.md')))
        }
      }),
      // Plugin to copy core action docs and CHANGELOG
      {
        apply: (compiler) => {
          compiler.hooks.beforeRun.tap('CopyDocsPlugin', () => {
            if (process.env.VUE_APP_EMBEDDED === 'true') {
              console.log('Skipping doc copy because VUE_APP_EMBEDDED=true');
              return;
            }

            const targetDir = path.resolve(__dirname, 'public/docs/core-actions');
            fs.mkdirSync(targetDir, { recursive: true });

            fs.copyFileSync(
              path.resolve(__dirname, '../CHANGELOG.md'),
              path.resolve(__dirname, 'public/docs/CHANGELOG.md')
            );

            // copy all org.deltafi.core.action.*.md files
            const srcPattern = path.resolve(
              __dirname,
              '../deltafi-core-actions/src/main/resources/docs/org.deltafi.core.action.*.md'
            );
            const files = glob.sync(srcPattern);
            files.forEach((file) => {
              const destFile = path.resolve(targetDir, path.basename(file));
              fs.copyFileSync(file, destFile);
            });

            console.log('Copied docs successfully');
          });
        },
      },
      // Plugin to replace __VERSION__ placeholders in Markdown files
      {
        apply: (compiler) => {
          compiler.hooks.done.tap('ReplaceVersion', () => {
            const version = getLatestVersion(path.resolve(__dirname, 'dist/docs/CHANGELOG.md'));
            const files = glob.sync(path.resolve(__dirname, 'dist/docs/**/*.md'));
            files.forEach((file) => {
              let content = fs.readFileSync(file, 'utf8');
              content = content.replace(/__VERSION__/g, version);
              fs.writeFileSync(file, content);
            });

            console.log('Replaced __VERSION__ placeholders successfully');
          });
        },
      }
    ]
  }
}

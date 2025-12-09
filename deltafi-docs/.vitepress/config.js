import { defineConfig } from "vitepress";
import { loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import fs from "fs";
import { resolve, basename } from "path";
import fg from "fast-glob";
import { parse } from "csv-parse/sync";

function isHtmlTag(tag) {
  // Remove angle brackets
  tag = tag.replace(/[<>]/g, "");
  // Normalize
  tag = tag.toLowerCase();

  // Common HTML5 tag list
  const htmlTags = new Set(["div", "span", "p", "a", "ul", "ol", "li", "table", "thead", "tbody", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6", "input", "textarea", "button", "select", "option", "label", "form", "img", "video", "audio", "canvas", "svg", "path", "circle", "rect", "g", "script", "style", "link", "meta", "head", "body", "html", "br", "hr", "strong", "em", "b", "i", "u", "code", "pre", "blockquote", "nav", "footer", "header", "section", "article", "aside", "main"]);

  return htmlTags.has(tag);
}

function globalReplace(fileNamePattern, searchValue, replaceValue) {
  const files = fg.sync(resolve(__dirname, fileNamePattern));
  files.forEach((file) => {
    let content = fs.readFileSync(file, 'utf8');
    content = content.replace(searchValue, replaceValue);
    fs.writeFileSync(file, content);
  });
}

function getLatestVersion() {
  const changelogPath = resolve(__dirname, "../docs/CHANGELOG.md");
  const content = fs.readFileSync(changelogPath, "utf8");
  const match = content.match(/^## \[(\d+\.\d+\.\d+)\]/m);
  if (!match) throw new Error("No version found in CHANGELOG.md");
  return match[1];
}

function copyDocs() {
  if (process.env.VUE_APP_EMBEDDED === "true") {
    console.log("Skipping doc copy because VUE_APP_EMBEDDED=true");
    return;
  }

  console.log("Copying docs from", resolve(__dirname, "../../CHANGELOG.md"));
  console.log("Copying docs to", resolve(__dirname, "../docs/CHANGELOG.md"));

  // Copy DeltaFi CHANGELOG.md from root of DeltaFi repo to DeltaFi docs
  fs.copyFileSync(resolve(__dirname, "../../CHANGELOG.md"), resolve(__dirname, "../docs/CHANGELOG.md"));

  // Make a directory for core actions
  const targetDir = resolve(__dirname, "../docs/core-actions");
  fs.mkdirSync(targetDir, { recursive: true });

  // Copy core actions docs
  const srcPattern = resolve(__dirname, "../../deltafi-core-actions/src/main/resources/docs/org.deltafi.core.action.*.md");
  const files = fg.sync(srcPattern);
  files.forEach((file) => {
    const destFile = resolve(targetDir, basename(file));
    fs.copyFileSync(file, destFile);
  });

  console.log("Copied docs successfully");
}

function updateVersionInDocs() {
  const version = getLatestVersion();
  console.log("Replacing __VERSION__ with", version, "in built docs...");
  globalReplace('./dist/**/*.{js,html}', /__VERSION__/g, version);
}

function injectPermissionsInDocs() {
  console.log("Injecting permissions into markdown");
  const base = process.env.VUE_APP_EMBEDDED ? "../docs" : "../../deltafi-core/src/main/resources";
  const csvFile = resolve(__dirname, `${base}/permissions.csv`);
  const csvContent = fs.readFileSync(csvFile, 'utf8');
  const records = parse(csvContent, { columns: true });
  let permissionsTable = "<table><tr><th>Name</th><th>Category</th><th>Description</th></tr>";
  records.forEach((record) => {
    permissionsTable += `<tr><td>${record.Name}</td><td>${record.Category}</td><td>${record.Description}</td></tr>`;
  });
  permissionsTable += "</table>"; // Close table
  globalReplace('./dist/**/*.{js,html}', /PERMISSIONS_TABLE/g, permissionsTable);
}

function cleanupChangelog() {
  console.log("Cleaning up changelog");
  globalReplace('./dist/**/*.{js,html}', /\[(\d+\.\d+\.\d+)\]/g, "$1");
}

function generateDocsSidebar() {
  console.log("Generating docs sidebar...");
  const sidebarCoreActions = [];
  // VUE_APP_EMBEDDED set TRUE when running vitepress in app, otherwise vitepress is served from netlify for public docs
  const coreActionDocsPath = process.env.VUE_APP_EMBEDDED ? "../docs/core-actions" : "../../deltafi-core-actions/src/main/resources/docs";
  const srcPattern = resolve(__dirname, `${coreActionDocsPath}/org.deltafi.core.action.*.md`);
  const files = fg.sync(srcPattern);
  files.forEach((file) => {
    const fileName = basename(file);
    const fileNameParts = fileName.split('.');
    const actionName = fileNameParts[fileNameParts.length - 2]; // Extract action name from file name
    sidebarCoreActions.push({ name: actionName, link: `/core-actions/${fileName}` })
  });
  sidebarCoreActions.sort((a, b) => a.name.localeCompare(b.name));

  const sidebarJson = fs.readFileSync(resolve(__dirname, 'sidebar.json'), 'utf8');
  const sidebarContent = JSON.parse(sidebarJson);

  // Inject core action menu items into the sidebar
  const coreActionsSection = sidebarContent.find(section => section.text === "Core Actions");
  if (coreActionsSection) {
    coreActionsSection.items = sidebarCoreActions.map(action => ({
      text: action.name,
      link: action.link
    }));
  } else {
    throw new Error("Core Actions section not found in sidebar.json");
  }

  return sidebarContent;
}

// run immediately when config loads
copyDocs();

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    base: env.VUE_APP_EMBEDDED === 'true' ? '/docs/' : '/',
    srcDir: "docs",
    description: "A data transformation, normalization, and enrichment platform that handles all the details, so that you can focus on your business logic.",
    head: [["link", { rel: "icon", href: "/favicon.ico" }]],
    themeConfig: {
      siteTitle: false,
      logo: {
        light: "/logo.png", // Path to your light mode logo in the public folder
        dark: "/logo-dark.png", // Path to your dark mode logo in the public folder
      },
      // https://vitepress.dev/reference/default-theme-config
      nav: [
        {
          text: `v${getLatestVersion()}`,
          items: [
            { text: 'Changelog', link: '/CHANGELOG' },
            { text: 'Contributing', link: '/contributing' },
            { text: 'v1.2.20', link: 'https://v1.docs.deltafi.org/' }
          ]
        }
      ],
      search: {
        provider: "local",
      },
      sidebar: generateDocsSidebar(),
      socialLinks: [
        {
          icon: {
            svg: `
            <svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M22.71 14.42L21.07 9.46a.54.54 0 00-.03-.09l-2.12-5.77a.52.52 0 00-.98 0l-2.12 5.77H8.18L6.06 3.6a.52.52 0 00-.98 0L2.96 9.37l-1.65 5a.54.54 0 00.19.6l10.5 7.62c.19.14.46.14.65 0l10.5-7.62a.54.54 0 00.16-.55z"/>
            </svg>
          `,
          },
          link: "https://gitlab.com/deltafi/deltafi",
        },
        {
          icon: {
            svg: `
            <svg viewBox="0 0 512 512" id="_x30_1" version="1.1" xml:space="preserve" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
              <g id="SVGRepo_bgCarrier" stroke-width="0"></g><g id="SVGRepo_tracerCarrier" stroke-linecap="round" stroke-linejoin="round"></g><g id="SVGRepo_iconCarrier"><path d="M256,0C114.615,0,0,114.615,0,256s114.615,256,256,256s256-114.615,256-256S397.385,0,256,0z M418.275,146h-46.667 c-5.365-22.513-12.324-43.213-20.587-61.514c15.786,8.776,30.449,19.797,43.572,32.921C403.463,126.277,411.367,135.854,418.275,146 z M452,256c0,17.108-2.191,33.877-6.414,50h-64.034c1.601-16.172,2.448-32.887,2.448-50s-0.847-33.828-2.448-50h64.034 C449.809,222.123,452,238.892,452,256z M256,452c-5.2,0-21.048-10.221-36.844-41.813c-6.543-13.087-12.158-27.994-16.752-44.187 h107.191c-4.594,16.192-10.208,31.1-16.752,44.187C277.048,441.779,261.2,452,256,452z M190.813,306 c-1.847-16.247-2.813-33.029-2.813-50s0.966-33.753,2.813-50h130.374c1.847,16.247,2.813,33.029,2.813,50s-0.966,33.753-2.813,50 H190.813z M60,256c0-17.108,2.191-33.877,6.414-50h64.034c-1.601,16.172-2.448,32.887-2.448,50s0.847,33.828,2.448,50H66.414 C62.191,289.877,60,273.108,60,256z M256,60c5.2,0,21.048,10.221,36.844,41.813c6.543,13.087,12.158,27.994,16.752,44.187H202.404 c4.594-16.192,10.208-31.1,16.752-44.187C234.952,70.221,250.8,60,256,60z M160.979,84.486c-8.264,18.301-15.222,39-20.587,61.514 H93.725c6.909-10.146,14.812-19.723,23.682-28.593C130.531,104.283,145.193,93.262,160.979,84.486z M93.725,366h46.667 c5.365,22.513,12.324,43.213,20.587,61.514c-15.786-8.776-30.449-19.797-43.572-32.921C108.537,385.723,100.633,376.146,93.725,366z M351.021,427.514c8.264-18.301,15.222-39,20.587-61.514h46.667c-6.909,10.146-14.812,19.723-23.682,28.593 C381.469,407.717,366.807,418.738,351.021,427.514z"></path></g>
            </svg>
          `,
          },
          link: "https://deltafi.org/",
        },

      ],
      footer: {
        message: '<a href="https://deltafi.org/#contact">Contact US</a>',
        copyright: "Copyright © 2021-2025 DeltaFi. All rights reserved.",
      },
    },
    markdown: {
      config: (md) => {
        // Configs fix Vitepress' Markdown issues with several of DeltaFi's MD files
        //
        // --- TABLES ---
        //
        // Fixes issue were Vitepress tries "interpolation" on several strings in tables.
        // Ex: In action_parameters.md strings like `{{ deltaFileName }}` in tables cause interpolation issues.
        const defaultTableOpen = md.renderer.rules.table_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options));
        const defaultTableClose = md.renderer.rules.table_close || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options));

        md.renderer.rules.table_open = (tokens, idx, options, env, self) => {
          return `<div v-pre>` + defaultTableOpen(tokens, idx, options, env, self);
        };
        md.renderer.rules.table_close = (tokens, idx, options, env, self) => {
          return defaultTableClose(tokens, idx, options, env, self) + `</div>`;
        };

        //
        // --- INLINE CODE (backticks, including inside lists) ---
        //
        // Ex: In action_parameters.md strings like `https://api.service/{{ deltaFileName.toLowerCase() }}` cause interpolation issues.
        // Ex: In kind.md strings like `<blah>` in list cause interpolation issues.
        const defaultCodeInline = md.renderer.rules.code_inline || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options));

        md.renderer.rules.code_inline = (tokens, idx, options, env, self) => {
          let content = tokens[idx].content;
          // Escape moustaches
          content = content.replace(/{{/g, "&#123;&#123;").replace(/}}/g, "&#125;&#125;");
          // Escape angle brackets so Vue doesn't think it's a tag
          content = content.replace(/</g, "&lt;").replace(/>/g, "&gt;");
          return `<code v-pre>${content}</code>`;
        };

        //
        // --- Escape <Something> globally ---
        //
        // Handle inline HTML tokens (like <TransformResult>)
        // Ex: In CHANGELOG.md strings like `<TransformResult>` cause interpolation issues.
        const defaultHtmlInline = md.renderer.rules.html_inline || ((tokens, idx) => tokens[idx].content);

        md.renderer.rules.html_inline = (tokens, idx, options, env, self) => {
          let content = tokens[idx].content;
          // If it looks like <Word> (not a known HTML tag), escape it
          if (/^<([A-Za-z][A-Za-z0-9_:.]*)>$/.test(content)) {
            if (!isHtmlTag(content)) {
              return content.replace(/</g, "&lt;").replace(/>/g, "&gt;");
            }
          }
          return defaultHtmlInline(tokens, idx, options, env, self);
        };

        // --- CUSTOM INLINE RULES ---
        // Links at the end of CHANGELOG.md were not being hyperlinked
        // Custom inline rule: turn reference-style link definitions into hyperlinks
        const originalRender = md.render.bind(md);

        md.render = (src, env) => {
          // Match `[Label]: URL` style lines
          const withLinks = src.replace(/^\[([^\]]+)\]:\s+(https?:\/\/\S+)/gm, (_full, label, url) => {
            return `[${label}](${url})\n`;
          });

          return originalRender(withLinks, env);
        };
      },
    },
    optimizeDeps: {
      exclude: ["@vueuse/core", "vitepress"],
    },

    // Error Message overlay if something goes wrong
    server: {
      hmr: {
        overlay: false,
      },
    },

    vite: {
      publicDir: "../public", // resolved relative to root -- e.g. docs if you run vitepress build docs
    },

    plugins: [
      vue(),
      // Auto-import Vue components
      Components({
        dirs: [".vitepress/theme/components"],
        extensions: ["vue", "md"],
        include: [/\.vue$/, /\.vue\?vue/, /\.md$/],
      }),
    ],

    // Optional: ignore certain dead links to not block build
    ignoreDeadLinks: ["/install/plugins", "/install/core", "/install/ansible", "/configuration"],

    // Run a script after build completes
    buildEnd() {
      updateVersionInDocs();
      injectPermissionsInDocs();
      cleanupChangelog();
    }
  };
});

import { defineConfig } from "vitepress";
import { loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import fs from "fs";
import { resolve, basename } from "path";
import glob from "glob";

import { sidebar } from "./sidebar.js"; // Import the sidebar configuration

function isHtmlTag(tag) {
  // Remove angle brackets
  tag = tag.replace(/[<>]/g, "");
  // Normalize
  tag = tag.toLowerCase();

  // Common HTML5 tag list
  const htmlTags = new Set(["div", "span", "p", "a", "ul", "ol", "li", "table", "thead", "tbody", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6", "input", "textarea", "button", "select", "option", "label", "form", "img", "video", "audio", "canvas", "svg", "path", "circle", "rect", "g", "script", "style", "link", "meta", "head", "body", "html", "br", "hr", "strong", "em", "b", "i", "u", "code", "pre", "blockquote", "nav", "footer", "header", "section", "article", "aside", "main"]);

  return htmlTags.has(tag);
}

function getLatestVersion(changelogPath) {
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
  const files = glob.sync(srcPattern);
  files.forEach((file) => {
    const destFile = resolve(targetDir, basename(file));
    fs.copyFileSync(file, destFile);
  });

  console.log("Copied docs successfully");
}

function updateVersionInDocs() {
  const version = getLatestVersion(resolve(__dirname, "../docs/CHANGELOG.md"));
  console.log("Replacing __VERSION__ with", version, "in built docs...");
  const files = glob.sync(resolve(__dirname, './dist/**/*.{js,html}'));
  files.forEach((file) => {
    let content = fs.readFileSync(file, 'utf8');
    content = content.replace(/__VERSION__/g, version);
    fs.writeFileSync(file, content);
  });
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
          text: "Home",
          link: "/",
        },
        {
          text: "deltafi.org",
          link: "https://deltafi.org",
        },
      ],
      search: {
        provider: "local",
      },
      sidebar: sidebar, // Assign the imported sidebar
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
      ],
      footer: {
        message: '<a href="https://deltafi.org/#contact">Contact US</a>',
        copyright: "Copyright © 2022-2025 DeltaFi. All rights reserved.",
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
    }
  };
});

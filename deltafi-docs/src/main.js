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

import Docute from "docute";
import RemoveMd from "remove-markdown";

function collectPaths(items, acc = []) {
  for (const item of items) {
    if (item.link) {
      acc.push({ title: item.title, link: item.link });
    }
    if (item.children) {
      collectPaths(item.children, acc);
    }
  }
  return acc;
}

// fetch markdown for each link and build a search index
async function buildSearchIndex(sidebar) {
  const paths = collectPaths(sidebar);
  const docs = [];

  for (const path of paths) {
    try {
      let file;
      if (path.link === "/") {
        file = "/README.md";
      } else {
        file = path.link.endsWith(".md") ? path.link : `${path.link}.md`;
      }

      const res = await fetch(`docs${file}`);
      const text = await res.text();

      docs.push({
        title: path.title,
        link: path.link,
        body: RemoveMd(text),
      });
    } catch (err) {
      console.warn(`Could not fetch ${path.link}`, err);
    }
  }

  return docs;
}

const sidebar = [
  {
    title: "Introduction",
    link: "/",
  },
  {
    title: "Concepts",
    link: "/concepts",
  },
  {
    title: "Getting Started",
    children: [
      {
        title: "Quick Start With Docker Compose",
        link: "/getting-started/quick-start",
      },
      {
        title: "Extending DeltaFi with Plugins",
        link: "/getting-started/install-plugins",
      },
      {
        title: "Creating a Simple Plugin",
        link: "/getting-started/simple-plugin",
      },
    ],
  },
  {
    title: "Installation",
    children: [
      {
        title: "Docker Compose",
        link: "/install/compose",
      },
      {
        title: "Kubernetes",
        link: "/install/kubernetes",
      },
      {
        title: "KinD (Kubernetes in Docker)",
        link: "/install/kind",
      },
    ],
  },
  {
    title: "Configuration",
    children: [
      {
        title: "Authentication",
        link: "/config/authentication",
      },
    ],
  },
  {
    title: "Plugins",
    children: [
      {
        title: "Creating a Plugin",
        link: "/plugins",
      },
      {
        title: "Actions",
        link: "/actions",
      },
      {
        title: "Timed Ingress Action",
        link: "/actions/timed_ingress",
      },
      {
        title: "Transform Action",
        link: "/actions/transform",
      },
      {
        title: "Egress Action",
        link: "/actions/egress",
      },
      {
        title: "Flows",
        link: "/flows",
      },
      {
        title: "Action Parameters",
        link: "/action_parameters",
      },
      {
        title: "Action Unit Testing",
        link: "/unit-test",
      },
    ],
  },
  {
    title: "Core Actions",
    children: [
      {
        title: "Annotate",
        link: "/core-actions/org.deltafi.core.action.annotate.Annotate",
      },
      {
        title: "Compress",
        link: "/core-actions/org.deltafi.core.action.compress.Compress",
      },
      {
        title: "Convert",
        link: "/core-actions/org.deltafi.core.action.convert.Convert",
      },
      {
        title: "Decompress",
        link: "/core-actions/org.deltafi.core.action.compress.Decompress",
      },
      {
        title: "Delay",
        link: "/core-actions/org.deltafi.core.action.delay.Delay",
      },
      {
        title: "DeleteContent",
        link: "/core-actions/org.deltafi.core.action.delete.DeleteContent.md",
      },
      {
        title: "Error",
        link: "/core-actions/org.deltafi.core.action.error.Error",
      },
      {
        title: "ExtractJson",
        link: "/core-actions/org.deltafi.core.action.extract.ExtractJson",
      },
      {
        title: "ExtractXml",
        link: "/core-actions/org.deltafi.core.action.extract.ExtractXml",
      },
      {
        title: "Filter",
        link: "/core-actions/org.deltafi.core.action.filter.Filter",
      },
      {
        title: "HttpEgress",
        link: "/core-actions/org.deltafi.core.action.egress.HttpEgress.md",
      },
      {
        title: "JoltTransform",
        link: "/core-actions/org.deltafi.core.action.jolt.JoltTransform",
      },
      {
        title: "Merge",
        link: "/core-actions/org.deltafi.core.action.merge.Merge",
      },
      {
        title: "MetadataToContent",
        link: "/core-actions/org.deltafi.core.action.metadata.MetadataToContent",
      },
      {
        title: "ModifyMediaType",
        link: "/core-actions/org.deltafi.core.action.mediatype.ModifyMediaType",
      },
      {
        title: "ModifyMetadata",
        link: "/core-actions/org.deltafi.core.action.metadata.ModifyMetadata",
      },
      {
        title: "Split",
        link: "/core-actions/org.deltafi.core.action.split.Split",
      },
      {
        title: "XmlEditor",
        link: "/core-actions/org.deltafi.core.action.xml.XmlEditor",
      },
      {
        title: "XsltTransform",
        link: "/core-actions/org.deltafi.core.action.xslt.XsltTransform",
      },
    ],
  },
  {
    title: "Operating",
    children: [
      {
        title: "Ingress",
        link: "/operating/ingress",
      },
      {
        title: "TUI (Text User Interface)",
        link: "/operating/TUI",
      },
      {
        title: "GUI",
        link: "/operating/GUI",
      },
      {
        title: "Configuration",
        link: "/operating/configuration",
      },
      {
        title: "Error Handling",
        link: "/operating/errors",
      },
      {
        title: "Events API",
        link: "/operating/events_api",
      },
      {
        title: "Metrics",
        link: "/operating/metrics",
      },
      {
        title: "DeltaFile Analytics and Survey",
        link: "/operating/deltafile_analytics",
      },
    ],
  },
  {
    title: "Advanced Topics",
    children: [
      {
        title: "Architecture",
        link: "/advanced/architecture",
      },
      {
        title: "Automatic Resume",
        link: "/advanced/auto_resume",
      },
      {
        title: "Advanced Routing",
        link: "/advanced/advanced_routing",
      },
      {
        title: "Data Retention",
        link: "/advanced/data_retention",
      },
      {
        title: "DeltaFile Annotations",
        link: "/advanced/deltafile_annotations",
      },
      {
        title: "Multithreading Java Actions",
        link: "/advanced/multithreading_java_action_kit",
      },
    ],
  },
  {
    title: "KinD Cluster for Demo, Dev, and Test",
    link: "/kind",
  },
  {
    title: "Contributing to Core Development",
    link: "/contributing",
  },
  {
    title: "DeltaFi Changelog",
    link: "/CHANGELOG",
  },
];

const searchBar = (entries) => {
  return {
    name: "searchBar",
    extend(api) {
      api.enableSearch({
        handler: (keyword) => {
          if (!keyword) return [];

          return entries
            .filter((value) => value.title.toLowerCase().includes(keyword.toLowerCase()) || value.body.toLowerCase().includes(keyword.toLowerCase()))
            .map((value) => {
              let snippet = "";
              const lowerBody = value.body.toLowerCase();
              const lowerKeyword = keyword.toLowerCase();
              const index = lowerBody.indexOf(lowerKeyword);

              // Want to highlight the match but also show some context
              if (index !== -1) {
                const start = Math.max(0, index - 40); // 40 chars before
                const end = Math.min(value.body.length, index + keyword.length + 40); // 40 chars after
                snippet = value.body.slice(start, end);

                // highlight match
                snippet = snippet.replace(new RegExp(`(${keyword})`, "ig"), "<mark>$1</mark>");

                if (start > 0) snippet = "…" + snippet;
                if (end < value.body.length) snippet = snippet + "…";
              }

              return {
                title: value.title,
                link: value.link,
                description: snippet || value.body.slice(0, 120) + "…", // fallback snippet
              };
            });
        },
      });
    },
  };
};

(async () => {
  const searchIndex = await buildSearchIndex(sidebar);
  const config = {
    target: "#docs",
    title: "",
    highlight: ["java", "yaml"],
    sourcePath: "docs",
    detectSystemDarkTheme: true,
    darkThemeToggler: true,
    headerBackground: "#2f3136",
    headerTextColor: "#ffffffe0",
    headerHeight: "66px",
    cssVariables(theme) {
      return theme === "dark" ? { headerHeight: "66px", accentColor: "rgb(12,123,192)", logo: "url('./logo-dark.png')" } : { headerHeight: "66px", accentColor: "rgb(12,123,192)", logo: "url('./logo.png')" };
    },

    plugins: [searchBar(searchIndex)],
    nav: [
      {
        title: "Home",
        link: "/",
      },
      {
        title: "GitLab",
        link: "https://gitlab.com/deltafi/deltafi",
      },
      {
        title: "DeltaFi.org",
        link: "https://deltafi.org",
      },
    ],
    sidebar: sidebar,
  };

  if (process.env.VUE_APP_SHOW_VERSIONS === "true") {
    config.versions = {};
    config.versions[`v${process.env.VUE_APP_VERSION} (Latest)`] = { link: "/" };
    config.versions["v1.2.20"] = { link: "https://v1.docs.deltafi.org" };
  }

  new Docute(config);
})();

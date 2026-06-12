/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import DOMPurify from "dompurify";

// Shared sanitizer for plugin-authored action documentation.
//
// The generated docs intentionally contain HTML: core's DocumentationGenerator emits
// <font>/<br/>, and action authors may write inline HTML in description/details/notes.
// We therefore render markdown with html:true so it displays properly. But that HTML
// is rendered in the operator's (often admin) browser session, which is a different and
// more privileged trust boundary than the sandboxed container the plugin's code runs in.
// Raw html:true would let a malicious or careless plugin land stored XSS in that session.
//
// DOMPurify's default policy keeps the formatting tags we want (font, br, tables, links,
// code, lists, etc.) while stripping <script>, on* event handlers, javascript:/data: URLs,
// and frame/object/embed injection. That gives us rich rendering without the XSS vector.
export function sanitizeDocsHtml(html) {
  return DOMPurify.sanitize(html);
}

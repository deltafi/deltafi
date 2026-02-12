// ABOUTME: Parsing and serialization utilities for escaped comma-separated collection values.
// ABOUTME: Used by LIST, SET, and MAP property types throughout the UI.

// Split a comma-separated string respecting \, escapes
export const splitEscaped = (value) => {
  const items = [];
  let current = "";
  let escaped = false;
  for (const ch of value) {
    if (!escaped && ch === "\\") {
      escaped = true;
    } else if (!escaped && ch === ",") {
      items.push(current.trim());
      current = "";
    } else {
      if (escaped && ch !== ",") {
        current += "\\";
      }
      current += ch;
      escaped = false;
    }
  }
  items.push(current.trim());
  return items.filter((i) => i.length > 0);
};

// Escape commas in a single item for safe serialization
export const escapeCommas = (item) => item.replace(/,/g, "\\,");

export const COLLECTION_TYPES = ['MAP', 'LIST', 'SET'];

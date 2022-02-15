import hljs from "highlight.js/lib/common";

export const highlightCode = async (code: string, language: string) => {
  const workerStart = Date.now();
  console.debug("Highlight worker started");

  const options = { language: language, ignoreIllegals: true };
  const result = (language && hljs.getLanguage(language))
    ? hljs.highlight(code, options)
    : hljs.highlightAuto(code);

  console.debug("Highlight worker completed in", Date.now() - workerStart, "milliseconds");

  return {
    code: result.value,
    language: result.language
  };
};

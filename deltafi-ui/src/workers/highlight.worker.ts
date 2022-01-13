import hljs from "highlight.js/lib/common";

export const highlightCode = async (code: string, language: string) => {
  const workerStart = Date.now();
  let result;
  if (hljs.getLanguage(language)) {
    result = hljs.highlight(code, {
      language: language,
      ignoreIllegals: true,
    });
  } else {
    result = hljs.highlightAuto(code);
  }
  console.debug("Worker completed in", Date.now() - workerStart, "milliseconds")
  return {
    code: result.value,
    language: result.language
  }
}

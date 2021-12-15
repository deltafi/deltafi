export class UtilFunctions {
  formatContextData(contextData: string) {
    let formattedString = contextData;
    // Javascript does not provide the PCRE recursive parameter (?R) which would allow for the matching against nested JSON using regex: /\{(?:[^{}]|(?R))*\}/g. In order to
    // capture nested JSON we have to have this long regex.
    const jsonIdentifierRegEx = /\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(""))*\}))*\}))*\}))*\}))*\}))*\}))*\}))*\}/g

    formattedString = formattedString.replace(jsonIdentifierRegEx, match => parseMatch(match));
    
    function parseMatch(match: string) {
        try {
          JSON.parse(match);
        } catch (e) {
          return match;
        }
        return JSON.stringify(JSON.parse(match), null, 2);
    }

    return formattedString;
  }
}
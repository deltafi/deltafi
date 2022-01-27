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

  duration(milliseconds: number, precision: number = 1) {
    const seconds = (milliseconds / 1000);
    const minutes = (milliseconds / (1000 * 60));
    const hours = (milliseconds / (1000 * 60 * 60));
    const days = (milliseconds / (1000 * 60 * 60 * 24));
    if (seconds < 1 ) {
      return milliseconds + "ms";
    } else if (seconds < 60) {
      return seconds.toFixed(precision) + "s";
    } else if (minutes < 60) {
      return minutes.toFixed(precision) + "m";
    } else if (hours < 24) {
      return hours.toFixed(precision) + "h";
    } else {
      return days.toFixed(precision) + "d";
    }
  }

  pluralize(count: number, singular:string, plural?:string) {
    if (count === 1) return `${count} ${singular}`;
    if (plural) return `${count} ${plural}`;
    return `${count} ${singular}s`;
  }
}
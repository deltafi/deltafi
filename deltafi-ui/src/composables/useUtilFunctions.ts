import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import filesize from "filesize";
import useUiConfig from "./useUiConfig";

const { uiConfig } = useUiConfig();
dayjs.extend(utc)

export default function useUtilFunctions(): {
  formatTimestamp: (date: any, format: string) => String | undefined;
  shortTimezone: () => String | undefined;
  convertLocalDateToUTC: (date: Date) => Date;
  formatContextData: (contextData: string) => string;
  formattedBytes: (bytes: number) => string;
  duration: (milliseconds: number, precision: number) => string;
  pluralize: (count: number, singular: string, plural?: string) => string;
  sentenceCaseString: (stringValue: string) => string;
} {
  const formatContextData = (contextData: string) => {
    let formattedString = contextData;
    // Javascript does not provide the PCRE recursive parameter (?R) which would allow for the matching against nested JSON using regex: /\{(?:[^{}]|(?R))*\}/g. In order to
    // capture nested JSON we have to have this long regex.
    const jsonIdentifierRegEx = /\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(""))*\}))*\}))*\}))*\}))*\}))*\}))*\}))*\}/g

    formattedString = formattedString.replace(jsonIdentifierRegEx, match => parseMatch(match));

    const parseMatch = (match: string) => {
      try {
        JSON.parse(match);
      } catch (e) {
        return match;
      }
      return JSON.stringify(JSON.parse(match), null, 2);
    }

    return formattedString;
  }

  const duration = (milliseconds: number, precision: number = 1) => {
    const seconds = (milliseconds / 1000);
    const minutes = (milliseconds / (1000 * 60));
    const hours = (milliseconds / (1000 * 60 * 60));
    const days = (milliseconds / (1000 * 60 * 60 * 24));
    if (seconds < 1) {
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

  const pluralize = (count: number, singular: string, plural?: string) => {
    if (count === 1) return `${count} ${singular}`;
    if (plural) return `${count} ${plural}`;
    return `${count} ${singular}s`;
  }

  const formattedBytes = (bytes: number) => {
    return filesize(bytes || 0, { base: 10 });
  };

  const convertLocalDateToUTC = (date: Date) => {
    return new Date(date.getTime() + (-(date.getTimezoneOffset() * 60000)));
  }

  const shortTimezone = () => {
    return uiConfig.useUTC ? 'UTC' : new Date().toLocaleString('en', { timeZoneName: 'short' }).split(' ').pop();
  }

  const formatTimestamp = (date: any, format: string) => {
    return uiConfig.useUTC ? dayjs(date).utc().format(format) : dayjs(date).format(format);
  }

  const sentenceCaseString = (stringValue: string) => {
    // Metrics names should always be snake case
    const words = stringValue.split("_").map((word) => {
      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
    });
    return words.join(" ");
  };

  return {
    formatTimestamp,
    shortTimezone,
    convertLocalDateToUTC,
    formatContextData,
    formattedBytes,
    duration,
    pluralize,
    sentenceCaseString
  };
}
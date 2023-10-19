import { differenceInCalendarDays, format, intervalToDuration } from "date-fns";

type DurationKey =
  | "years"
  | "months"
  | "weeks"
  | "days"
  | "hours"
  | "minutes"
  | "seconds";

export class TimeframeHelper {
  /**
   * Returns number with ordinal number suffix.
   * @param number The number to add suffix.
   *
   * @example nthNumber(1) returns 1st, nthNumber(2) returns 2nd
   */
  static getOrdinalNumberSuffix = (number: number): string => {
    if (number > 3 && number < 21) return "th";

    switch (number % 10) {
      case 1:
        return "st";
      case 2:
        return "nd";
      case 3:
        return "rd";
      default:
        return "th";
    }
  };

  static getTimezoneOptions = (): FormMenuOption[] => {
    return Intl.supportedValuesOf("timeZone").map((timezone) => ({
      label: timezone,
      value: timezone,
    }));
  };

  static getTimeframeUnitOptions = (): FormMenuOption[] => {
    return [
      {
        value: TimeframeUnitConstant.MS,
        label: "ms",
      },
      {
        value: TimeframeUnitConstant.SEC,
        label: "sec",
      },
      {
        value: TimeframeUnitConstant.MIN,
        label: "min",
      },
      {
        value: TimeframeUnitConstant.HR,
        label: "hr",
      },
    ];
  };

  /**
   * Formats the date time by the given format
   *
   * @param dateTime The date time
   * @param timeFormat The format
   * @returns The formatted time text
   */
  static toSpecificFormat(dateTime: Date, timeFormat: string): string {
    return format(dateTime, timeFormat);
  }
  /**
   * Calculates elapsed time based on passed time intervals.
   *
   * @param `start` - Date object, contains date when time interval started.
   * @param `end` - Date object, contains date when time interval ended.
   *
   * @remark `duration` - API - https://date-fns.org/v2.29.1/docs/intervalToDuration
   * @remark `days` - API - https://date-fns.org/v2.29.1/docs/differenceInCalendarDays
   */
  static elapsedTime(start: Date, end: Date): string {
    const duration = intervalToDuration({ start, end });
    const days = differenceInCalendarDays(end, start);

    return Object.keys(duration).reduce<string>((acc, cur) => {
      const validTimePeriods: DurationKey[] = ["days", "hours", "minutes", "seconds"];
      const durationKey = cur as DurationKey;
      if (validTimePeriods.includes(durationKey) && duration[durationKey]) {
        return durationKey === "days"
          ? (acc += `${days}${durationKey[0]} `) // output: "2d "
          : (acc += `${duration[durationKey]}${durationKey[0]} `); // output: "1d 4h 3m 2s"
      }

      return acc;
    }, "");
  }
  /**
   * Converts the milliseconds to hh:mm:ss format
   *
   * @param milliSeconds milliseconds
   * @returns the milliseconds in hh:mm:ss format
   * @example
   *
   */
  static milliSecondsInHHMMSSFormat(milliSeconds: number): string {
    // Returns if the seconds is not a number
    if (isNaN(milliSeconds)) return "";

    let diff = Math.round(milliSeconds / 1000);
    // The value of seconds.
    let s = diff % 60;
    diff = (diff - s) / 60;
    // The value of minutes.
    let m = diff % 60;
    diff = (diff - m) / 60;
    // The value of hours.
    let h = diff;

    // Seconds with 2 digits format.
    let ss = s <= 9 && s >= 0 ? `0${s}` : s;

    // Minutes with 2 digits format.
    let mm = m <= 9 && m >= 0 ? `0${m}` : m;

    // Hours with 2 digits format.
    let hh = h <= 9 && h >= 0 ? `0${h}` : h;

    return hh + ":" + mm + ":" + ss;
  }

  /**
   * Converts the timeframe to the formatted timeframe.
   *
   * @param timeframeUnitInSeconds The timeframe in seconds.
   * @returns The formatted timeframe.
   * @see FormattedTimeframe
   */
  static toFormattedTimeframe = (
    timeframeUnitInSeconds: number
  ): FormattedTimeframe => {
    if (timeframeUnitInSeconds === null || timeframeUnitInSeconds === undefined)
      return {
        value: null,
        unit: TimeframeUnitConstant.MS,
      };

    let formattedTimeframeUnit: TimeframeUnit = TimeframeUnitConstant.MS;
    let formattedTimeframe = timeframeUnitInSeconds * 1000;

    if (formattedTimeframe % 3600000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.HR;
      formattedTimeframe = formattedTimeframe / 3600000;
    } else if (formattedTimeframe % 60000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.MIN;
      formattedTimeframe = formattedTimeframe / 60000;
    } else if (formattedTimeframe % 1000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.SEC;
      formattedTimeframe = formattedTimeframe / 1000;
    } else {
      formattedTimeframeUnit = TimeframeUnitConstant.MS;
      formattedTimeframe = formattedTimeframe;
    }

    return {
      unit: formattedTimeframeUnit,
      value: formattedTimeframe,
    };
  };

  /**
   * Converts the timeframe to be in milliseconds.
   *
   * @param timeframe The value of the timeframe.
   * @param unit The unit of the timeframe.
   * @returns The timeframe in milliseconds.
   */
  static toMilliseconds = (timeframe: number, unit: TimeframeUnit): number => {
    const unitMeasurementMap = {
      [TimeframeUnitConstant.MS]: 1,
      [TimeframeUnitConstant.SEC]: 1000,
      [TimeframeUnitConstant.MIN]: 60000,
      [TimeframeUnitConstant.HR]: 3600000,
    };
    return timeframe * unitMeasurementMap[unit];
  };
}

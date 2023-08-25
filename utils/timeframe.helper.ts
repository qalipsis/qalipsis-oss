import { differenceInCalendarDays, format, intervalToDuration } from "date-fns";

export class TimeframeHelper {

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

        return Object.keys(duration).reduce((acc, cur) => {
            const validTimePeriods = ['days', 'hours', 'minutes', 'seconds'];

            if (validTimePeriods.includes(cur) && duration[cur]) {
                return cur === 'days'
                    ? acc += `${days}${cur[0]} `
                    : acc += `${duration[cur]}${cur[0]} `;
            }

            return acc
        }, '');
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
        if (isNaN(milliSeconds)) return '';

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

        return hh + ':' + mm + ':' + ss;
    }

    /**
     * Converts the timeframe to the formatted timeframe.
     * 
     * @param timeframeUnitInSeconds The timeframe in seconds.
     * @returns The formatted timeframe.
     * @see FormattedTimeframe
     */
    static toFormattedTimeframe = (timeframeUnitInSeconds: number): FormattedTimeframe => {
        if (timeframeUnitInSeconds === null || timeframeUnitInSeconds === undefined) return {
            value: null,
            unit: TimeframeUnit.MS
        };

        let formattedTimeframeUnit = TimeframeUnit.MS;
        let formattedTimeframe = timeframeUnitInSeconds * 1000;

        if (formattedTimeframe % 3600000 === 0) {
            formattedTimeframeUnit = TimeframeUnit.HR
            formattedTimeframe = formattedTimeframe / 3600000
        } else if (formattedTimeframe % 60000 === 0) {
            formattedTimeframeUnit = TimeframeUnit.MIN
            formattedTimeframe = formattedTimeframe / 60000
        } else if (formattedTimeframe % 1000 === 0) {
            formattedTimeframeUnit = TimeframeUnit.SEC
            formattedTimeframe = formattedTimeframe / 1000
        } else {
            formattedTimeframeUnit = TimeframeUnit.MS;
            formattedTimeframe = formattedTimeframe;
        }

        return {
            unit: formattedTimeframeUnit,
            value: formattedTimeframe
        }
    }

    /**
     * Converts the timeframe to be in milliseconds.
     * 
     * @param timeframe The value of the timeframe.
     * @param unit The unit of the timeframe.
     * @returns The timeframe in milliseconds.
     */
    static toMilliseconds = (timeframe: number, unit: TimeframeUnit): number => {
        const unitMeasurementMap = {
            [TimeframeUnit.MS]: 1,
            [TimeframeUnit.SEC]: 1000,
            [TimeframeUnit.MIN]: 60000,
            [TimeframeUnit.HR]: 3600000
        }
        return timeframe * unitMeasurementMap[unit]
    }
}
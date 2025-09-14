import { differenceInCalendarDays, format, intervalToDuration } from 'date-fns'
import { Duration } from 'luxon'

type DurationKey = 'years' | 'months' | 'weeks' | 'days' | 'hours' | 'minutes' | 'seconds'

export class TimeframeHelper {
  /**
   * Returns number with ordinal number suffix.
   * @param number The number to add suffix.
   *
   * @example nthNumber(1) returns 1st, nthNumber(2) returns 2nd
   */
  static getOrdinalNumberSuffix = (number: number): string => {
    if (number > 3 && number < 21) return 'th'

    switch (number % 10) {
      case 1:
        return 'st'
      case 2:
        return 'nd'
      case 3:
        return 'rd'
      default:
        return 'th'
    }
  }

  static toIsoStringDuration = (value: number, unit: TimeframeUnit): string => {
    switch (unit) {
      case 'MS':
        return Duration.fromObject({ milliseconds: value }).toISO()
      case 'SEC':
        return Duration.fromObject({ seconds: value }).toISO()
      case 'MIN':
        return Duration.fromObject({ minutes: value }).toISO()
      case 'HR':
        return Duration.fromObject({ hours: value }).toISO()
    }
  }

  static getTimezoneOptions = (): FormMenuOption[] => {
    return Intl.supportedValuesOf('timeZone').map((timezone) => ({
      label: timezone,
      value: timezone,
    }))
  }

  static getTimeframeUnitOptions = (): FormMenuOption[] => {
    return [
      {
        value: TimeframeUnitConstant.MS,
        label: 'ms',
      },
      {
        value: TimeframeUnitConstant.SEC,
        label: 'sec',
      },
      {
        value: TimeframeUnitConstant.MIN,
        label: 'min',
      },
      {
        value: TimeframeUnitConstant.HR,
        label: 'hr',
      },
    ]
  }

  /**
   * Formats the date time by the given format
   *
   * @param dateTime The date time
   * @param timeFormat The format
   * @returns The formatted time text
   */
  static toSpecificFormat(dateTime: Date, timeFormat: string): string {
    return format(dateTime, timeFormat)
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
    const duration = intervalToDuration({ start, end })
    const days = differenceInCalendarDays(end, start)

    return Object.keys(duration).reduce<string>((acc, cur) => {
      const validTimePeriods: DurationKey[] = ['days', 'hours', 'minutes', 'seconds']
      const durationKey = cur as DurationKey
      if (validTimePeriods.includes(durationKey) && duration[durationKey]) {
        return durationKey === 'days'
          ? (acc += `${days}${durationKey[0]} `) // output: "2d "
          : (acc += `${duration[durationKey]}${durationKey[0]} `) // output: "1d 4h 3m 2s"
      }

      return acc
    }, '')
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
    if (isNaN(milliSeconds)) return ''

    let diff = Math.round(milliSeconds / 1000)
    // The value of seconds.
    let s = diff % 60
    diff = (diff - s) / 60
    // The value of minutes.
    let m = diff % 60
    diff = (diff - m) / 60
    // The value of hours.
    let h = diff

    // Seconds with 2 digits format.
    let ss = s <= 9 && s >= 0 ? `0${s}` : s

    // Minutes with 2 digits format.
    let mm = m <= 9 && m >= 0 ? `0${m}` : m

    // Hours with 2 digits format.
    let hh = h <= 9 && h >= 0 ? `0${h}` : h

    return hh + ':' + mm + ':' + ss
  }

  /**
   * Converts the timeframe to the formatted timeframe.
   *
   * @param timeframeInIsoStringFormat The timeframe in ISO string format.
   * @returns The formatted timeframe.
   * @see FormattedTimeframe
   */
  static toFormattedTimeframe = (timeframeInIsoStringFormat: string | undefined): FormattedTimeframe => {
    if (!timeframeInIsoStringFormat)
      return {
        value: null,
        unit: TimeframeUnitConstant.MS,
      }

    let formattedTimeframeUnit: TimeframeUnit = TimeframeUnitConstant.MS
    let formattedTimeframeValue = Duration.fromISO(timeframeInIsoStringFormat).toMillis()

    if (formattedTimeframeValue % 3_600_000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.HR
      formattedTimeframeValue = formattedTimeframeValue / 3600000
    } else if (formattedTimeframeValue % 60_000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.MIN
      formattedTimeframeValue = formattedTimeframeValue / 60000
    } else if (formattedTimeframeValue % 1_000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.SEC
      formattedTimeframeValue = formattedTimeframeValue / 1000
    } else {
      formattedTimeframeUnit = TimeframeUnitConstant.MS
      formattedTimeframeValue = formattedTimeframeValue
    }

    return {
      unit: formattedTimeframeUnit,
      value: formattedTimeframeValue,
    }
  }

  /**
   * Converts the timeframe to be in milliseconds.
   *
   * @param timeframe The value of the timeframe.
   * @param unit The unit of the timeframe.
   * @returns The timeframe in milliseconds.
   */
  static isoStringToMilliseconds = (timeframeInIsoStringFormat: string | undefined): number => {
    if (!timeframeInIsoStringFormat) return 0

    return Duration.fromISO(timeframeInIsoStringFormat).toMillis()
  }
}

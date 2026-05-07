import { differenceInCalendarDays, format, intervalToDuration } from 'date-fns'
import { Duration } from 'luxon'

type DurationKey = 'years' | 'months' | 'weeks' | 'days' | 'hours' | 'minutes' | 'seconds'

const UNIT_MAP = { MS: 1, SEC: 1_000, MIN: 60_000, HR: 3_600_000 } as const

export const TimeframeHelper = {
  /**
   * Returns number with ordinal number suffix.
   *
   * @param number The number to add suffix.
   *
   * @example nthNumber(1) returns 1st, nthNumber(2) returns 2nd
   */
  getOrdinalNumberSuffix(number: number): string {
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
  },

  toIsoStringDuration(value: number, unit: TimeframeUnit): string {
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
  },

  toMs(value: number, unit: TimeframeUnit): number {
    return value * UNIT_MAP[unit]
  },

  fromMs(ms: number, unit: TimeframeUnit): number {
    return ms / UNIT_MAP[unit]
  },

  getTimezoneOptions(): FormMenuOption[] {
    return Intl.supportedValuesOf('timeZone').map((timezone) => ({
      label: timezone,
      value: timezone,
    }))
  },

  getTimeframeUnitOptions(): FormMenuOption[] {
    return [
      { value: TimeframeUnitConstant.MS, label: 'ms' },
      { value: TimeframeUnitConstant.SEC, label: 'sec' },
      { value: TimeframeUnitConstant.MIN, label: 'min' },
      { value: TimeframeUnitConstant.HR, label: 'hr' },
    ]
  },

  /**
   * Formats the date time by the given format
   *
   * @param dateTime The date time
   * @param timeFormat The format
   * @returns The formatted time text
   */
  toSpecificFormat(dateTime: Date, timeFormat: string): string {
    return format(dateTime, timeFormat)
  },

  /**
   * Calculates elapsed time based on passed time intervals.
   *
   * @param `start` - Date object, contains date when time interval started.
   * @param `end` - Date object, contains date when time interval ended.
   *
   * @remark `duration` - API - https://date-fns.org/v2.29.1/docs/intervalToDuration
   * @remark `days` - API - https://date-fns.org/v2.29.1/docs/differenceInCalendarDays
   */
  elapsedTime(start: Date, end: Date): string {
    const duration = intervalToDuration({ start, end })
    const days = differenceInCalendarDays(end, start)

    return Object.keys(duration).reduce<string>((acc, cur) => {
      const validTimePeriods: DurationKey[] = ['days', 'hours', 'minutes', 'seconds']
      const durationKey = cur as DurationKey
      if (validTimePeriods.includes(durationKey) && duration[durationKey]) {
        return durationKey === 'days'
          ? (acc += `${days}${durationKey[0]} `)
          : (acc += `${duration[durationKey]}${durationKey[0]} `)
      }

      return acc
    }, '')
  },

  /**
   * Converts the milliseconds to hh:mm:ss format.
   *
   * @param milliSeconds milliseconds.
   * @returns the milliseconds in hh:mm:ss format.
   * @example 36,000 ms = 00:00:36.
   */
  milliSecondsInHHMMSSFormat(milliSeconds: number): string {
    if (isNaN(milliSeconds)) return ''

    let diff = Math.round(milliSeconds / 1000)
    const s = diff % 60
    diff = (diff - s) / 60
    const m = diff % 60
    const h = (diff - m) / 60

    return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':')
  },

  /**
   * Converts the timeframe to the formatted timeframe.
   *
   * @param timeframeInIsoStringFormat The timeframe in ISO string format.
   * @returns The formatted timeframe.
   * @see FormattedTimeframe
   */
  toFormattedTimeframe(timeframeInIsoStringFormat: string | undefined): FormattedTimeframe {
    if (!timeframeInIsoStringFormat)
      return {
        value: null,
        unit: TimeframeUnitConstant.MS,
      }

    let formattedTimeframeUnit: TimeframeUnit = TimeframeUnitConstant.MS
    let formattedTimeframeValue = Duration.fromISO(timeframeInIsoStringFormat).toMillis()

    if (formattedTimeframeValue % 3_600_000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.HR
      formattedTimeframeValue = formattedTimeframeValue / 3_600_000
    } else if (formattedTimeframeValue % 60_000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.MIN
      formattedTimeframeValue = formattedTimeframeValue / 60_000
    } else if (formattedTimeframeValue % 1_000 === 0) {
      formattedTimeframeUnit = TimeframeUnitConstant.SEC
      formattedTimeframeValue = formattedTimeframeValue / 1_000
    }

    return {
      unit: formattedTimeframeUnit,
      value: formattedTimeframeValue,
    }
  },

  /**
   * Converts a millisecond value directly to a FormattedTimeframe with its largest exact unit.
   */
  msToFormattedTimeframe(ms: number): FormattedTimeframe {
    return TimeframeHelper.toFormattedTimeframe(TimeframeHelper.toIsoStringDuration(ms, 'MS'))
  },

  /**
   * Converts the timeframe to the target unit.
   *
   * @param timeframeInIsoStringFormat The value of the timeframe in ISO format.
   * @param unit The unit of the timeframe.
   * @returns The timeframe in the target unit (default: milliseconds).
   */
  isoStringToTargetTimeframeUnit(
    timeframeInIsoStringFormat: string | undefined,
    unit: TimeframeUnit = 'MS',
  ): number {
    if (!timeframeInIsoStringFormat) return 0

    let valueInMilliseconds: number
    try {
      valueInMilliseconds = Duration.fromISO(timeframeInIsoStringFormat).toMillis()
    } catch {
      return 0
    }

    if (!(unit in UNIT_MAP)) {
      throw new Error(`Unsupported timeframe unit: ${unit}`)
    }

    return valueInMilliseconds / UNIT_MAP[unit]
  },
}

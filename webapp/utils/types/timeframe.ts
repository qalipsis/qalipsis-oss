/**
 * The formatted timeframe value and unit
 */
export interface FormattedTimeframe {
    value: number | null,
    unit: TimeframeUnit
}

/**
 * The constants for the timeframe units.
 */
export const TimeframeUnitConstant = {
    // Millisecond
    MS: 'MS',
    // Second
    SEC: 'SEC',
    // Minute
    MIN: 'MIN',
    // Hour
    HR: 'HR'
} as const;
export type TimeframeUnit = typeof TimeframeUnitConstant[keyof typeof TimeframeUnitConstant];

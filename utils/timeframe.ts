/**
 * The formatted timeframe value and unit
 */
export interface FormattedTimeframe {
    value: number,
    unit: TimeframeUnit
}

/**
 * The enum for the timeframe units.
 */
export enum TimeframeUnit {
    // Millisecond
    MS = 'MS',
    // Second
    SEC = 'SEC',
    // Minute
    MIN = 'MIN',
    // Hour
    HR = 'HR'
}

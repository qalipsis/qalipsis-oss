const elapsedTimeWidthClass = {
  sm: 'w-40',
  md: 'w-56',
  lg: 'w-72',
}

export const ScenarioDetailsHelper = {
  toDisplayNumber(value: number): string {
    const thresholds = [
      { limit: 1_000_000_000_000, suffix: 't' },
      { limit: 1_000_000_000, suffix: 'b' },
      { limit: 1_000_000, suffix: 'm' },
      { limit: 1_000, suffix: 'k' },
    ]

    for (const { limit, suffix } of thresholds) {
      if (Math.abs(value) >= limit) {
        const abbreviated = value / limit
        const formatted = abbreviated % 1 === 0 ? abbreviated.toString() : abbreviated.toFixed(2).replace(/\.?0+$/, '')

        return `${formatted}${suffix}`
      }
    }

    return value.toString()
  },

  getElapsedTimeSectionWidthClass(scenarioReports: ScenarioReport[]): string {
    if (!scenarioReports.length) return elapsedTimeWidthClass.sm // guard empty

    const hasAnyReportStartEndDateDifferent = scenarioReports.some((report) => {
      if (!report.start || !report.end) return false
      return new Date(report.start).toDateString() !== new Date(report.end).toDateString()
    })

    if (hasAnyReportStartEndDateDifferent) return elapsedTimeWidthClass.lg

    const hasNoEndTimestampFromAllReports = scenarioReports.every((report) => !report.end) // simplified

    if (hasNoEndTimestampFromAllReports) return elapsedTimeWidthClass.sm

    return elapsedTimeWidthClass.md
  },
}

/**
 * Styles of lines to assign to the different campaigns
 * in a single report, to differentiate them.
 */
export const LINE_STYLES: Array<{ svgDash: string; chartDash: number }> = [
    {svgDash: 'none', chartDash: 0},  // solid
    {svgDash: '12 4', chartDash: 12},  // long dash
    {svgDash: '8 4', chartDash: 8},  // medium dash
    {svgDash: '4 4', chartDash: 4},  // short dash
    {svgDash: '1.5 4', chartDash: 2},  // dot
    {svgDash: '12 4 2 4', chartDash: 9},  // long dash-dot
    {svgDash: '8 4 2 4', chartDash: 6},  // medium dash-dot
    {svgDash: '8 4 2 4 2 4', chartDash: 7},  // medium dash-dot-dot
    {svgDash: '12 3 5 3', chartDash: 10},  // long-short alternating
    {svgDash: '4 3 2 3', chartDash: 5},  // short dash-dot
    {svgDash: '4 3 2 3 2 3', chartDash: 5},  // short dash-dot-dot
    {svgDash: '1.5 2', chartDash: 1},  // dense dot
]

export const ReportDetailsConfig = {
    TABLE_COLUMNS: [
        {
            title: "Series name",
            key: "seriesName",
        },
        {
            title: "Campaign name",
            key: "campaignName",
        },
        {
            title: "Start time",
            key: "startTimeText",
        },
        {
            title: "Elapsed",
            key: "elapsedText",
        },
        {
            title: "Value",
            key: "valueDisplayText",
        },
    ] as TableColumnConfig[],
}

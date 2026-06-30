export interface CampaignTableData extends Campaign {
  /**
   * The elapsed time of the campaign.
   */
  elapsedTime: string

  /**
   * Creation time of the campaign in formatted text.
   */
  creationTime: string

  /**
   * The start time of the campaign in formatted text.
   * If the campaign is not yet started. "Not started yet" text is displayed.
   */
  startTime: string

  /**
   * The text of all scenario names.
   */
  scenarioText: string

  /**
   * The tag of the status.
   */
  statusTag: Tag | null

  /**
   * A flag to indicate if the row can be selected.
   */
  disabled?: boolean
}

/**
 * Configuration of the rendering of a campaign in detailed view,
 * including the style of the related lines in the charts.
 */
export interface CampaignOption extends CampaignExecutionDetails {
  strokeDashArray: number
    svgDashPattern: string
    strokeWidth: number
  isActive: boolean
  enrichedScenarioReports: ScenarioReport[]
}

export const TimeoutTypeConstant = {
  SOFT: 'soft',
  HARD: 'hard',
  NONE: 'none',
} as const

export type TimeoutType = (typeof TimeoutTypeConstant)[keyof typeof TimeoutTypeConstant]


export interface CampaignConfigurationForm {
  timeoutType: TimeoutType
  durationValue: string
  durationUnit: TimeframeUnit
  scheduled: boolean
  repeatEnabled: boolean
  repeatTimeRange: TimeRange
  repeatValues: string[]
  relativeRepeatValues: string[]
  timezone: string
  scheduledTime: Date | null
}

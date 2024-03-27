import { TimeoutTypeConstant } from '../types/campaign';

export class CampaignDetailsConfig {
  static CAMPAIGN_TIMEOUT_OPTIONS = [
    {
      label: "Soft Timeout",
      value: TimeoutTypeConstant.SOFT,
    },
    {
      label: "Hard Timeout",
      value: TimeoutTypeConstant.HARD,
    },
  ];

  static REPEAT_TIME_RANGE_OPTIONS = [
    {
      label: "Day",
      value: "HOURLY",
    },
    {
      label: "Week",
      value: "DAILY",
    },
    {
      label: "Month",
      value: "MONTHLY",
    },
  ];

  static HOURLY_REPEAT_OPTIONS = Array.from(
    { length: 24 },
    (_, i) => i + 1
  ).map((num) => ({
    label: num.toString(),
    value: num.toString(),
  }));

  static DAILY_REPEAT_OPTIONS = Object.entries({
    Mo: 0,
    Tu: 1,
    We: 2,
    Th: 3,
    Fr: 4,
    Sa: 5,
    Su: 6,
  }).map(([key, value]) => ({
    label: key,
    value: value.toString(),
  }));
  
  static MONTHLY_REPEAT_OPTIONS = Array.from(
    { length: 31 },
    (_, i) => i + 1
  ).map((num) => ({
    label: num.toString(),
    value: num.toString(),
  }));
  
  static RELATIVE_DAY_OF_MONTH_OPTIONS = Array.from({ length: 7 }, (_, i) => i - 7).map((num) => ({
    label: num.toString(),
    value: num.toString(),
  }));
  
}

export const useConfigurationApi = () => {
  const { get$ } = baseApi()

  /**
   * Fetches the configuration of the campaign
   *
   * @returns the configuration of the campaign
   */
  const fetchDefaultCampaignConfiguration = (): Promise<DefaultCampaignConfiguration> => {
    return get$<DefaultCampaignConfiguration>('/configuration/campaign')
  }

  return {
    fetchDefaultCampaignConfiguration,
  }
}

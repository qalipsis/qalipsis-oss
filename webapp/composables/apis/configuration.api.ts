export const useConfigurationApi = () => {
  const { get$ } = baseApi()

  /**
   * Fetches the configuration of the campaign
   *
   * @returns the configuration of the campaign
   */
  const fetchDefaultCampaignConfiguration = (): Promise<DefaultCampaignConfiguration> => {
    return get$<DefaultCampaignConfiguration, unknown>('/configuration/campaign')
  }

  return {
    fetchDefaultCampaignConfiguration,
  }
}

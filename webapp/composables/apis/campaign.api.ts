export const useCampaignApi = () => {
  const { get$, post$, put$ } = baseApi()

  /**
   * Fetches the campaigns
   *
   * @param pageQueryParams The query parameters
   * @returns The page list of campaigns
   */
  const fetchCampaigns = (pageQueryParams: PageQueryParams): Promise<Page<Campaign>> => {
    return get$<Page<Campaign>, PageQueryParams>('/campaigns', pageQueryParams)
  }

  /**
   * Fetches the configuration of the campaign
   *
   * @param campaignKey The key of the campaign
   * @returns The configuration of the campaign
   */
  const fetchCampaignConfiguration = (campaignKey: string): Promise<CampaignConfiguration> => {
    return get$<CampaignConfiguration, never>(`/campaigns/${campaignKey}/configuration`)
  }

  /**
   * Creates a new campaign.
   *
   * @param params The campaign configuration
   * @returns The new campaign details
   */
  const createCampaign = (params: CampaignConfiguration): Promise<Campaign> => {
    return post$<Campaign>('/campaigns', params)
  }

  /**
   * Schedule a campaign
   *
   * @param params The campaign configuration
   * @returns The scheduled campaign details
   */
  const scheduleCampaign = (params: CampaignConfiguration): Promise<Campaign> => {
    return post$<Campaign>('/campaigns/schedule', params)
  }

  /**
   * Updates the configuration of a scheduled campaign
   *
   * @param campaignKey The key of the campaign
   * @param params The campaign configuration to be updated
   * @returns The updated campaign configuration
   */
  const updateCampaignConfig = (campaignKey: string, params: CampaignConfiguration): Promise<Campaign> => {
    return put$<Campaign, CampaignConfiguration>(`/campaigns/schedule/${campaignKey}`, params)
  }

  /**
   * Fetches a campaign
   *
   * @param campaignReference The reference of the campaign
   * @returns The campaign details
   */
  const fetchCampaignDetails = async (campaignReference: string): Promise<CampaignExecutionDetails> => {
    /**
     * Notes:
     * the function is designed to accept just a single campaign reference,
     * and the response from the endpoint is a list of campaign.
     *
     * Thus, this function returns the first item which contains the execution details for the provided campaign reference.
     */
    const campaigns = await get$<CampaignExecutionDetails[], unknown>(`/campaigns/${campaignReference}`)
    if (!campaigns.length) throw new Error(`No campaign found for reference: ${campaignReference}`)

    return campaigns[0]
  }

  /**
   * Fetches multiple campaign details
   *
   * @param campaignReferences The references of the campaign
   * @returns The list of campaign details
   */
  const fetchMultipleCampaignsDetails = (campaignReferences: string[]): Promise<CampaignExecutionDetails[]> => {
    return get$<CampaignExecutionDetails[], unknown>(`/campaigns/${campaignReferences.join(',')}`)
  }

  /**
   * Abort a campaign with the provided campaign name and details of abortion.
   *
   * @param key key of the campaign.
   * @param isForceAbort Forces the campaign to fail when set to true, defaults to false.
   */
  const abortCampaign = (key: string, isForceAbort: boolean): Promise<CampaignExecutionDetails> => {
    return post$<CampaignExecutionDetails>(`/campaigns/${key}/abort`, { hard: isForceAbort })
  }

  /**
   * Replays a campaign.
   *
   * @param key key of the campaign.
   * @returns The campaign details
   */
  const replayCampaign = (key: string): Promise<CampaignExecutionDetails> => {
    return post$<CampaignExecutionDetails>(`/campaigns/${key}/replay`)
  }

  return {
    fetchCampaigns,
    fetchCampaignConfiguration,
    createCampaign,
    updateCampaignConfig,
    scheduleCampaign,
    fetchCampaignDetails,
    fetchMultipleCampaignsDetails,
    abortCampaign,
    replayCampaign,
  }
}

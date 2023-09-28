import { DefaultCampaignConfiguration } from "utils/configuration";

export const useConfigurationApi = () => {
    const { get$ } = baseApi();

    /**
     * Fetches the configuration of the campaign
     * 
     * @returns the configuration of the campaign
     */
    const fetchCampaignConfiguration = async (): Promise<DefaultCampaignConfiguration> => {
        return get$<DefaultCampaignConfiguration, unknown>("/configuration/campaign");
    }

    return {
        fetchCampaignConfiguration
    }
}
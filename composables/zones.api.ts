import { Zone } from "utils/scenario";

export const useZonesApi = () => {
    const { get$ } = baseApi();

    /**
     * Fetches the profile of the user
     * 
     * @returns The profile of the user
     */
    const fetchZones = (): Promise<Zone[]> => {
        return get$<Zone[], unknown>("/zones");
    }

    return {
        fetchZones
    }
}
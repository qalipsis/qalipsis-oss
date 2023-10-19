export const useScenarioApi = () => {
    const { get$ } = baseApi();

    /**
     * Fetches the scenarios
     * 
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchScenarios = async (): Promise<ScenarioSummary[]> => {
        return get$<ScenarioSummary[], unknown>("/scenarios");
    }

    return {
        fetchScenarios
    }
}
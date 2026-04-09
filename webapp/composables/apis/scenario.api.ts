export const useScenarioApi = () => {
  const { get$ } = baseApi()

  /**
   * Fetches the scenarios
   *
   * @returns The list of scenarios
   */
  const fetchScenarios = (): Promise<ScenarioSummary[]> => {
    return get$<ScenarioSummary[]>('/scenarios')
  }

  return {
    fetchScenarios,
  }
}

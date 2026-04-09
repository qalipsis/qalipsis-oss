export const useZonesApi = () => {
  const { get$ } = baseApi()

  /**
   * Fetches the available zones
   *
   * @returns The available zones
   */
  const fetchZones = (): Promise<Zone[]> => {
    return get$<Zone[]>('/zones')
  }

  return {
    fetchZones,
  }
}

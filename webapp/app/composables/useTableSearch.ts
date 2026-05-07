export const useTableSearch = (setFilter: (sanitizedQuery: string) => void, fetchFn: () => Promise<void>) => {
  const toastStore = useToastStore()

  const handleSearch = async (value: string) => {
    setFilter(TableHelper.getSanitizedQuery(value))
    try {
      await fetchFn()
    } catch (error) {
      toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
    }
  }

  return { handleSearch }
}

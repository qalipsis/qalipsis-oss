export const useAsyncField = <T>(fetchFn: (...args: any[]) => Promise<T>) => {
  const toastStore = useToastStore()
  const data = ref<T | null>(null)
  const isFetched = ref(false)

  const fetch = async (...args: any[]) => {
    isFetched.value = false
    try {
      data.value = await fetchFn(...args)
      isFetched.value = true
    } catch (error) {
      toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
    }
  }

  return { data, isFetched, fetch }
}

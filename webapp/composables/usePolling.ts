export const usePolling = (
  fn: () => Promise<void>,
  shouldContinue: () => boolean,
  interval = 5000
) => {
  const toastStore = useToastStore()
  const isLoading = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  const run = async () => {
    try {
      isLoading.value = true
      await fn()
      if (shouldContinue()) {
        timer = setTimeout(run, interval)
      }
    } catch (error) {
      toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
    } finally {
      isLoading.value = false
    }
  }

  onUnmounted(() => {
    if (timer) clearTimeout(timer)
  })

  return { run, isLoading }
}

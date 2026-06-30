export const usePolling = (
  fn: () => Promise<void>,
  shouldContinue: () => boolean,
  interval = 5000,
  immediate = true
) => {
  const toastStore = useToastStore()
  const isLoading = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

    const _tick = async () => {
    try {
      isLoading.value = true
      await fn()
      if (shouldContinue()) {
          timer = setTimeout(_tick, interval)
      }
    } catch (error) {
      toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
    } finally {
      isLoading.value = false
    }
  }

    const run = () => {
        if (immediate) {
            return _tick()
        }
        if (shouldContinue()) {
            timer = setTimeout(_tick, interval)
        }
        return Promise.resolve()
    }

  onUnmounted(() => {
    if (timer) clearTimeout(timer)
  })

  return { run, isLoading }
}

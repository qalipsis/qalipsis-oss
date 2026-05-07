export const useUnsavedChanges = (hasChanges: () => boolean) => {
  const modalOpen = ref(false)
  let redirectPath = ''
  let shouldDiscard = false
  let hasSaved = false

  const handler = (e: BeforeUnloadEvent) => {
    if (hasChanges() && !shouldDiscard && !hasSaved) {
      e.preventDefault()
      e.returnValue = ''
    }
  }

  onMounted(() => window.addEventListener('beforeunload', handler))
  onUnmounted(() => window.removeEventListener('beforeunload', handler))

  onBeforeRouteLeave((to) => {
    redirectPath = to.path
    if (hasChanges() && !shouldDiscard && !hasSaved) {
      modalOpen.value = true

      return false
    }
  })

  const confirmDiscard = () => {
    shouldDiscard = true
    navigateTo(redirectPath)
  }

  const markSaved = () => {
    hasSaved = true
  }

  return { modalOpen, confirmDiscard, markSaved }
}

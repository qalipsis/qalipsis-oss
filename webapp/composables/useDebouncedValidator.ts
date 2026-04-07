export const useDebouncedValidator = (
  asyncFn: (value: string) => Promise<boolean>,
  delay = 200
) => {
  let timer: ReturnType<typeof setTimeout> | null = null

  onUnmounted(() => {
    if (timer) clearTimeout(timer)
  })

  const validate = (value: string | null): Promise<boolean> => {
    if (timer) clearTimeout(timer)

    return new Promise((resolve) => {
      timer = setTimeout(async () => {
        if (value?.trim()) {
          resolve(await asyncFn(value.trim()))
        } else {
          resolve(true)
        }
      }, delay)
    })
  }

  return { validate }
}

<template>
  <div class="flex items-center gap-x-2">
    <BaseIcon
      icon="qls-icon-time"
      class="text-xl text-gray-500 dark:text-gray-100"
    />
    <div>
      <div class="text-gray-500 dark:text-gray-100 text-sm">
        {{ displayTimeText }}
      </div>
      <div class="text-gray-400 dark:text-gray-200 text-xs">({{ intervalText }})</div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  start: string
  end?: string
}>()

const now = ref(new Date().toISOString())

const {pause, resume} = useIntervalFn(() => {
  now.value = new Date().toISOString()
}, 1000)

watch(() => props.end, (end) => {
  if (end) {
    pause()
  } else {
    resume()
  }
}, {immediate: true})

const effectiveEnd = computed(() => props.end ?? now.value)

const displayTimeText = computed(() => ScenarioHelper.toTimeDisplayText(props.start, props.end))
const intervalText = computed(() => ScenarioHelper.toIntervalInHHMMSSFormat(props.start, effectiveEnd.value))
</script>


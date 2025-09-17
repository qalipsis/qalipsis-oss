<template>
  <div class="flex items-center">
    <BaseIcon icon="qls-icon-time" class="text-xl text-gray-500 dark:text-gray-100"/>
    <span class="text-gray-500 dark:text-gray-100 pl-1">{{ displayTimeText }}</span>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  start: string,
  end?: string
}>()

/**
 * The display time text
 */
const displayTimeText = computed(() => ScenarioHelper.toTimeDisplayText(props.start, props.end));

</script>

<style scoped lang="scss">

</style>
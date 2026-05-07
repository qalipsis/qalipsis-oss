<template>
  <BaseDrawer
    :open="open"
    title="All Series"
    :footer-hidden="true"
    @close="emit('update:open', false)"
    @update:open="emit('update:open', $event)"
  >
    <SeriesPanel
      :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
      :campaignKey="campaignKey"
      @selectedDataSeriesChange="emit('selectedDataSeriesChange', $event)"
    />
  </BaseDrawer>
</template>

<script setup lang="ts">
defineProps<{
  open: boolean
  preselectedDataSeriesReferences: string[]
  campaignKey?: string
}>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'selectedDataSeriesChange', v: DataSeriesOption[]): void
}>()
</script>

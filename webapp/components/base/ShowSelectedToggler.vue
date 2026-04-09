<template>
  <div class="flex items-center">
    <div
      class="flex items-center cursor-pointer hover:text-primary-500"
      :class="[showChecked ? 'text-primary-500' : 'text-gray-500 dark:text-gray-100']"
      @click="handleClick"
    >
      <BaseIcon
        icon="qls-icon-check-fill"
        class="text-2xl"
      />
      <span class="text-sm">Show {{ numberOfSelectedItems }} selected items</span>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  numberOfSelectedItems: number
  defaultChecked?: boolean
}>()
const showChecked = ref(props.defaultChecked ?? false)
const emit = defineEmits<{
  (e: 'checkedChange', v: boolean): void
}>()

const handleClick = () => {
  showChecked.value = !showChecked.value
  emit('checkedChange', showChecked.value)
}
</script>

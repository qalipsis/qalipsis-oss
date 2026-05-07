<template>
  <div
    class="relative flex items-start gap-2.5 px-2 py-1 rounded-md cursor-pointer transition-opacity duration-150 hover:bg-gray-50 dark:hover:bg-gray-800"
    :class="isActive ? 'bg-gray-50 dark:bg-gray-800' : 'opacity-40 hover:opacity-100'"
    @click="emits('click')"
  >
    <div
      class="absolute left-0 top-1.5 bottom-1.5 w-0.5 rounded-full transition-opacity duration-150"
      :style="{ backgroundColor: color, opacity: isActive ? 1 : 0 }"
    ></div>
    <div
      class="w-2 h-2 rounded-full flex-shrink-0 mt-1"
      :style="{ backgroundColor: color }"
    ></div>
    <div class="flex flex-col gap-1.5 min-w-0 flex-1">
      <span
        class="text-xs leading-snug break-words text-gray-800 dark:text-gray-100"
        :class="{ 'font-medium': isActive }"
      >
        {{ displayName }}
      </span>
      <div class="flex flex-wrap gap-1">
        <BaseTag
          :text="dataTypeText"
          background-css-class="bg-gray-100 dark:bg-gray-700"
          text-css-class="capitalize text-gray-600 dark:text-gray-300 text-xxs"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  reference: string
  displayName: string
  dataType: DataType
  isActive: boolean
  color?: string
}>()
const emits = defineEmits<{
  (e: 'click'): void
}>()

const color = computed(() => props.color ?? ColorsConfig.PRIMARY_COLOR_HEX_CODE)
const dataTypeText = computed(() => props.dataType.toLowerCase())
</script>

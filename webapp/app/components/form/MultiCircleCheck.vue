<template>
  <div class="flex flex-wrap">
    <div
      v-for="option in options"
      :key="option.value"
      class="w-8 h-8 rounded-full border solid border-primary-400 dark:border-gray-200 text-center flex items-center justify-center mr-1 mb-1 cursor-pointer"
      :class="{
        'bg-white dark:bg-transparent dark:text-gray-200': !selectedValues?.includes(option.value),
        'bg-primary-500 text-white': selectedValues?.includes(option.value),
        'cursor-not-allowed pointer-events-none': disabled,
      }"
      @click="handleBtnClick(option.value)"
    >
      <span> {{ option.label }} </span>
    </div>
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import { useField } from 'vee-validate'

const props = defineProps<{
  formControlName: string
  options: FormMenuOption[]
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'change', v: string[]): void
}>()

const { value: selectedValues, errorMessage } = useField<string[]>(() => props.formControlName)

const handleBtnClick = (selectedValue: string) => {
  if (!selectedValues.value.includes(selectedValue)) {
    selectedValues.value = [...selectedValues.value, selectedValue]
  } else {
    selectedValues.value = selectedValues.value.filter((v) => v !== selectedValue)
  }
  emit('change', selectedValues.value)
}
</script>

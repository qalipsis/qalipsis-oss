<template>
  <div class="flex items-center cursor-pointer gap-x-2">
    <input
      type="radio"
      :id="value"
      :checked="isChecked"
      :disabled="disabled"
      @change="handleChange"
      class="relative appearance-none w-4 h-4 border border-gray-300 rounded-full bg-white disabled:bg-gray-50 dark:disabled:bg-gray-600 disabled:cursor-not-allowed enabled:hover:border-primary-500 cursor-pointer shrink-0 checked:border-primary-500 after:content-[''] after:w-[10px] after:h-[10px] after:mx-auto after:my-[2px] after:rounded-full after:hidden checked:after:block checked:after:bg-primary-500"
    />
    <label
      v-if="label"
      :for="value"
      :class="disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'"
    >
      {{ label }}
    </label>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  value: string
  label?: string | number
  disabled?: boolean
}>()

// Inject parent RadioButtonGroup
const radioGroup = inject<{
  modelValue: { value: string }
  updateValue: (v: string) => void
}>('radioGroup')

if (!radioGroup) {
  throw new Error('RadioButton must be used inside a RadioButtonGroup')
}

// Computed to determine if this option is selected
const isChecked = computed(() => radioGroup.modelValue.value === props.value)

// Click handler calls parent update function
const handleChange = () => {
  radioGroup.updateValue(props.value)
}
</script>

<template>
  <div
    v-if="label"
    class="mb-4"
  >
    <FormLabel :text="label" />
  </div>

  <div class="flex items-center gap-x-3">
    <div
      v-for="option in options"
      :key="option.value"
    >
      <FormRadioButton
        :value="option.value"
        :label="option.label"
        :disabled="disabled"
      />
    </div>
  </div>

  <FormErrorMessage :errorMessage="errorMessage" />
</template>

<script setup lang="ts">
import { useField } from 'vee-validate'

const props = defineProps<{
  formControlName: string
  /**
   * The options for the dropdown menu.
   */
  options: FormMenuOption[]
  label?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'change', v: string): void
}>()

const { value: fieldValue, errorMessage } = useField<string>(() => props.formControlName)

// Local reactive value for injection
const selected = ref(fieldValue.value)

// Sync vee-validate value → selected
watch(
  () => fieldValue.value,
  (val) => {
    selected.value = val
  },
)

// Provide the radio group context
provide('radioGroup', {
  modelValue: selected,
  updateValue: (val: string) => {
    selected.value = val
    fieldValue.value = val // update vee-validate field
    emit('change', val) // emit change event
  },
})
</script>

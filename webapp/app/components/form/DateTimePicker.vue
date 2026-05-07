<template>
  <div
    class="date-time-picker"
    :class="{ 'has-error': hasError }"
  >
    <FormLabel
      :text="label"
      :hasError="hasError"
    />
    <VueDatePicker
      v-model="value"
      :format="format"
      :dark="darkThemeEnabled"
      :min-date="minDate"
      :clearable="false"
      @update:model-value="handleDate"
      :alt-position="customPosition"
      :disabled="disabled"
      time-picker-inline
    />
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import { VueDatePicker } from '@vuepic/vue-datepicker'
import '@vuepic/vue-datepicker/dist/main.css'
import { type TypedSchema, useField } from 'vee-validate'

const customPosition = (_: HTMLElement) => ({ top: 0, left: 0 })

const props = defineProps<{
  label: string
  formControlName: string
  format: string
  disabled?: boolean
  fieldValidationSchema?: TypedSchema
  minDate?: Date
}>()
const emit = defineEmits<{
  (e: 'change', v: Date): void
}>()

const themeStore = useThemeStore()

const darkThemeEnabled = computed(() => themeStore.theme === 'dark')
const hasError = computed(() => !!errorMessage.value)

const { value, errorMessage } = useField<string>(() => props.formControlName, props.fieldValidationSchema)

const handleDate = (modelData: Date) => {
  emit('change', modelData)
}
</script>

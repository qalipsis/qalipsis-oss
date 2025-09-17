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
        ref="dp"
        :format="format"
        :dark="darkThemeEnabled"
        :min-date="minDate"
        :clearable="false"
        @update:model-value="handleDate"
        time-picker-inline
    />
    <FormErrorMessage :errorMessage="errorMessage"/>
  </div>
</template>

<script setup lang="ts">
import VueDatePicker from '@vuepic/vue-datepicker'
import '@vuepic/vue-datepicker/dist/main.css'
import {type TypedSchema, useField} from 'vee-validate'

const props = defineProps<{
  label: string
  formControlName: string
  format: string
  fieldValidationSchema?: TypedSchema
  minDate?: Date
}>()
const emit = defineEmits<{
  (e: 'Change', v: Date): void
}>()

const themeStore = useThemeStore()

const dp = ref()
const darkThemeEnabled = computed(() => themeStore.theme === 'dark')
const hasError = computed(() => (errorMessage.value ? true : false))

const {value, errorMessage} = useField<string>(() => props.formControlName, props.fieldValidationSchema)

const handleDate = (modelData: Date) => {
  emit('Change', modelData)
}
</script>

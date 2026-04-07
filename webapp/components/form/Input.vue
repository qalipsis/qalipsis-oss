<template>
  <div>
    <FormLabel
      :text="label"
      :hasError="hasError"
    />
    <div
      :class="[
        TailwindClassConfig.formInputWrapperClass,
        hasError ? TailwindClassConfig.formInputWrapperErrorClass : TailwindClassConfig.formInputWrapperActiveClass,
      ]"
    >
      <input
        :class="TailwindClassConfig.formInputClass"
        :id="formControlName"
        :value="inputValue"
        :type="type ?? 'text'"
        :placeholder="placeholder"
        :disabled="disabled"
        @input="debouncedInputChange(($event.target as HTMLInputElement).value)"
      />
      <span
        v-if="suffix"
        class="text-gray-400"
        >{{ suffix }}</span
      >
    </div>
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate'

const props = defineProps<{
  label: string
  type?: FormInputType
  modelValue?: string
  formControlName: string
  fieldValidationSchema?: TypedSchema
  placeholder?: string
  suffix?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'input', v: string): void
  (e: 'update:modelValue', v: string): void
}>()

const debouncedInputChange = debounce((newValue: string) => {
  inputValue.value = newValue
  emit('input', newValue)
  emit('update:modelValue', newValue)
}, 300)

const fieldValidationSchema = toRef(props, 'fieldValidationSchema')
const { value: inputValue, errorMessage } = useField<string>(() => props.formControlName, fieldValidationSchema, {
  initialValue: props.modelValue,
})
const hasError = computed(() => !!errorMessage.value)
</script>

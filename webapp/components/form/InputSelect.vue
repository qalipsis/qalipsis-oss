<template>
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
      type="text"
      :class="TailwindClassConfig.formInputClass"
      :id="formInputControlName"
      :value="inputValue"
      :placeholder="inputPlaceholder"
      :disabled="inputDisabled"
      @input="handleInputChange(($event.target as HTMLInputElement).value)"
    />
    <Listbox
      v-model="selectValue"
      :disabled="selectDisabled"
    >
      <div :class="TailwindClassConfig.formDropdownClass">
        <ListboxButton>
          <div
            class="flex items-center justify-between border-l border-solid p-2 border-gray-200 w-20 h-10"
            :class="{
              'border-red-600 dark:border-red-400': hasError,
            }"
          >
            <input
              readonly
              type="text"
              class="cursor-pointer outline-none"
              :id="formSelectControlName"
              :class="TailwindClassConfig.formInputClass"
              :value="selectedOptionLabel"
              :placeholder="selectPlaceholder"
              :disabled="selectDisabled"
            />
            <BaseIcon
              icon="qls-icon-arrow-down"
              class="text-xl"
              :class="selectDisabled ? 'text-gray-500' : ''"
            />
          </div>
        </ListboxButton>
        <ListboxOptions
          class="w-fit"
          :class="TailwindClassConfig.formDropdownPanelClass"
        >
          <ListboxOption
            v-for="option in options"
            :key="option.value"
            :value="option.value"
            :disabled="option.disabled"
            v-slot="{ active, selected }"
            as="template"
          >
            <div @click="handleSelectChange(option)">
              <slot
                name="optionContent"
                :option="option"
              >
                <FormSelectOption
                  :label="option.label"
                  :active="active"
                  :disabled="option.disabled"
                  :selected="selected"
                />
              </slot>
            </div>
          </ListboxOption>
        </ListboxOptions>
      </div>
    </Listbox>
  </div>
  <FormErrorMessage :errorMessage="inputErrorMessage" />
  <FormErrorMessage :errorMessage="selectErrorMessage" />
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate'
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue'

const props = defineProps<{
  label: string
  formInputControlName: string
  formInputModelValue?: string
  formSelectControlName: string
  formSelectModelValue?: string
  options: FormMenuOption[]
  inputFieldValidationSchema?: TypedSchema
  inputPlaceholder?: string
  inputDisabled?: boolean
  selectPlaceholder?: string
  selectDisabled?: boolean
  selectFieldValidationSchema?: TypedSchema
}>()
const emit = defineEmits<{
  (e: 'update:formInputModelValue', v: string): void
  (e: 'update:formSelectModelValue', v: string): void
}>()

const inputFieldValidationSchema = toRef(props, 'inputFieldValidationSchema')
const selectFieldValidationSchema = toRef(props, 'selectFieldValidationSchema')

const { value: inputValue, errorMessage: inputErrorMessage } = useField<string>(
  () => props.formInputControlName,
  inputFieldValidationSchema,
  {
    initialValue: props.formInputModelValue,
  },
)
const { value: selectValue, errorMessage: selectErrorMessage } = useField<string>(
  () => props.formSelectControlName,
  selectFieldValidationSchema,
  {
    initialValue: props.formSelectModelValue,
  },
)

const hasError = computed(() => !!(inputErrorMessage.value || selectErrorMessage.value))

const selectedOptionLabel = computed(() => props.options.find((option) => option.value === selectValue.value)?.label)

const debouncedInputChange = debounce((newValue: string) => {
  inputValue.value = newValue
  emit('update:formInputModelValue', newValue)
}, 300)

const handleInputChange = (newValue: string) => {
  debouncedInputChange(newValue)
}

const handleSelectChange = (option: FormMenuOption) => {
  selectValue.value = option.value
  emit('update:formSelectModelValue', option.value)
}
</script>

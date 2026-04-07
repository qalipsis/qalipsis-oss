<template>
  <div>
    <FormLabel
      :text="label"
      :hasError="hasError"
    />
    <Combobox
      v-model="selectedFormControlValue"
      @update:modelValue="handleSelect"
    >
      <div
        class="w-full"
        :class="TailwindClassConfig.formDropdownClass"
      >
        <ComboboxButton
          :disabled="disabled"
          class="outline-none w-full"
        >
          <div
            :class="[
              TailwindClassConfig.formInputWrapperClass,
              hasError
                ? TailwindClassConfig.formInputWrapperErrorClass
                : TailwindClassConfig.formInputWrapperActiveClass,
            ]"
          >
            <input
              type="text"
              autocomplete="off"
              :class="TailwindClassConfig.formInputClass"
              :value="inputValue"
              :id="formControlName"
              :placeholder="placeholder"
              :disabled="disabled"
              @input="handleInputChange(($event.target as HTMLInputElement).value)"
            />
            <BaseIcon
              icon="qls-icon-arrow-down"
              class="text-xl"
              :class="disabled ? 'text-gray-500' : ''"
            />
          </div>
        </ComboboxButton>
        <transition
          :enter-active-class="TailwindClassConfig.formDropdownTransitionEnterActiveClass"
          :enter-from-class="TailwindClassConfig.formDropdownTransitionEnterFromClass"
          :enter-to-class="TailwindClassConfig.formDropdownTransitionEnterToClass"
          :leave-active-class="TailwindClassConfig.formDropdownTransitionLeaveActiveClass"
          :leave-from-class="TailwindClassConfig.formDropdownTransitionLeaveFromClass"
          :leave-to-class="TailwindClassConfig.formDropdownTransitionLeaveToClass"
        >
          <ComboboxOptions
            class="w-full"
            :class="[TailwindClassConfig.formDropdownPanelClass, !filteredOptions.length ? 'invisible' : 'visible']"
          >
            <ComboboxOption
              v-for="option in filteredOptions"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
              v-slot="{ active, selected }"
              as="template"
            >
              <div>
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
            </ComboboxOption>
          </ComboboxOptions>
        </transition>
      </div>
    </Combobox>
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import { Combobox, ComboboxButton, ComboboxOption, ComboboxOptions } from '@headlessui/vue'
import { type TypedSchema, useField } from 'vee-validate'

const props = defineProps<{
  label: string
  formControlName: string
  options: FormMenuOption[]
  modelValue?: string
  fieldValidationSchema?: TypedSchema
  placeholder?: string
  disabled?: boolean
  customSearchEnabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'select', v: string): void
  (e: 'search', v: string): void
  (e: 'update:modelValue', v: string): void
}>()

const { value: selectedFormControlValue, errorMessage } = useField<string>(
  () => props.formControlName,
  props.fieldValidationSchema,
)

const findLabel = (value: string) => props.options.find((o) => o.value === value)?.label ?? value

const inputValue = ref(findLabel(selectedFormControlValue.value ?? ''))

watch(selectedFormControlValue, (newVal) => {
  inputValue.value = findLabel(newVal ?? '')
})

const hasError = computed(() => !!errorMessage.value)

const filteredOptions = computed(() => {
  if (props.customSearchEnabled) {
    return props.options
  }

  return inputValue.value === ''
    ? props.options
    : props.options.filter((option) => option.label.toLowerCase().includes(inputValue.value.toLowerCase()))
})

let inputTimer: ReturnType<typeof setTimeout> | null = null

onUnmounted(() => {
  if (inputTimer) clearTimeout(inputTimer)
})

const handleInputChange = (newValue: string) => {
  inputValue.value = newValue
  if (inputTimer) clearTimeout(inputTimer)
  inputTimer = setTimeout(() => {
    selectedFormControlValue.value = newValue
    if (props.customSearchEnabled) emit('search', newValue)
    emit('update:modelValue', newValue)
  }, 300)
}

const handleSelect = (newValue: string) => {
  if (inputTimer) {
    clearTimeout(inputTimer)
    inputTimer = null
  }
  const selectedOption = props.options.find((o) => o.value === newValue)
  if (selectedOption) {
    inputValue.value = selectedOption.label
    emit('select', newValue)
  }

  emit('update:modelValue', newValue)
}
</script>

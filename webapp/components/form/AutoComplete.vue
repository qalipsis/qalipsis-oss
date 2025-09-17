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
          :class="TailwindClassHelper.formDropdownClass"
      >
        <ComboboxButton
            :disabled="disabled"
            class="outline-none w-full"
        >
          <div
              :class="[
              TailwindClassHelper.formInputWrapperClass,
              hasError
                ? TailwindClassHelper.formInputWrapperErrorClass
                : TailwindClassHelper.formInputWrapperActiveClass,
            ]"
          >
            <input
                type="text"
                :class="TailwindClassHelper.formInputClass"
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
            :enter-active-class="TailwindClassHelper.formDropdownTransitionEnterActiveClass"
            :enter-from-class="TailwindClassHelper.formDropdownTransitionEnterFromClass"
            :enter-to-class="TailwindClassHelper.formDropdownTransitionEnterToClass"
            :leave-active-class="TailwindClassHelper.formDropdownTransitionLeaveActiveClass"
            :leave-from-class="TailwindClassHelper.formDropdownTransitionLeaveFromClass"
            :leave-to-class="TailwindClassHelper.formDropdownTransitionLeaveToClass"
        >
          <ComboboxOptions
              class="w-full"
              :class="[TailwindClassHelper.formDropdownPanelClass, !filteredOptions.length ? 'invisible' : 'visible']"
          >
            <ComboboxOption
                v-for="filteredOption in filteredOptions"
                :key="filteredOption[optionValueKey]"
                :value="filteredOption[optionValueKey]"
                :disabled="filteredOption.disabled"
                v-slot="{ active, selected }"
                as="template"
            >
              <div>
                <slot
                    name="optionContent"
                    :option="filteredOption"
                >
                  <div
                      class="flex items-center mb-1 cursor-pointer"
                      :class="[
                      active ? 'bg-primary-50' : '',
                      selected ? TailwindClassHelper.formDropdownOptionActiveClass : '',
                      TailwindClassHelper.formDropdownOptionClass,
                    ]"
                  >
                    {{ filteredOption[optionLabelKey] }}
                  </div>
                </slot>
              </div>
            </ComboboxOption>
          </ComboboxOptions>
        </transition>
      </div>
    </Combobox>
    <FormErrorMessage :errorMessage="errorMessage"/>
  </div>
</template>

<script setup lang="ts">
import {Combobox, ComboboxButton, ComboboxOption, ComboboxOptions} from '@headlessui/vue'
import {type TypedSchema, useField} from 'vee-validate'

const props = defineProps<{
  label: string
  formControlName: string
  /**
   * The options for the dropdown menu.
   */
  options: FormMenuOption[] | any[]
  modelValue?: string
  labelKey?: string
  valueKey?: string
  fieldValidationSchema?: TypedSchema
  placeholder?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'select', v: string): void
  (e: 'update:modelValue', v: string): void
}>()

const {value: selectedFormControlValue, errorMessage} = useField<string | string[]>(
    () => props.formControlName,
    props.fieldValidationSchema
)

const optionLabelKey = computed(() => props.labelKey ?? 'label')
const optionValueKey = computed(() => props.valueKey ?? 'value')

const selectedOptionLabel = computed(
    () => props.options.find((option) => option[optionValueKey.value] === selectedFormControlValue.value)?.label
)

const inputValue = ref(selectedOptionLabel.value ?? selectedFormControlValue.value ?? '')

const hasError = computed(() => (errorMessage.value ? true : false))

const filteredOptions = computed(() =>
    inputValue.value === ''
        ? props.options
        : props.options.filter((option) =>
            option[optionLabelKey.value].toLowerCase().includes(inputValue.value.toLowerCase())
        )
)

const debouncedInputChange = debounce((newValue: string) => {
  inputValue.value = newValue
  selectedFormControlValue.value = newValue
  emit('select', newValue)
  emit('update:modelValue', newValue)
}, 300)

const handleInputChange = (newValue: string) => {
  debouncedInputChange(newValue)
}

const handleSelect = (newValue: string) => {
  const selectedOption = props.options.find((opt) => opt[optionValueKey.value] === newValue)
  if (selectedOption) {
    inputValue.value = selectedOption[optionLabelKey.value]
    emit('select', newValue)
  }
  emit('update:modelValue', newValue)
}
</script>

<template>
  <div>
    <FormLabel
      :text="label"
      :hasError="hasError"
    />
    <Listbox
      v-model="selectedFormControlValue"
      @update:model-value="handleValueUpdate"
      :multiple="multipleEnabled"
    >
      <div
        class="w-full"
        :class="TailwindClassHelper.formDropdownClass"
      >
        <ListboxButton
          :disabled="disabled"
          class="outline-none w-full"
        >
          <template v-if="multipleEnabled">
            <div
              :class="[
                TailwindClassHelper.formInputWrapperClass,
                hasError
                  ? TailwindClassHelper.formInputWrapperErrorClass
                  : TailwindClassHelper.formInputWrapperActiveClass,
              ]"
            >
              <div class="flex items-center">
                <div
                  v-for="selectedOption in selectedOptions"
                  :key="selectedOption[optionValueKey]"
                  :value="selectedOption[optionValueKey]"
                  class="flex items-center px-2 rounded-lg text-sm bg-gray-100 dark:bg-gray-800 mr-2 last:mr-0"
                >
                  <span class="pr-2">{{ selectedOption[optionLabelKey] }}</span>
                  <div @click="handleDeleteButtonClick(selectedOption)">
                    <BaseIcon
                      class="text-xl hover:text-primary-500"
                      icon="qls-icon-close"
                    ></BaseIcon>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div
              :class="[
                TailwindClassHelper.formInputWrapperClass,
                hasError
                  ? TailwindClassHelper.formInputWrapperErrorClass
                  : TailwindClassHelper.formInputWrapperActiveClass,
              ]"
            >
              <input
                readonly
                type="text"
                class="cursor-pointer"
                :class="TailwindClassHelper.formInputClass"
                :value="selectedOptionLabel"
                :placeholder="placeholder"
                :disabled="disabled"
              />
              <BaseIcon
                icon="qls-icon-arrow-down"
                :class="disabled ? 'text-gray-500' : ''"
                :width="20"
                :height="20"
              />
            </div>
          </template>
        </ListboxButton>
        <transition
          :enter-active-class="TailwindClassHelper.formDropdownTransitionEnterActiveClass"
          :enter-from-class="TailwindClassHelper.formDropdownTransitionEnterFromClass"
          :enter-to-class="TailwindClassHelper.formDropdownTransitionEnterToClass"
          :leave-active-class="TailwindClassHelper.formDropdownTransitionLeaveActiveClass"
          :leave-from-class="TailwindClassHelper.formDropdownTransitionLeaveFromClass"
          :leave-to-class="TailwindClassHelper.formDropdownTransitionLeaveToClass"
        >
          <ListboxOptions
            class="w-full"
            :class="[TailwindClassHelper.formDropdownPanelClass, !options.length ? 'invisible' : 'visible']"
          >
            <ListboxOption
              v-for="option in options"
              :key="option[optionValueKey]"
              :value="option[optionValueKey]"
              :disabled="option.disabled"
              v-slot="{ active, selected }"
              as="template"
            >
              <div @click="emit('change', option[optionValueKey])">
                <slot
                  name="optionContent"
                  :option="option"
                >
                  <div
                    class="flex items-center mb-1 cursor-pointer"
                    :class="[
                      active ? 'bg-primary-50' : '',
                      selected ? TailwindClassHelper.formDropdownOptionActiveClass : '',
                      TailwindClassHelper.formDropdownOptionClass,
                    ]"
                  >
                    {{ option[optionLabelKey] }}
                  </div>
                </slot>
              </div>
            </ListboxOption>
          </ListboxOptions>
        </transition>
      </div>
    </Listbox>
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate'
import { Listbox, ListboxButton, ListboxOptions, ListboxOption } from '@headlessui/vue'

const props = defineProps<{
  label: string
  formControlName: string
  /**
   * The options for the dropdown menu.
   */
  options: FormMenuOption[] | any[]
  multipleEnabled?: boolean
  modelValue?: string
  labelKey?: string
  valueKey?: string
  fieldValidationSchema?: TypedSchema
  placeholder?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'change', v: string): void
  (e: 'update:modelValue', v: string | string[]): void
}>()

const { value: selectedFormControlValue, errorMessage } = useField<string | string[]>(
  () => props.formControlName,
  props.fieldValidationSchema,
  {
    initialValue: props.modelValue,
  }
)

const optionLabelKey = computed(() => props.labelKey ?? 'label')
const optionValueKey = computed(() => props.valueKey ?? 'value')

const hasError = computed(() => (errorMessage.value ? true : false))

const selectedOptions = computed(() =>
  props.options.filter((option) => (selectedFormControlValue.value as string[])?.includes(option[optionValueKey.value]))
)

const selectedOptionLabel = computed(() => {
  return props.options.find((option) => option[optionValueKey.value] === selectedFormControlValue.value)?.[
    optionLabelKey.value
  ]
})

const handleDeleteButtonClick = (option: any | FormMenuOption) => {
  selectedFormControlValue.value = (selectedFormControlValue.value as string[]).filter(
    (value) => value !== option[optionValueKey.value]
  )
}

const handleValueUpdate = () => {
  emit('update:modelValue', selectedFormControlValue.value)
}
</script>

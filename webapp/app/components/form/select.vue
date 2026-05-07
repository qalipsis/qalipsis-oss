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
        :class="TailwindClassConfig.formDropdownClass"
      >
        <ListboxButton
          :disabled="disabled"
          class="outline-none w-full"
        >
          <template v-if="multipleEnabled">
            <div
              :class="[
                TailwindClassConfig.formInputWrapperClass,
                hasError
                  ? TailwindClassConfig.formInputWrapperErrorClass
                  : TailwindClassConfig.formInputWrapperActiveClass,
                '!h-auto min-h-10 py-1',
              ]"
            >
              <div class="flex flex-wrap items-center gap-1">
                <div
                  v-for="selectedOption in selectedOptions"
                  :key="selectedOption.value"
                  class="flex items-center px-2 rounded-lg text-sm bg-gray-100 dark:bg-gray-800"
                >
                  <span class="pr-2">{{ selectedOption.label }}</span>
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
                TailwindClassConfig.formInputWrapperClass,
                hasError
                  ? TailwindClassConfig.formInputWrapperErrorClass
                  : TailwindClassConfig.formInputWrapperActiveClass,
              ]"
            >
              <input
                readonly
                type="text"
                class="cursor-pointer"
                autocomplete="off"
                :class="TailwindClassConfig.formInputClass"
                :value="selectedOptionLabel"
                :placeholder="placeholder"
                :disabled="disabled"
              />
              <div class="flex items-center">
                <BaseIcon
                  v-if="clearEnabled && selectedFormControlValue"
                  icon="qls-icon-close"
                  class="hover:text-primary-500"
                  :class="disabled ? 'text-gray-500' : ''"
                  @click="handleClearButtonClick($event)"
                />
                <BaseIcon
                  icon="qls-icon-arrow-down"
                  :class="disabled ? 'text-gray-500' : ''"
                />
              </div>
            </div>
          </template>
        </ListboxButton>
        <transition
          :enter-active-class="TailwindClassConfig.formDropdownTransitionEnterActiveClass"
          :enter-from-class="TailwindClassConfig.formDropdownTransitionEnterFromClass"
          :enter-to-class="TailwindClassConfig.formDropdownTransitionEnterToClass"
          :leave-active-class="TailwindClassConfig.formDropdownTransitionLeaveActiveClass"
          :leave-from-class="TailwindClassConfig.formDropdownTransitionLeaveFromClass"
          :leave-to-class="TailwindClassConfig.formDropdownTransitionLeaveToClass"
        >
          <ListboxOptions
            class="w-full"
            :class="[TailwindClassConfig.formDropdownPanelClass, !options.length ? 'invisible' : 'visible']"
          >
            <ListboxOption
              v-for="option in options"
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
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue'

const props = defineProps<{
  label: string
  formControlName: string
  options: FormMenuOption[]
  multipleEnabled?: boolean
  clearEnabled?: boolean
  modelValue?: string | string[]
  fieldValidationSchema?: TypedSchema
  placeholder?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'change', v: string | string[]): void
  (e: 'update:modelValue', v: string | string[]): void
}>()

const { value: selectedFormControlValue, errorMessage } = useField<string | string[]>(
  () => props.formControlName,
  props.fieldValidationSchema,
  {
    initialValue: props.modelValue,
  },
)

const hasError = computed(() => !!errorMessage.value)

const selectedOptions = computed(() =>
  props.options.filter((option) => (selectedFormControlValue.value as string[])?.includes(option.value)),
)

const selectedOptionLabel = computed(
  () => props.options.find((option) => option.value === selectedFormControlValue.value)?.label,
)

const handleDeleteButtonClick = (option: FormMenuOption) => {
  selectedFormControlValue.value = (selectedFormControlValue.value as string[]).filter(
    (value) => value !== option.value,
  )
  emit('change', selectedFormControlValue.value)
  emit('update:modelValue', selectedFormControlValue.value)
}

const handleClearButtonClick = (event: MouseEvent) => {
  event.preventDefault()
  selectedFormControlValue.value = ''
  emit('change', '')
  emit('update:modelValue', '')
}

const handleValueUpdate = () => {
  emit('change', selectedFormControlValue.value)
  emit('update:modelValue', selectedFormControlValue.value)
}
</script>

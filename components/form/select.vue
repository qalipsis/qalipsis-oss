<template>
  <div>
    <FormLabel :text="label" />
    <Listbox v-model="selectedFormControlValue" :multiple="multipleEnabled">
      <div class="w-full" :class="TailwindClassHelper.formDropdownClass">
        <ListboxButton :disabled="disabled" class="outline-none w-full">
          <template v-if="multipleEnabled">
            <!-- TODO: -->
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
                icon="/icons/icon-arrow-down-light-black.svg"
                :class="
                  disabled ? TailwindClassHelper.grayColorFilterClass : ''
                "
                :width="20"
                :height="20"
              />
            </div>
          </template>
        </ListboxButton>
        <transition
          :enter-active-class="
            TailwindClassHelper.formDropdownTransitionEnterActiveClass
          "
          :enter-from-class="
            TailwindClassHelper.formDropdownTransitionEnterFromClass
          "
          :enter-to-class="
            TailwindClassHelper.formDropdownTransitionEnterToClass
          "
          :leave-active-class="
            TailwindClassHelper.formDropdownTransitionLeaveActiveClass
          "
          :leave-from-class="
            TailwindClassHelper.formDropdownTransitionLeaveFromClass
          "
          :leave-to-class="
            TailwindClassHelper.formDropdownTransitionLeaveToClass
          "
        >
          <ListboxOptions
            class="w-full"
            :class="TailwindClassHelper.formDropdownPanelClass"
          >
            <div v-if="!options?.length" class="text-gray-500">
              No available options.
            </div>
            <ListboxOption
              v-for="option in options"
              :key="option[optionValueKey]"
              :value="option[optionValueKey]"
              :disabled="option.disabled"
              v-slot="{ active, selected }"
              as="template"
            >
              <div @click="emit('change', option[optionValueKey])">
                <slot name="optionContent" :option="option">
                  <div
                    class="flex items-center mb-1 cursor-pointer"
                    :class="[
                      active
                        ? 'bg-primary-50'
                        : '',
                      selected
                        ? TailwindClassHelper.formDropdownOptionActiveClass
                        : '',
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
import { type TypedSchema, useField } from "vee-validate";
import {
  Listbox,
  ListboxButton,
  ListboxOptions,
  ListboxOption,
} from "@headlessui/vue";

const props = defineProps<{
  label: string;
  formControlName: string;
  /**
   * The options for the dropdown menu.
   */
  options: FormMenuOption[] | any[];
  multipleEnabled?: boolean;
  modelValue?: string;
  labelKey?: string;
  valueKey?: string;
  fieldValidationSchema?: TypedSchema;
  placeholder?: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (e: "change", v: string): void;
}>();

const { value: selectedFormControlValue, errorMessage } = useField<
  string | string[]
>(() => props.formControlName, props.fieldValidationSchema, {
  initialValue: props.modelValue,
});

const optionLabelKey = computed(() => props.labelKey ?? "label");
const optionValueKey = computed(() => props.valueKey ?? "value");

const hasError = computed(() => (errorMessage.value ? true : false));

const selectedOptions = computed(() =>
  props.options.filter((option) =>
    (selectedFormControlValue.value as string[]).includes(option.value)
  )
);

const selectedOptionLabel = computed(
  () =>
    props.options.find(
      (option) => option.value === selectedFormControlValue.value
    )?.label
);
</script>

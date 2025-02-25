<template>
    <FormLabel :text="label" />
    <div
        :class="[
            TailwindClassHelper.formInputWrapperClass,
            hasError
                ? TailwindClassHelper.formInputWrapperErrorClass
                : TailwindClassHelper.formInputWrapperActiveClass
        ]"
    >
        <input
            type="text"
            :class="TailwindClassHelper.formInputClass"
            :id="formInputControlName"
            :value="inputValue"
            :placeholder="inputPlaceholder"
            :disabled="inputDisabled"
            @input="handleInputChange(($event.target as HTMLInputElement).value)"
        >
        <Listbox v-model="selectValue">
            <div :class="TailwindClassHelper.formDropdownClass">
                <ListboxButton>
                    <div 
                        class="flex items-center justify-between border-l border-solid p-2 border-gray-200 w-20"
                        :class="{
                            'border-red-500': hasError
                        }"
                    >
                        <input
                            readonly
                            type="text"
                            class="cursor-pointer outline-none"
                            :id="formSelectControlName"
                            :class="TailwindClassHelper.formInputClass"
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
                    :class="TailwindClassHelper.formDropdownPanelClass"
                >
                    <ListboxOption
                        v-for="option in options"
                        :key="option[optionValueKey]"
                        :value="option[optionValueKey]"
                        :disabled="option.disabled"
                        v-slot="{ active, selected }"
                        as="template"
                    >
                        <div @click="handleSelectChange(option)">
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
            </div>
        </Listbox>
    </div>
    <FormErrorMessage :errorMessage="inputErrorMessage"/>
    <FormErrorMessage :errorMessage="selectErrorMessage"/>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate';
import {
  Listbox,
  ListboxButton,
  ListboxOptions,
  ListboxOption,
} from "@headlessui/vue";

const props = defineProps<{
    label: string;
    formInputControlName: string;
    formInputModelValue?: string;
    formSelectControlName: string;
    formSelectModelValue?: string;
    /**
     * The options for the dropdown menu.
     */
    options: FormMenuOption[] | any[];
    labelKey?: string;
    valueKey?: string;
    inputFieldValidationSchema?: TypedSchema;
    inputPlaceholder?: string;
    inputDisabled?: boolean;
    selectPlaceholder?: string;
    selectDisabled?: boolean;
    selectFieldValidationSchema?: TypedSchema;
}>();
const emit = defineEmits<{
    (e: "update:formInputModelValue", v: string): void;
    (e: "update:formSelectModelValue", v: string): void;
}>()

const { value: inputValue, errorMessage: inputErrorMessage } = useField<string>(
    () => props.formInputControlName,
    props.inputFieldValidationSchema,
    {
        initialValue: props.formInputModelValue,
    }
);
const { value: selectValue, errorMessage: selectErrorMessage } = useField<string>(
    () => props.formSelectControlName,
    props.selectFieldValidationSchema,
    {
        initialValue: props.formSelectModelValue,
    }
)

const optionLabelKey = computed(() => props.labelKey ?? "label");
const optionValueKey = computed(() => props.valueKey ?? "value");

const hasError = computed(() => (inputErrorMessage.value || selectErrorMessage.value) ? true : false);

const selectedOptionLabel = computed(
  () =>
    props.options.find(
      (option) => option[optionValueKey.value] === selectValue.value
    )?.label
);

const debouncedInputChange = debounce((newValue: string) => {
    inputValue.value = newValue;
    emit("update:formInputModelValue", newValue);
}, 300);

const handleInputChange = (newValue: string) => {
    debouncedInputChange(newValue);
}

const handleSelectChange = (option: FormMenuOption | any) => {
    selectValue.value = option[optionValueKey.value];
    emit("update:formSelectModelValue", option.value);
}

</script>

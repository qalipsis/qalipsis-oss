<template>
    <div>
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
                :class="TailwindClassHelper.formInputClass"
                :id="formControlName"
                :value="inputValue"
                :type="inputFormControlType"
                :placeholder="placeholder"
                :disabled="disabled"
                @input="handleInputChange(($event.target as HTMLInputElement).value)"
            >
            <span v-if="suffix" class="text-gray-400">{{ suffix }}</span>
        </div>
        <FormErrorMessage :errorMessage="errorMessage"/>
    </div>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate';

const props = defineProps<{
    label: string;
    type?: FormInputType;
    modelValue?: string;
    formControlName: string;
    fieldValidationSchema?: TypedSchema;
    placeholder?: string;
    suffix?: string;
    disabled?: boolean;
}>();

const inputFormControlType = computed(() => props.type ?? 'text');

const emit = defineEmits<{
    (e: "input", v: string): void
    (e: "update:modelValue", v: string): void
}>()

const debouncedInputChange = debounce((newValue: string) => {
    inputValue.value = newValue;
    emit("input", newValue);
    emit("update:modelValue", newValue);
}, 300);

const { value: inputValue, errorMessage } = useField<string>(
    () => props.formControlName,
    props.fieldValidationSchema,
    {
        initialValue: props.modelValue,
    }
);
const hasError = computed(() => errorMessage.value ? true : false);

const handleInputChange = (newValue: string) => {
    debouncedInputChange(newValue);
}

</script>

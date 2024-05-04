<template>
    <div>
        <FormLabel :text="label" />
        <div 
            class="border px-2 py-1 h-10 border-solid w-full rounded-md flex items-center justify-between has-[:disabled]:bg-gray-50 has-[:disabled]:text-gray-400"
            :class="{
                'border-gray-200 has-[:focus]:border-primary-500 has-[:enabled]:hover:border-primary-500 has-[:enabled]:hover:bg-primary-50': !hasError,
                'border-red-600': hasError
            }"
        >
            <input
                class="outline-none w-full bg-transparent disabled:cursor-not-allowed"
                :value="inputValue"
                :type="type ?? 'text'"
                :placeholder="placeholder"
                :disabled="disabled"
                @input="handleInputChange(($event.target as HTMLInputElement).value)"
            >
            <span v-if="suffix" class=" text-gray-400">{{ suffix }}</span>
        </div>
        <FormErrorMessage :errorMessage="errorMessage"/>
    </div>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate';

const props = defineProps<{
    label: string,
    type?: FormInputType,
    modelValue?: string,
    formControlName: string,
    fieldValidationSchema?: TypedSchema,
    placeholder?: string,
    suffix?: string,
    disabled?: boolean
}>();
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

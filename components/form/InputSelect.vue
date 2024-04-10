<template>
    <FormLabel :text="label" />
    <a-input 
        v-model:value="inputValue"
        :disabled="inputDisabled"
        :placeholder="inputPlaceholder"
        :field-validation-schema="inputFieldValidationSchema"
        :status="inputErrorStatus">
        <template #addonAfter>
            <a-select 
                size="large"
                v-model:value="selectValue"
                :options="options"
                :placeholder="selectPlaceholder"
                :disabled="selectDisabled"
                :field-validation-schema="selectFieldValidationSchema"
                :status="selectErrorStatus"
                @change="emit('change', selectValue)" />
        </template>
    </a-input>
    <FormErrorMessage :errorMessage="inputErrorMessage"/>
    <FormErrorMessage :errorMessage="selectErrorMessage"/>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate';

const props = defineProps<{
    label: string,
    formInputControlName: string,
    formSelectControlName: string,
    /**
     * The options for the dropdown menu.
     */
    options: FormMenuOption[],
    inputFieldValidationSchema?: TypedSchema,
    inputPlaceholder?: string,
    inputDisabled?: boolean,
    selectPlaceholder?: string,
    selectDisabled?: boolean,
    selectFieldValidationSchema?: TypedSchema,
}>();
const emit = defineEmits<{
    (e: "change", v: string): void
}>()

const { value: inputValue, errorMessage: inputErrorMessage } = useField<string>(
    () => props.formInputControlName,
    props.inputFieldValidationSchema);
const { value: selectValue, errorMessage: selectErrorMessage } = useField<string>(
    () => props.formSelectControlName,
    props.selectFieldValidationSchema);
const inputErrorStatus = computed(() => inputErrorMessage.value ? 'error' : '')
const selectErrorStatus = computed(() => selectErrorMessage.value ? 'error' : '')

</script>

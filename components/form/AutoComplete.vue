<template>
    <div>
        <label class="form-label">{{ label }}</label>
        <a-auto-complete
            size="large"
            class="full-width"
            v-model:value="value"
            :options="options"
            :status="errorStatus"
            :placeholder="placeholder"
            :disabled="disabled"
            :filter-option="filterOption"
            @select="emit('select', value)"
        >
        </a-auto-complete>
        <FormErrorMessage :errorMessage="errorMessage"/>
    </div>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from 'vee-validate';

const props = defineProps<{
    label: string,
    formControlName: string,
    /**
     * The options for the dropdown menu.
     */
    options: FormMenuOption[],
    fieldValidationSchema?: TypedSchema,
    placeholder?: string,
    disabled?: boolean
}>();
const emit = defineEmits<{
    (e: "select", v: string): void
}>()

const { value, errorMessage } = useField<string>(() => props.formControlName, props.fieldValidationSchema);
const errorStatus = computed(() => errorMessage.value ? 'error' : '')

const filterOption = (input: string, option: FormMenuOption) => {
  return option.value.toUpperCase().indexOf(input.toUpperCase()) >= 0;
};

</script>

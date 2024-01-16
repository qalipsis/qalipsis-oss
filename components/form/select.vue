<template>
    <div>
        <label class="form-label">{{ label }}</label>
        <a-select
            size="large"
            class="full-width"
            v-model:value="value"
            :mode="mode"
            :options="options"
            :status="errorStatus"
            :placeholder="placeholder"
            :disabled="disabled"
            @change="emit('change', value)"
        >
        </a-select>
        <FormErrorMessage :errorMessage="errorMessage"/>
    </div>
</template>

<script setup lang="ts">
import { TypedSchema, useField } from 'vee-validate';

const props = defineProps<{
    label: string,
    formControlName: string,
    mode?: 'multiple' | 'tags',
    /**
     * The options for the dropdown menu.
     */
    options: FormMenuOption[],
    fieldValidationSchema?: TypedSchema,
    placeholder?: string,
    disabled?: boolean
}>();
const emit = defineEmits<{
    (e: "change", v: string): void
}>()

const { value, errorMessage } = useField<string>(() => props.formControlName, props.fieldValidationSchema);
const errorStatus = computed(() => errorMessage.value ? 'error' : '')

</script>

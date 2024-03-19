<template>
    <div>
        <label class="form-label">{{ label }}</label>
        <a-input
            size="large"
            class="full-width"
            v-model:value="value"
            :placeholder="placeholder"
            :status="errorStatus"
            :type="type ?? 'text'"
            :disabled="disabled"
            @input="handleInputChange"
        >
            <template v-if="suffix"  #suffix>
                <span>{{ suffix }}</span>
            </template>
        </a-input>
        <FormErrorMessage :errorMessage="errorMessage"/>
    </div>
</template>

<script setup lang="ts">
import { FormInputType } from 'utils/types/form';
import { TypedSchema, useField } from 'vee-validate';

const props = defineProps<{
    label: string,
    type?: FormInputType,
    formControlName: string,
    fieldValidationSchema?: TypedSchema,
    placeholder?: string,
    suffix?: string,
    disabled?: boolean
}>();
const emit = defineEmits<{
    (e: "input", v: string): void
}>()

const debouncedInputChange = debounce(() => {
    emit("input", value.value);
}, 300);

const { value, errorMessage } = useField<string>(() => props.formControlName, props.fieldValidationSchema);
const errorStatus = computed(() => errorMessage.value ? 'error' : '')
const handleInputChange = () => {
    debouncedInputChange();
}

</script>

<template>
  <div>
    <a-checkbox v-model:checked="value" @change="emit('change', value)" :disabled="disabled">
      {{ label }}
    </a-checkbox>
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import { TypedSchema, useField } from "vee-validate";

const props = defineProps<{
  label: string;
  formControlName: string;
  fieldValidationSchema?: TypedSchema;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (e: "change", v: boolean): void;
}>();

const { value, errorMessage } = useField<boolean>(
  () => props.formControlName,
  props.fieldValidationSchema
);
</script>

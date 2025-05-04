<template>
  <div class="flex items-center">
    <input
      type="checkbox"
      :id="formControlName"
      :class="[
        TailwindClassHelper.checkBoxClass,
        TailwindClassHelper.checkBoxMarkerClass
      ]"
      :checked="checkboxValue"
      v-model="checkboxValue"
      :disabled="disabled"
      @change="emit('change', checkboxValue)"
    />
    <label class="ml-2 cursor-pointer" :for="formControlName" >{{ label }}</label>
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import { type TypedSchema, useField } from "vee-validate";

const props = defineProps<{
  label: string;
  formControlName: string;
  fieldValidationSchema?: TypedSchema;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (e: "change", v: boolean): void;
}>();

const { value: checkboxValue, errorMessage } = useField<boolean>(
  () => props.formControlName,
  props.fieldValidationSchema
);

</script>

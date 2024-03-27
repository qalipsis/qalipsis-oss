<template>
  <div class="date-time-picker" :class="{ 'has-error': errorMessage }">
    <label class="form-label">{{ label }}</label>
    <VueDatePicker
      v-model="value"
      ref="dp"
      :format="format"
      :min-date="minDate"
      :clearable="false"
      @update:model-value="handleDate"
      time-picker-inline
    />
    <FormErrorMessage :errorMessage="errorMessage" />
  </div>
</template>

<script setup lang="ts">
import VueDatePicker from "@vuepic/vue-datepicker";
import "@vuepic/vue-datepicker/dist/main.css";
import { type TypedSchema, useField } from "vee-validate";

const props = defineProps<{
  label: string;
  formControlName: string;
  format: string;
  fieldValidationSchema?: TypedSchema;
  minDate?: Date;
}>();
const emit = defineEmits<{
  (e: "Change", v: Date): void;
}>();

const dp = ref();
const { value, errorMessage } = useField<string>(
  () => props.formControlName,
  props.fieldValidationSchema
);

const handleDateSelect = () => {
  dp.value.selectDate();
};

const handleDate = (modelData: Date) => {
  emit("Change", modelData);
};
</script>

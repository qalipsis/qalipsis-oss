<template>
  <div v-if="label" class="mb-4">
    <FormLabel :text="label" />
  </div>
  <div class="flex items-center">
    <div v-for="option in options" class="mr-3 last:mr-0">
        <FormRadioButton
            :value="option.value"
            :label="option.label"
            v-model="value"
            @update:modelValue="emit('change', value)"
        >
        </FormRadioButton>
    </div>
  </div>
  <FormErrorMessage :errorMessage="errorMessage" />
</template>

<script setup lang="ts">
import { useField } from "vee-validate";

const props = defineProps<{
  formControlName: string;
  /**
   * The options for the dropdown menu.
   */
  options: FormMenuOption[];
  label?: string;
  placeholder?: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (e: "change", v: string): void;
}>();

const { value, errorMessage } = useField<string>(() => props.formControlName);
</script>

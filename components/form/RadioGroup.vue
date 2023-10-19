<template>
  <label v-if="label" class="form-label mb-4">{{ label }}</label>
  <a-radio-group 
    v-model:value="value"
    :disabled="disabled"
    @change="emit('change', value)">
    <a-radio v-for="option in options" :value="option.value">
      <div v-if="$slots.customOption">
            <slot name="customOption"></slot>
      </div>
      <span v-else>
        {{ option.label }}
      </span>
    </a-radio>
  </a-radio-group>
  <FormErrorMessage :errorMessage="errorMessage"/>
</template>

<script setup lang="ts">
import { useField } from 'vee-validate';

const props = defineProps<{
  formControlName: string,
  /**
   * The options for the dropdown menu.
   */
  options: FormMenuOption[],
  label?: string,
    placeholder?: string,
    disabled?: boolean
}>();
const emit = defineEmits<{
  (e: "change", v: string): void
}>()

const { value, errorMessage } = useField<string>(() => props.formControlName);

</script>


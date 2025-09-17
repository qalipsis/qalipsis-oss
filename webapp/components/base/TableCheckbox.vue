<template>
  <div class="flex items-center">
    <input
        type="checkbox"
        :class="[
        TailwindClassHelper.checkBoxClass,
        TailwindClassHelper.checkBoxMarkerClass,
        indeterminate ? TailwindClassHelper.checkBoxIndeterminateClass : ''
      ]"
        :checked="isChecked"
        :value="value"
        :disabled="disabled"
        @change="handleChange"
    />
    <label v-if="label" class="ml-2">{{ label }}</label>
  </div>
</template>

<script setup lang="ts">
export interface CheckboxProps {
  value: string;
  modelValue: boolean | string | string[];
  disabled?: boolean;
  label?: string;
  trueValue?: boolean | string;
  falseValue?: boolean | string;
  indeterminate?: boolean
}

const props = withDefaults(defineProps<CheckboxProps>(), {
  trueValue: true,
  falseValue: false,
  disabled: false
});

const emits = defineEmits<{
  (e: "update:modelValue", v: boolean | string | string[]): void;
}>();

const isChecked = computed(() => {
  if (props.modelValue instanceof Array) {
    return props.modelValue.includes(props.value);
  }

  return props.modelValue === props.trueValue;
});

const handleChange = (event: Event) => {
  let isChecked = (event.target as HTMLInputElement).checked;

  if (props.modelValue instanceof Array) {
    let newValue = [...props.modelValue];

    if (isChecked) {
      newValue.push(props.value);
    } else {
      newValue.splice(newValue.indexOf(props.value), 1);
    }

    emits("update:modelValue", newValue);
  } else {
    emits("update:modelValue", isChecked ? props.trueValue : props.falseValue);
  }
};

defineExpose({disabled: props.disabled, checked: isChecked.value})

</script>

<template>
  <div class="flex flex-wrap">
    <div
      v-for="option in options"
      :key="option.label"
      class="w-7 h-7 rounded-full border solid border-primary-400 text-center flex items-center justify-center mr-1 mb-1 cursor-pointer"
      :class="{ 
        'text-primary-950 bg-white': !selectedValues?.includes(option.value),
        'bg-primary-500 text-white': selectedValues?.includes(option.value) }
      "
      @click="handleBtnClick(option.value)"
    >
      <span> {{ option.label }} </span>
    </div>
    <FormErrorMessage :errorMessage="errorMessage"/>
  </div>
</template>

<script setup lang="ts">
import { useField } from "vee-validate";

const props = defineProps<{
  formControlName: string;
  options: FormMenuOption[];
  label?: string;
  placeholder?: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (e: "change", v: string[]): void;
}>();

const { value: selectedValues, errorMessage } = useField<string[]>(() => props.formControlName);

const handleBtnClick = (selectedValue: string) => {
  if(!selectedValues.value.includes(selectedValue)){
      selectedValues.value.push(selectedValue);
  }else{
      selectedValues.value.splice(selectedValues.value.indexOf(selectedValue), 1);
  }
  emit("change", selectedValues.value)
};
</script>

<template>
  <div class="flex flex-wrap">
    <div
      v-for="option in options"
      :key="option.label"
      class="circle-check-box"
      :class="{ 'circle-check-box--active': selectedValues?.includes(option.value) }"
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

<style scoped lang="scss">
@import "../../assets/scss/_color.scss";

.circle-check-box {
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 50%;
  border: 1px solid $primary-color;
  color: $black;
  background-color: #ffffff;
  text-align: center;
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 0 .25rem .25rem 0;

  cursor: pointer;

  &--active {
    background-color: $primary-color;
    color: #ffffff;
  }
}
</style>

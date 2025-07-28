<template>
  <div
    class="flex items-center cursor-pointer"
    @click="handleOptionClick"
  >
    <input
      type="radio"
      :id="value"
      :class="[TailwindClassHelper.radioButtonClass, TailwindClassHelper.radioButtonActiveClass]"
      :value="value"
      :checked="isChecked"
    />
    <label
      v-if="label"
      class="pl-2 cursor-pointer"
      :for="value"
      >{{ label }}</label
    >
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  value: string
  modelValue?: string
  label?: string | number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()

const isChecked = computed(() => {
  return props.modelValue === props.value
})

const handleOptionClick = () => {
  emit('update:modelValue', props.value)
}
</script>

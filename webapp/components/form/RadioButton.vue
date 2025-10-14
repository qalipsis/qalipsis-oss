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
        :disabled="disabled"
    />
    <label
        v-if="label"
        class="pl-2"
        :for="value"
        :class="disabled ? 'cursor-not-allowed' : 'cursor-pointer'"
    >{{ label }}</label
    >
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  value: string
  modelValue?: string
  label?: string | number
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()

const isChecked = computed(() => {
  return props.modelValue === props.value
})

const handleOptionClick = () => {
  if (props.disabled) return

  emit('update:modelValue', props.value)
}
</script>

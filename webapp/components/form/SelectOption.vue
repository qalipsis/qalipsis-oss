<template>
  <div
    class="flex items-center mb-1 cursor-pointer"
    :class="[
      active ? 'bg-primary-50' : '',
      disabled ? TailwindClassHelper.formDropdownOptionDisabledClass : '',
      !disabled ? TailwindClassHelper.formDropdownOptionHoverClass : '',
      selected ? TailwindClassHelper.formDropdownOptionActiveClass : '',
      TailwindClassHelper.formDropdownOptionClass,
    ]"
  >
    {{ label }}
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  label: string
  active: boolean
  selected: boolean
  disabled: boolean
}>()
</script>

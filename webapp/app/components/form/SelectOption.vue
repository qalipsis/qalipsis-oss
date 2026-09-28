<template>
  <div
    class="flex items-center gap-x-2 mb-1 cursor-pointer"
    :class="[
      active ? 'bg-primary-50' : '',
      disabled ? TailwindClassConfig.formDropdownOptionDisabledClass : '',
      !disabled ? TailwindClassConfig.formDropdownOptionHoverClass : '',
      selected ? TailwindClassConfig.formDropdownOptionActiveClass : '',
      TailwindClassConfig.formDropdownOptionClass,
    ]"
  >
    <img
      v-if="option.imagePath"
      :src="option.imagePath"
      :alt="option.label"
      class="rounded-full bg-cover w-5 h-5 flex-shrink-0"
    />
    <div>
      <div>{{ option.label }}</div>
      <div
        v-if="option.description"
        class="text-sm text-gray-500 dark:text-gray-400"
      >
        {{ option.description }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  option: FormMenuOption
  active: boolean
  selected: boolean
}>()

const disabled = computed(() => props.option.disabled)
</script>

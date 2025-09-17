<template>
  <div class="mb-2">
    <label
        class="font-medium  text-base"
        :class="{
                'text-gray-700 dark:text-gray-300': !props.hasError,
                'text-red-700': props.hasError
            }"
    >{{ text }}</label>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  text: string,
  hasError?: boolean
}>();

</script>

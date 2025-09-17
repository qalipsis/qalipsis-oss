<template>
  <div
      class="shadow-md p-2 dark:bg-gray-900 rounded-lg"
      :class="{
            'bg-gray-50 dark:bg-gray-950': active
        }"
  >
    <slot></slot>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  active?: boolean
}>();
</script>

<template>
  <template v-if="canViewContent">
    <slot></slot>
  </template>
</template>

<script setup lang="ts">
const props = defineProps<{
  permissions: string[]
  requiredAll?: boolean
}>()
const userStore = useUserStore()

const canViewContent = computed(() => {
  if (props.requiredAll) {
    return props.permissions.every((permission) => userStore.permissions.includes(permission))
  }

  if (!props.permissions.length) return true

  return userStore.permissions.some((permission) => props.permissions.includes(permission))
})
</script>

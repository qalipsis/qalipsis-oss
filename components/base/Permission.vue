<template>
    <template v-if="canViewContent">
        <slot />
    </template>
</template>

<script setup lang="ts">
const props = defineProps<{
    permissions: typeof PermissionEnum[],
    requiredAll?: boolean
}>();
const userStore = useUserStore();

const canViewContent = computed(() => {
    if (props.requiredAll) {
        return userStore.permissions.every(permission => props.permissions.length ? props.permissions.includes(permission) : true);
    }

    return userStore.permissions.some(permission => {
        return props.permissions.length ? props.permissions.includes(permission) : true;
    });
});

</script>

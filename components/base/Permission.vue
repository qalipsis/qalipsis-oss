<template>
    <template v-if="canViewContent">
        <slot />
    </template>
</template>

<script setup lang="ts">
const props = defineProps<{
    permissions: PermissionEnum[],
    requiredAll?: boolean
}>();
const userStore = useUserStore();

const canViewContent = computed(() => {
    return props.requiredAll
        ? props.permissions.every(permission => userStore.permissions.some(permission))
        : userStore.permissions.some(permission => {
            if (props.permissions.length) return props.permissions.includes(permission);

            return true;
        });
});

</script>

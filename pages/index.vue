<template>
    <div>
        <BaseButton text="test123" :btn-style="'default'" :icon="'/icons/icon-plus-grey.svg'" @click="modalOpen = true" />
        <BaseButton text="test123" :btn-style="'stroke'" :icon="'/icons/icon-plus-grey.svg'" @click="drawerOpen = true"/>
        <BaseModal v-model:open="modalOpen" :title="'test123'" :closable="true">
            <template #customFooter>
                I am custom footer
            </template>
        </BaseModal>
        <BaseDrawer v-model:open="drawerOpen" :title="'test123'" />

    </div>
</template>

<script setup lang="ts">

const modalOpen = ref(false)
const drawerOpen = ref(false)

</script>

<style scoped></style>
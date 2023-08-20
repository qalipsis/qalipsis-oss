<template>
    <a-layout-sider
        v-model:collapsed="collapsed"
        collapsible
        :trigger="null"
        theme="light"
        width="240"
        collapsed-width="80"
        class="sidebar-border sidebar-layout"
        @collapse="toggleSidebar">
        <div 
            class="sidebar-custom-collapse-trigger-btn"
            :class="{ 'sidebar-custom-collapse-trigger-btn--collapsed': collapsed }"
            @click="() => (collapsed = !collapsed)">
            <BaseIcon :icon="collapsedIcon" />
        </div>
        <nav class="sidebar-container">
            <SidebarMenu
                :menu-items="menuItems"
                :collapsed="collapsed"
            />
        </nav>
    </a-layout-sider>
</template>

<script setup lang="ts">
const menuItems = SidebarHelper.getMenuItems();
const collapsed = ref(false);

const collapsedIcon = computed(() => {
    return collapsed.value ? "/icons/icon-arrow-right-light-black.svg" : "/icons/icon-arrow-left-light-black.svg";
})

const toggleSidebar = () => {
    collapsed.value = !collapsed.value
}

</script>

<style lang="scss" scoped>
@import "../../assets/scss/color";

.sidebar-border {
    border-right: 1px solid $grey-3;
}

.sidebar-custom-collapse-trigger-btn  {
    position: absolute;
    top: 0;
    width: 2rem;
    height: 2rem;
    top: 2rem;
    left: 14rem;
    display: flex;
    position: absolute;
    cursor: pointer;
    align-items: center;
    justify-content: center;
    line-height: normal;
    border: 1px solid $grey-3;
    border-radius: 50%;
    z-index: 1;
    background: white;

    &--collapsed {
        left: 4rem;
    }
}

.sidebar-container {
    height: 100vh;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
}
</style>
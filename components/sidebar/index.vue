<template>
    <a-layout-sider
        v-model:collapsed="collapsed"
        collapsible
        :trigger="null"
        theme="light"
        width="240"
        collapsed-width="80"
        class="border-r border-solid border-gray-100"
        @collapse="toggleSidebar">
        <div 
            class="absolute top-10 w-8 h-8 flex items-center justify-center cursor-pointer border border-solid border-gray-100 rounded-full z-10 bg-white"
            :class="{
                'left-56': !collapsed,
                'left-16': collapsed
            }"
            @click="() => (collapsed = !collapsed)">
            <BaseIcon :icon="collapsedIcon" />
        </div>
        <nav class="h-screen flex flex-col justify-between">
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

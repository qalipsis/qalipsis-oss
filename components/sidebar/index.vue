<template>
    <div 
        class="flex-grow-0 flex-shrink-0 relative border-r border-solid border-gray-100 transition-all duration-200"
        :class="{
            'basis-60': !collapsed,
            'basis-20': collapsed
        }"
    >
        <div 
            class="absolute top-10 w-8 h-8 flex items-center justify-center cursor-pointer border border-solid border-gray-100 rounded-full bg-white"
            :class="{
                'left-56': !collapsed,
                'left-16': collapsed
            }"
            @click="collapsed = !collapsed">
            <BaseIcon :icon="collapsedIcon" class="text-lg" />
        </div>
        <nav class="h-screen flex flex-col justify-between">
            <SidebarMenuItems
                :menu-items="menuItems"
                :collapsed="collapsed"
            />
        </nav>
    </div>
</template>

<script setup lang="ts">
const menuItems = SidebarHelper.getMenuItems();
const collapsed = ref(false);

const collapsedIcon = computed(() => {
    return collapsed.value ? "qls-icon-arrow-right" : "qls-icon-arrow-left";
})

</script>

<template>
    <section class="sidebar-section text-primary-950">
        <div 
            class="flex items-center pl-3 h-28 cursor-pointer w-full" 
            @click="handleMenuItemClick('', '')"
        >
            <div class="w-12 h-12 flex items-center pl-2">
                <BaseIcon icon="/icons/icon-logo.svg"/>
            </div>
            <div v-if="!collapsed" class="px-3 text-2xl font-semibold">
                QALIPSIS
            </div>
        </div>
        <a-menu>
            <div 
                v-for="menuItem in menuItems"
                class="flex items-center relative text-primary-950"
                :key="menuItem.path"
            >
                <BasePermission :permissions="menuItem.permissions">
                    <div 
                        class="absolute h-14 w-2 rounded-md bg-primary-500"
                        :class="{
                            'invisible': activeMenuItemId !== menuItem.id,
                            'visible': activeMenuItemId === menuItem.id
                        }">
                    </div>
                    <a-menu-item v-if="!menuItem.subMenuItems" @click="handleMenuItemClick(menuItem.id, menuItem.path)" :key="menuItem.path">
                        <div 
                            class="flex items-center pl-3 w-full h-16"
                            :class="activeMenuItemId === menuItem.id ? TailwindClassHelper.primaryColorFilterClass : ''">
                            <div class="flex items-center w-12 h-12 justify-center flex-shrink-0">
                                <BaseIcon 
                                    class="w-7 h-7"
                                    :icon="menuItem.icon"
                                />
                            </div>
                            <span class="px-3 text-base">
                                {{ menuItem.text }}
                            </span>
                        </div>
                    </a-menu-item>
                </BasePermission>
                <a-sub-menu v-if="menuItem.subMenuItems && menuItem.subMenuItems.length > 0">
                    <template #title>
                        <div 
                            class="flex items-center pl-3 w-full h-16"
                            :class="activeMenuItemId === menuItem.id ? TailwindClassHelper.primaryColorFilterClass : ''"
                        >
                            <div class="flex items-center w-12 h-12 justify-center flex-shrink-0">
                                <BaseIcon 
                                    class="w-7 h-7"
                                    :icon="menuItem.icon"
                                />
                            </div>
                            <span class="px-3 text-base">
                                {{ menuItem.text }}
                            </span>
                        </div>
                    </template>
                    <template v-for="subMenuItem in menuItem.subMenuItems" :key="subMenuItem.path">
                        <BasePermission :permissions="subMenuItem.permissions">
                            <a-menu-item 
                                class="h-fit"
                                @click="handleMenuItemClick(menuItem.id, subMenuItem.path)"
                                :key="subMenuItem.path"
                            >
                                <div class="flex items-center pl-3 w-full h-10">
                                    <span class="px-3 text-base">
                                        {{ subMenuItem.text }}
                                    </span>
                                </div>
                            </a-menu-item>
                        </BasePermission>
                    </template>
                </a-sub-menu>
            </div>
        </a-menu>
    </section>
</template>

<script setup lang="ts">

const router = useRouter();

const props = defineProps<{
    /**
     * The menu items to be displayed
     */
    menuItems: SidebarMenuItem[];

    /**
     * A flag to indicate if the sidebar is collapsed.
     */
    collapsed: boolean
}>();

const activeMenuItemId = ref('');

onMounted(() => {
    const activeMenuItem = props.menuItems.find(menuItem => router.currentRoute.value.fullPath.includes(menuItem.path));

    if (activeMenuItem) {
        activeMenuItemId.value = activeMenuItem.id;
    }
})

/**
 * Navigates to the page when clicking the button.
 *  
 * @param path The path url to be navigated
 */
const handleMenuItemClick = (menuItemId: string, path: string) => {
    activeMenuItemId.value = menuItemId;
    navigateTo(`/${path}`);
}

</script>

<style scoped lang="scss">
/** Overrides the ant-design menu styles */
.sidebar-section {
    :deep(.ant-menu-vertical .ant-menu-item),
    :deep(.ant-menu-vertical .ant-menu-submenu-title) {
        height: 3.75rem;
        padding-inline: 0;
    }

    :deep(.ant-menu-light.ant-menu-root.ant-menu-vertical) {
        border-inline-end: none;
    }

    :deep(.ant-menu-vertical .ant-menu-submenu) {
        width: 100%;
    }

    :deep(.ant-menu-light .ant-menu-item:not(.ant-menu-item-disabled):focus-visible) {
        outline: none;
    }

    :deep(.ant-menu-light .ant-menu-item-selected) {
        background-color: inherit;
        color: inherit;
    }

    :deep(.ant-menu .ant-menu-submenu-arrow ) {
        display: none;
    }

}

</style>
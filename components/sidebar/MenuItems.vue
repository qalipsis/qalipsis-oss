<template>
    <section class="text-primary-950">
        <div 
            class="flex items-center pl-3 h-28 cursor-pointer w-full"
            @mouseenter="hoveredMenuItemId = ''"
            @click="handleMenuItemClick('', '')"
        >
            <div class="w-12 h-12 flex items-center pl-2">
                <BaseIcon icon="/icons/icon-logo.svg"/>
            </div>
            <div 
                v-if="!collapsed"
                class="px-3 text-2xl font-semibold"
            >
                QALIPSIS
            </div>
        </div>
        <ul class="relative">
            <template v-for="menuItem in menuItems">
                <div 
                    class="absolute h-16 w-2 rounded-md bg-primary-500 z-10"
                    :class="{
                        'invisible': activeMenuItemId !== menuItem.id,
                        'visible': activeMenuItemId === menuItem.id
                    }">
                </div>
                <BasePermission :permissions="menuItem.permissions">
                    <li 
                        class="relative"
                        @mouseenter="hoveredMenuItemId = menuItem.id"
                    >
                        <div 
                            class="peer relative hover:bg-primary-50"
                            @click="!menuItem.subMenuItems && handleMenuItemClick(menuItem.id, menuItem.path)"
                        >
                            <div
                                class="flex items-center h-16 cursor-pointer pl-4 text-primary-950"
                                :class="[
                                    TailwindClassHelper.primaryColorFilterHoverClass,
                                    activeMenuItemId === menuItem.id
                                        ? TailwindClassHelper.primaryColorFilterClass
                                        : ''
                                ]"
                            >
                                <div class="flex items-center w-12 h-12 justify-center flex-shrink-0">
                                    <BaseIcon 
                                        class="w-7 h-7"
                                        :icon="menuItem.icon"
                                    />
                                </div>
                                <div v-if="!collapsed" class="pl-3">
                                    {{ menuItem.text }}
                                </div>
                            </div>
                        </div>
                        <div 
                            v-if="collapsed && !menuItem.subMenuItems"
                            class="hidden absolute top-2 h-12 peer-hover:flex items-center px-4 w-fit left-20 z-20 text-primary-50 bg-primary-950/80 shadow-xl rounded-lg"
                        >
                            {{ menuItem.text }}
                        </div>
                        <ul
                            v-if="menuItem.subMenuItems"
                            @mouseleave="hoveredMenuItemId = ''"
                            class="absolute top-0 shadow-xl rounded-lg ml-1 bg-white z-10"
                            :class="{
                                'left-60': !collapsed,
                                'left-20': collapsed,
                                'invisible': hoveredMenuItemId !== menuItem.id,
                                'visible': hoveredMenuItemId === menuItem.id
                            }"
                        >
                            <template v-for="subMenuItem in menuItem.subMenuItems">
                                <BasePermission :permissions="subMenuItem.permissions">
                                    <li 
                                        class="h-14 flex items-center justify-center cursor-pointer px-4 hover:text-primary-500 hover:bg-primary-50"
                                        :class="{
                                            'bg-primary-50 text-primary-500': subMenuItem.id === activeSubMenuItemId                 
                                        }"
                                        @click="handleSubMenuItemClick(menuItem.id, subMenuItem.id, subMenuItem.path)"
                                    >
                                        <div class="min-w-32">
                                            {{ subMenuItem.text }}
                                        </div>
                                    </li>
                                </BasePermission>
                            </template>
                        </ul>
                    </li>
                </BasePermission>
            </template>
        </ul>
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
const activeSubMenuItemId = ref('');
const hoveredMenuItemId = ref('');

onMounted(() => {
    const activeMenuItem = props.menuItems.find(menuItem => {
        const routePath = router.currentRoute.value.fullPath
        return routePath.includes(menuItem.path)
            || menuItem.subMenuItems?.some(subMenuItem => routePath.includes(subMenuItem.path))
    });

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
    activeSubMenuItemId.value = '';
    navigateTo(`/${path}`);
}

const handleSubMenuItemClick = (menuItemId: string, subMenuItemId: string, path: string) => {
    activeMenuItemId.value = menuItemId;
    activeSubMenuItemId.value = subMenuItemId;
    // Sets the hover item id to be empty to hide the sub menu.
    hoveredMenuItemId.value = '';
    navigateTo(`/${path}`);
}

</script>

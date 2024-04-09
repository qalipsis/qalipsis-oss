<template>
    <section class="sidebar-section text-primary-950">
        <div 
            class="flex items-center pl-3 h-28 cursor-pointer w-full" 
            @click="handleMenuItemClick('')"
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
                :class="{ 'menu-container--active': menuItem.path === activePath || menuItem.subMenuItems?.some(o => o.path === activePath) }">
                <BasePermission :permissions="menuItem.permissions">
                    <div class="item-indicator"></div>
                    <a-menu-item v-if="!menuItem.subMenuItems" @click="handleMenuItemClick(menuItem.path)" :key="menuItem.path">
                        <div class="flex items-center pl-3 w-full h-16">
                            <div class="flex items-center w-12 h-12 justify-center flex-shrink-0">
                                <BaseIcon class="w-7 h-7" :icon="menuItem.icon" />
                            </div>
                            <span class="px-3 text-base">
                                {{ menuItem.text }}
                            </span>
                        </div>
                    </a-menu-item>
                </BasePermission>
                <a-sub-menu v-if="menuItem.subMenuItems && menuItem.subMenuItems.length > 0">
                    <template #title>
                        <div class="flex items-center pl-3 w-full h-16">
                            <div class="flex items-center w-12 h-12 justify-center flex-shrink-0">
                                <BaseIcon class="w-7 h-7" :icon="menuItem.icon" />
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
                                @click="handleSubMenuItemClick(menuItem.path, subMenuItem.path)"
                            >
                                <div class="flex items-center pl-3 w-full h-16">
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

defineProps<{
    /**
     * The menu items to be displayed
     */
    menuItems: SidebarMenuItem[];

    /**
     * A flag to indicate if the sidebar is collapsed.
     */
    collapsed: boolean
}>();

const activePath = computed(() => router.currentRoute.value.name);

const activeMenuItemPath = ref('campaigns');
const activeSubMenuItemPath = ref('');

/**
 * Navigates to the page when clicking the button.
 *  
 * @param path The path url to be navigated
 */
const handleMenuItemClick = (path: string) => {
    activeMenuItemPath.value = path;
    navigateTo(`/${path}`);
}

const handleSubMenuItemClick = (menuItemPath: string, subMenuItemPath: string) => {
    activeMenuItemPath.value = menuItemPath;
    activeSubMenuItemPath.value = subMenuItemPath;
    navigateTo(`/${subMenuItemPath}`);
}

</script>

<style scoped lang="scss">
@import "../../assets/scss/color";

$menu-item-height: 3.75rem;

@mixin flexLayout {
    display: flex;
    align-items: center;
}
@mixin highlight {
    .icon-wrapper img {
        filter: $primary-color-svg;
    }
}
@mixin menu-padding {
    padding-left: .75rem;
}

/** Overrides the ant-design menu styles */
.sidebar-section {
    :deep(.ant-menu-vertical .ant-menu-item),
    :deep(.ant-menu-vertical .ant-menu-submenu-title) {
        height: $menu-item-height;
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

    :deep(.ant-menu-light .ant-menu-submenu-selected >.ant-menu-submenu-title) {
        @include highlight;
        color: $primary-color;
    }
}

:global(.ant-menu-light .ant-menu-item-selected) {
    background-color: transparent;
    color: $primary-color;
}

.item-indicator {
    position: absolute;
    height: $menu-item-height;
    width: .5rem;
    border-radius: 5px;
    background-color: $primary-color;
    visibility: hidden;
}

.menu-container--active {
    @include highlight;
    .item-indicator {
        visibility: visible;
    }
}

</style>
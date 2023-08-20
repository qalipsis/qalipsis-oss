<template>
    <section class="sidebar-section">
        <div class="brand-container" @click="handleMenuItemClick('')">
            <div class="icon-wrapper">
                <BaseIcon icon="/icons/icon-logo.svg" />
            </div>
            <div v-if="!collapsed" class="text-wrapper text-2xl text-bold">
                QALIPSIS
            </div>
        </div>
        <a-menu>
            <div 
                v-for="menuItem in menuItems"
                class="menu-container"
                :key="menuItem.path"
                :class="{ 'menu-container--active': menuItem.path === activePath || menuItem.subOptions?.some(o => o.path === activePath) }">
                <div class="item-indicator"></div>
                <a-menu-item v-if="!menuItem.subOptions" @click="handleMenuItemClick(menuItem.path)" :key="menuItem.path">
                    <div class="option-container">
                        <div class="icon-wrapper">
                            <BaseIcon :icon="menuItem.icon" />
                        </div>
                        <span class="text-wrapper">
                            {{ menuItem.text }}
                        </span>
                    </div>
                </a-menu-item>
                <a-sub-menu v-if="menuItem.subOptions && menuItem.subOptions.length > 0">
                    <template #title>
                        <div class="option-container">
                            <div class="icon-wrapper">
                                <BaseIcon :icon="menuItem.icon" />
                            </div>
                            <span class="text-wrapper">
                                {{ menuItem.text }}
                            </span>
                        </div>
                    </template>
                    <a-menu-item v-for="subOption in menuItem.subOptions" :key="subOption.path"
                        @click="handleMenuItemClick(subOption.path)" style="height: fit-content;">
                        <div class="option-container">
                            <span class="text-wrapper">
                                {{ subOption.text }}
                            </span>
                        </div>
                    </a-menu-item>
                </a-sub-menu>
            </div>
        </a-menu>
    </section>
</template>

<script setup lang="ts">
import { SidebarMenuItem } from 'utils/sidebar.helper';

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

const activePath = ref(router.currentRoute.value.name?.toString() ?? '');

/**
 * Navigates to the page when clicking the button.
 *  
 * @param path The path url to be navigated
 */
const handleMenuItemClick = (path: string) => {
    activePath.value = path;
    navigateTo(path);
}

</script>

<style scoped lang="scss">
@import "../../assets/scss/color";
@import "../../assets/scss/variables";

$menu-item-height: 3.75rem;

@mixin flexLayout {
    display: flex;
    align-items: center;
}
@mixin highlight {
    span.text-wrapper {
        color: $primary-color !important;
    }

    .icon-wrapper img {
        filter: $primary-color-svg;
    }
}
@mixin menu-padding {
    padding-left: .75rem;
}

/** Overrides the ant-design menu styles */
.sidebar-section {

    :deep(.ant-menu-vertical .ant-menu-item, ),
    :deep(.ant-menu-vertical .ant-menu-submenu-title) {
        height: $menu-item-height;
        padding-inline: 0;
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

.brand-container {
    @include flexLayout;
    @include menu-padding;
    height: $header-height;
    cursor: pointer;
    width: 100%;

    .icon-wrapper {
        img {
            width: 3rem;
            height: 3rem
        }
    }
}

.menu-container {
    @include flexLayout;
    position: relative;

    &--active {
        @include highlight;
        .item-indicator {
            visibility: visible;
        }
    }

}

.option-container {
    @include flexLayout;
    @include menu-padding;
    width: 100%;
    height: $menu-item-height;

    .text-wrapper {
        font-size: 1rem;
    }
}

.icon-wrapper {
    @include flexLayout;
    width: 3rem;
    height: 3rem;
    justify-content: center;
    flex-shrink: 0;

    img {
        width: 1.75rem;
        height: 1.75rem;
    }
}

.text-wrapper {
    padding: 0 .75rem;
}
</style>
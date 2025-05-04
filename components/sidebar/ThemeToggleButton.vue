<template>
    <div class="h-14 flex items-center pl-4">
        <div class="relative inline-block w-11 h-6">
            <input
                id="theme-switcher" 
                :checked="darkThemeEnabled"
                v-model="darkThemeEnabled"
                type="checkbox"
                class="
                    peer appearance-none w-11 h-6 rounded-full 
                    bg-primary-200 checked:bg-gray-600 cursor-pointer
                    transition-colors duration-300
                "
                @change="toggleTheme"
            />
            <label 
                for="theme-switcher"
                class="
                    absolute top-0 left-0 w-6 h-6 flex items-center justify-center
                    bg-white rounded-full border border-gray-100 shadow-sm
                    transition-transform duration-300 peer-checked:translate-x-6
                    peer-checked:border-gray-800 cursor-pointer
                "
            >
                <BaseIcon v-if="!darkThemeEnabled" icon="qls-icon-sun" class="text-base text-primary-500"></BaseIcon>
                <BaseIcon v-if="darkThemeEnabled" icon="qls-icon-moon" class="text-base text-gray-700"></BaseIcon>
            </label>
        </div>
        <label v-if="!collapsed" for="theme-switcher" class="pl-4 hover:text-primary-400 cursor-pointer">
            {{ themeText }}
        </label>
    </div>
</template>

<script setup lang="ts">
defineProps<{
    /**
     * A flag to indicate if the sidebar is collapsed.
     */
    collapsed: boolean
}>();


const themeStore = useThemeStore();
const darkThemeEnabled = ref(themeStore.theme === 'dark');
const themeText = computed(() => themeStore.theme === 'dark' ? 'Dark mode' : 'Light mode')

const toggleTheme = () => {
    const themeValue = darkThemeEnabled.value ? 'dark' : 'light';
    themeStore.$patch({
        theme: themeValue
    });

    localStorage.setItem(ThemeConfig.THEME_KEY, themeValue);
}

</script>

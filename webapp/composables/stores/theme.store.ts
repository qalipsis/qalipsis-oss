type Theme = 'light' | 'dark';

interface ThemeStore {
    theme: Theme
}

export const useThemeStore = defineStore("themeStore", {
    state: (): ThemeStore => {
        return {
            theme: localStorage.getItem(ThemeConfig.THEME_KEY)
                ? localStorage.getItem(ThemeConfig.THEME_KEY) as Theme
                : 'light',
        };
    }
});
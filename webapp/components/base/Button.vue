<template>
  <button
    type="button"
    class="h-10 px-3 py-2 text-base rounded-md min-w-32 flex items-center justify-center disabled:bg-gray-50 dark:disabled:bg-gray-600 disabled:text-gray-400 disabled:cursor-not-allowed"
    :class="[btnBaseClass, btnBgThemeClass]"
    :disabled="disabled"
    @click="emit('click')"
  >
    <BaseIcon
      v-if="icon"
      class="pr-2"
      :class="btnTextClass"
      :icon="icon"
    >
    </BaseIcon>
    <span :class="btnTextClass">
      {{ text }}
    </span>
  </button>
</template>

<script setup lang="ts">
type ThemeType = 'primary' | 'success' | 'error' | 'warning' | 'info'
const filledBtnBaseClass = 'group'
const filledBtnBaseTextClass = 'group-enabled:text-white'
const outlinedBtnBaseClass =
  'border border-solid border-gray-300 dark:border-gray-600 enabled:text-gray-800 dark:enabled:text-gray-100 dark:enabled:hover:bg-gray-800 group'

const outlinedBtnBgThemeClass: { [theme in ThemeType]: string } = {
  primary: 'enabled:hover:border-primary-500 enabled:hover:bg-primary-50',
  success: 'enabled:hover:border-green-500 enabled:hover:bg-green-50',
  error: 'enabled:hover:border-red-500 enabled:hover:bg-red-50',
  warning: 'enabled:hover:border-yellow-500 enabled:hover:bg-yellow-50',
  info: 'enabled:hover:border-purple-500 enabled:hover:bg-purple-50',
}
const outlineButtonTextThemeClass: { [theme in ThemeType]: string } = {
  primary: 'group-enabled:group-hover:text-primary-500',
  success: 'group-enabled:group-hover:text-green-500',
  error: 'group-enabled:group-hover:text-red-500',
  warning: 'group-enabled:group-hover:text-yellow-500',
  info: 'group-enabled:group-hover:text-purple-500',
}
const filledButtonBgThemeClass: { [theme in ThemeType]: string } = {
  primary: 'enabled:bg-primary-500 enabled:hover:bg-primary-400',
  success: 'enabled:bg-green-500 enabled:hover:bg-green-400',
  error: 'enabled:bg-red-500 enabled:hover:bg-red-400',
  warning: 'enabled:bg-yellow-500 enabled:hover:bg-yellow-400',
  info: 'enabled:bg-purple-500 enabled:hover:bg-purple-400',
}

const props = defineProps<{
  text: string
  theme?: 'primary' | 'success' | 'error' | 'warning' | 'info'
  btnStyle?: 'outlined' | 'filled'
  icon?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'click'): void
}>()
const btnBaseClass = computed(() => _getBtnBaseClass())
const btnBgThemeClass = computed(() => _getBtnBgThemeClass())
const btnTextClass = computed(() => _getBtnTextClass())

const _getBtnBgThemeClass = (): string => {
  const theme = _getBtnTheme()
  switch (props.btnStyle) {
    case 'outlined':
      return outlinedBtnBgThemeClass[theme]  
    case 'filled':
      return filledButtonBgThemeClass[theme]
    default:
      return filledButtonBgThemeClass[theme]
  }
}

const _getBtnTextClass = (): string => {
  switch (props.btnStyle) {
    case 'outlined':
      const theme = _getBtnTheme()
      return outlineButtonTextThemeClass[theme]  
    case 'filled':
      return filledBtnBaseTextClass
    default:
      return filledBtnBaseTextClass
  }
}

const _getBtnTheme = (): ThemeType => {
  return props.theme ?? 'primary'
}

const _getBtnBaseClass = (): string => {
  switch (props.btnStyle) {
    case 'outlined':
      return outlinedBtnBaseClass  
    case 'filled':
      return filledBtnBaseClass
    default:
      return filledBtnBaseClass
  }
}




</script>

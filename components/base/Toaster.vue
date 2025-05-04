<template>
  <teleport to="body">
    <ul
      v-if="toasts.length"
      class="fixed z-30 bottom-2 right-2"
      :class="{ 'dark': darkThemeEnabled }"
    >
      <li v-for="toast in toasts" :key="toast.text">
        <transition
          appear
          enter-active-class="transition-all ease-in-out duration-200"
          leave-active-class="transition-all ease-in-out duration-200"
          enter-from-class="translate-x-full"
          leave-to-class="translate-x-full"
        >
          <div
            class="relative flex items-center p-4 w-80 rounded-md shadow-2xl translate-none"
            :class="ToastConfig.toastStatusToTailwindClass[toast.status]"
          >
            <BaseIcon
              :icon="ToastConfig.toastStatusToIcon[toast.status]"
              :class="ToastConfig.toastStatusIconToTailwindClass[toast.status]"
              class="pr-4 flex-shrink-0"
            ></BaseIcon>
            <span>
              {{ toast.text }}
            </span>
            <div class="absolute top-2 right-2 cursor-pointer" @click="handleCloseButtonClick(toast.id)">
              <BaseIcon
                icon="qls-icon-close"
                class="text-base text-gray-700 hover:text-gray-900 dark:text-white dark:hover:text-gray-200"
              >
              </BaseIcon>
            </div>
          </div>
        </transition>

      </li>
    </ul>
  </teleport>
</template>

<script setup lang="ts">
defineProps<{
  darkThemeEnabled?: boolean
}>()


const toastStore = useToastStore();
const { toasts } = storeToRefs(toastStore);

const handleCloseButtonClick = (toastId: number) => {
  toasts.value = toasts.value.filter(toast => toast.id !== toastId);
}

</script>


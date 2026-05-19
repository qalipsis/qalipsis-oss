<template>
  <button
    class="w-8 h-8 rounded-md flex items-center justify-center enabled:hover:bg-gray-100 enabled:dark:hover:bg-gray-800"
    :disabled="disabled"
    @click="$emit('click')"
  >
    <div class="flex items-center">
      <div
        v-for="i in arrowCount"
        :key="i"
        class="border-r-2 border-b-2 border-solid p-1"
        :class="[
          rotation,
          i === 2 ? '-ml-0.5' : '',
          disabled ? 'border-gray-400 dark:border-gray-800' : 'border-gray-900 dark:border-gray-400',
        ]"
      ></div>
    </div>
  </button>
</template>

<script setup lang="ts">
const props = defineProps<{
  type: 'first' | 'prev' | 'next' | 'last'
  disabled?: boolean
}>()
defineEmits(['click'])

const arrowCount = computed(() => (props.type === 'first' || props.type === 'last' ? 2 : 1))

const rotation = computed(() => (props.type === 'next' || props.type === 'last' ? '-rotate-45' : 'rotate-[135deg]'))
</script>

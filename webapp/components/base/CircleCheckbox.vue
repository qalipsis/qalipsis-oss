<template>
  <div
      @click="handleBtnClick"
      :class="[active ? 'text-white bg-primary-500' : 'text-primary-900', TailwindClassHelper.circleCheckBoxBaseClass]"
  >
    <span>{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  text: string;
  active: boolean;
}>();
const emit = defineEmits<{
  (e: "update:active", v: boolean): void;
}>();

const handleBtnClick = () => {
  emit("update:active", !props.active);
};
</script>

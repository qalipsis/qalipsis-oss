<template>
  <div
    @click="handleBtnClick"
    class="circle-check-box"
    :class="{ 'circle-check-box--active': active }"
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

<style scoped lang="scss">
@import "../../assets/scss/_color.scss";

.circle-check-box {
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 50%;
  border: 1px solid $primary-color;
  color: $black;
  background-color: #ffffff;
  text-align: center;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;

  &--active {
    background-color: $primary-color;
    color: #ffffff;
  }
}
</style>

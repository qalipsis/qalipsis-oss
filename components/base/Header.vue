<template>
    <header class="header">
        <slot />
    </header>
</template>

<script setup lang="ts">

</script>

<style scoped lang="scss">
@import "../../assets/scss/variables";

header.header {
    display: flex;
    align-items: center;
    width: 100%;
    height: $header-height;
    padding: 0 $content-padding;
}


</style>
<template>
    <footer class="footer">
        <slot />
    </footer>
</template>

<script setup lang="ts">

</script>

<style scoped lang="scss">
@import "../../assets/scss/variables";
@import "../../assets/scss/color";

footer.footer {
    width: 100%;
    display: flex;
    align-items: center;
    height: $footer-height;
    padding: 0 $content-padding;
}


</style>
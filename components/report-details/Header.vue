<template>
    <BaseHeader>
        <div class="flex space-between items-center full-width">
            <div class="flex items-center">
                <BaseIcon icon="/icons/icon-arrow-left-black.svg" class="cursor-pointer icon-link pr-2" @click="navigateTo('/reports')" />
                <!-- <BaseTitle v-model:content="reportName" :editable="true" /> -->
            </div>
            <div class="flex items-center">
                <BaseButton
                    class="ml-2"
                    text="Save"
                    @click="handleSaveReportBtnClick"
                />
            </div>
        </div>
    </BaseHeader>
</template>

<script setup lang="ts">
const handleSaveReportBtnClick = () => {
    
}

</script>

<style scoped>

</style>
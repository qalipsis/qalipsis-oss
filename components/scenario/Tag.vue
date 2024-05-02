<template>
    <a-tooltip>
        <template v-if="failureReason" #title>
            {{ failureReason }}                    
        </template>
        <div class="cursor-pointer">
            <BaseTag 
                :text="tag.text"
                :backgroundCssClass="tag.backgroundCssClass"
                :textCssClass="tag.textCssClass"
                @click="emit('click')"
            />
        </div>
    </a-tooltip>
</template>

<script setup lang="ts">

const props = defineProps<{
    status: ExecutionStatus,
    failureReason?: string
}>()
const emit = defineEmits<{
    (e: 'click'): void
}>()
const tag = computed(() => CampaignHelper.toExecutionStatusTag(props.status)); 

</script>

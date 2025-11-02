<template>
  <BaseModal
    title="Abort campaign"
    confirmBtnText="Abort"
    :open="open"
    :closable="true"
    @close="emits('update:open', false)"
  >
    <span>Are you sure you want to cancel this scheduled campaign? This will cancel all future executions.</span>
    <template #customFooter>
      <div class="flex items-center justify-around">
        <BaseButton
          btn-style="outlined"
          theme="error"
          text="Cancel"
          @click="emits('update:open', false)"
        />
        <BaseButton
          text="Abort"
          theme="error"
          @click="handleConfirmAbortBtnClick"
        />
      </div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
const props = defineProps<{
  open: boolean;
  campaignKey: string;
  campaignName: string;
}>()
const emits = defineEmits<{
  (e: 'update:open', v: boolean): void,
  (e: 'aborted'): void
}>()

const toastStore = useToastStore();
const { abortCampaign } = useCampaignApi()

const handleConfirmAbortBtnClick = async () => {
  try {
    await abortCampaign(props.campaignKey, true)
    emits('aborted')
    emits('update:open', false)
    toastStore.success({
      text: `The scheduled campaign "${props.campaignName}" has been successfully aborted`,
    })
  } catch (error) {
    toastStore.error({text: ErrorHelper.getErrorMessage(error)})
  }
}
</script>


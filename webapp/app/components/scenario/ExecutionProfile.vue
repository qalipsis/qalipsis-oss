<template>
  <div class="grid grid-cols-12 gap-2 mb-2">
    <div class="col-span-4">
      <FormInput
        label="Minions"
        suffix="qty"
        :form-control-name="`executionProfileStages[${index}].minionsCount`"
        :field-validation-schema="executionProfileSchema.minionsCount"
        :disabled="disabled"
      />
    </div>
    <div class="col-span-4">
      <div class="flex items-center">
        <div class="flex-grow">
          <FormInputSelect
            label="Ramp Up"
            :form-input-control-name="`executionProfileStages[${index}].rampUpDuration`"
            :form-select-control-name="`executionProfileStages[${index}].rampUpDurationUnit`"
            :input-field-validation-schema="executionProfileSchema.rampUpDuration"
            :select-field-validation-schema="executionProfileSchema.rampUpDurationUnit"
            :options="timeframeUnitOptions"
            :input-disabled="disabled"
            :select-disabled="disabled"
          />
        </div>
      </div>
    </div>
    <div class="col-span-4">
      <div class="flex items-center">
        <div class="flex-grow">
          <FormInputSelect
            label="Duration"
            :form-input-control-name="`executionProfileStages[${index}].duration`"
            :form-select-control-name="`executionProfileStages[${index}].durationUnit`"
            :input-field-validation-schema="executionProfileSchema.duration"
            :select-field-validation-schema="executionProfileSchema.durationUnit"
            :options="timeframeUnitOptions"
            :input-disabled="disabled"
            :select-disabled="disabled"
          />
        </div>
        <div
          v-if="!deleteHidden && !disabled"
          class="flex-shrink-0 flex items-center pt-8 px-2 cursor-pointer hover:text-primary-500 text-gray-600"
          @click="handleDeleteBtnClick"
        >
          <BaseIcon
            class="text-xl"
            icon="qls-icon-delete"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useFieldArray } from 'vee-validate'
import * as zod from 'zod'

const props = defineProps<{
  index: number
  configuration: DefaultCampaignConfiguration
  deleteHidden?: boolean
  disabled?: boolean
}>()

const invalidNumberErrorMessage = 'You must specify a number'

const timeframeUnitOptions = TimeframeHelper.getTimeframeUnitOptions()

const { remove, fields } = useFieldArray<ExecutionProfileStage>('executionProfileStages')

const stageValidation = computed(() => {
  return {
    ...props.configuration.validation.stage,
    maxDurationInMilliSeconds: TimeframeHelper.isoStringToTargetTimeframeUnit(
      props.configuration.validation.stage.maxDuration,
    ),
    minDurationInMilliSeconds: TimeframeHelper.isoStringToTargetTimeframeUnit(
      props.configuration.validation.stage.minDuration,
    ),
    maxStartDurationInMilliSeconds: TimeframeHelper.isoStringToTargetTimeframeUnit(
      props.configuration.validation.stage.maxStartDuration,
    ),
    minStartDurationInMilliSeconds: TimeframeHelper.isoStringToTargetTimeframeUnit(
      props.configuration.validation.stage.minStartDuration,
    ),
  }
})

const _getNumberValidationSchema = (min: number, max: number) => {
  const rangeErrorMessage = `Value must be between ${min} and ${max}.`
  return zod.coerce
    .number({ invalid_type_error: invalidNumberErrorMessage })
    .min(min, rangeErrorMessage)
    .max(max, rangeErrorMessage)
    .nullable()
}

const executionProfileSchema = computed(() => {
  const {
    maxMinionsCount,
    minMinionsCount,
    maxDurationInMilliSeconds,
    minDurationInMilliSeconds,
    maxStartDurationInMilliSeconds,
    minStartDurationInMilliSeconds,
  } = stageValidation.value

  const startUnit = (fields.value[props.index]?.value?.rampUpDurationUnit ?? TimeframeUnitConstant.SEC) as TimeframeUnit
  const durUnit = (fields.value[props.index]?.value?.durationUnit ?? TimeframeUnitConstant.SEC) as TimeframeUnit

  return {
    minionsCount: toTypedSchema(_getNumberValidationSchema(minMinionsCount, maxMinionsCount)),
    rampUpDuration: toTypedSchema(
      _getNumberValidationSchema(
        TimeframeHelper.fromMs(minStartDurationInMilliSeconds, startUnit),
        TimeframeHelper.fromMs(maxStartDurationInMilliSeconds, startUnit),
      ),
    ),
    rampUpDurationUnit: toTypedSchema(zod.string().nullable()),
    duration: toTypedSchema(
      _getNumberValidationSchema(
        TimeframeHelper.fromMs(minDurationInMilliSeconds, durUnit),
        TimeframeHelper.fromMs(maxDurationInMilliSeconds, durUnit),
      ),
    ),
    durationUnit: toTypedSchema(zod.string().nullable()),
  }
})

const handleDeleteBtnClick = () => {
  remove(props.index)
}
</script>

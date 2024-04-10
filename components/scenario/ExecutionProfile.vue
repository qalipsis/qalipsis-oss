<template>
  <a-row :gutter="8" class="mb-2">
    <a-col :span="5">
      <FormInput
        label="Minions"
        suffix="qty"
        :form-control-name="`executionProfileStages[${index}].minionsCount`"
        :field-validation-schema="executionProfileSchema.minionsCount"
        @input="emit('executionProfileChange', fields[index].value)"
      />
    </a-col>
    <a-col :span="6">
      <FormInput
        label="Duration"
        suffix="ms"
        :form-control-name="`executionProfileStages[${index}].duration`"
        :field-validation-schema="executionProfileSchema.duration"
        @input="handleDurationInputChange"
      />
    </a-col>
    <a-col :span="6">
      <FormInput
        label="Start"
        suffix="ms"
        :form-control-name="`executionProfileStages[${index}].startDuration`"
        :field-validation-schema="executionProfileSchema.startDuration"
        @input="handleStartDurationInputChange"
      />
    </a-col>
    <a-col :span="6">
      <FormInput
        label="Start resolution"
        suffix="ms"
        :form-control-name="`executionProfileStages[${index}].resolution`"
        :field-validation-schema="executionProfileSchema.resolution"
        @input="emit('executionProfileChange', fields[index].value)"
      />
    </a-col>
    <a-col :span="1" v-if="!deleteHidden">
      <div
        class="cursor-pointer pt-12 flex items-center h-10"
        @click="handleDeleteBtnClick"
      >
        <BaseIcon
          :class="TailwindClassHelper.primaryColorFilterHoverClass"
          icon="/icons/icon-delete-small.svg"
        />
      </div>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { toTypedSchema } from "@vee-validate/zod";
import { useFieldArray } from "vee-validate";
import * as zod from "zod";

const props = defineProps<{
  index: number;
  configuration: DefaultCampaignConfiguration;
  deleteHidden?: boolean;
}>();
const emit = defineEmits<{
  (e: "executionProfileChange", v: ExecutionProfileStage | null): void;
}>();

const invalidNumberErrorMessage = "You must specify a number";

const { remove, fields } = useFieldArray<ExecutionProfileStage>("executionProfileStages");

const hasInvalidStartDuration = ref(false);

const stageValidation = computed(() => {
  return {
    ...props.configuration.validation.stage,
    maxDurationInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.maxDuration,
      TimeframeUnitConstant.SEC
    ),
    minDurationInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.minDuration,
      TimeframeUnitConstant.SEC
    ),
    maxStartDurationInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.maxStartDuration,
      TimeframeUnitConstant.SEC
    ),
    minStartDurationInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.minStartDuration,
      TimeframeUnitConstant.SEC
    ),
    maxResolutionInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.maxResolution,
      TimeframeUnitConstant.SEC
    ),
    minResolutionInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.minResolution,
      TimeframeUnitConstant.SEC
    ),
  };
});

const {
  maxMinionsCount,
  minMinionsCount,
  maxDurationInMilliSeconds,
  minDurationInMilliSeconds,
  maxResolutionInMilliSeconds,
  minResolutionInMilliSeconds,
  maxStartDurationInMilliSeconds,
  minStartDurationInMilliSeconds,
} = stageValidation.value;

const _getNumberValidationSchema = (min: number, max: number) => {
  return zod.coerce
    .number({ invalid_type_error: invalidNumberErrorMessage })
    .min(min)
    .max(max, `Value must be between ${min} and ${max}.`)
    .nullable();
};

const executionProfileSchema = {
  minionsCount: toTypedSchema(
    _getNumberValidationSchema(minMinionsCount, maxMinionsCount)
  ),
  duration: toTypedSchema(
    _getNumberValidationSchema(
      minDurationInMilliSeconds,
      maxDurationInMilliSeconds
    )
  ),
  startDuration: toTypedSchema(
    _getNumberValidationSchema(
      minStartDurationInMilliSeconds,
      maxStartDurationInMilliSeconds
    )
  ),
  resolution: toTypedSchema(
    _getNumberValidationSchema(
      minResolutionInMilliSeconds,
      maxResolutionInMilliSeconds
    )
  ),
};

const handleDurationInputChange = () => {
  emit("executionProfileChange", fields.value[props.index].value);
};

const handleStartDurationInputChange = () => {
  emit("executionProfileChange", fields.value[props.index].value);
};

const handleDeleteBtnClick = () => {
  remove(props.index);
  emit("executionProfileChange", null);
};
</script>

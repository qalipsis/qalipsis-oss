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
        @input="handleDurationInputChange($event)"
      />
    </a-col>
    <a-col :span="6">
      <FormInput
        label="Start"
        suffix="ms"
        :form-control-name="`executionProfileStages[${index}].startDuration`"
        :field-validation-schema="executionProfileSchema.startDuration"
        @input="handleStartDurationInputChange($event)"
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
        class="cursor-pointer pt-7 scenario-form-delete-btn-wrapper"
        @click="handleDeleteBtnClick"
      >
        <BaseIcon icon="/icons/icon-delete-small.svg" />
      </div>
    </a-col>
  </a-row>
  <!-- <span v-if="hasInvalidStartDuration" class="text-pink"
    >The start duration value should be less or equal than the duration value!
  </span> -->
</template>

<script setup lang="ts">
import { toTypedSchema } from "@vee-validate/zod";
import { DefaultCampaignConfiguration } from "utils/configuration";
import { ExecutionProfileStage } from "utils/scenario";
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
      TimeframeUnit.SEC
    ),
    minDurationInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.minDuration,
      TimeframeUnit.SEC
    ),
    maxStartDurationInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.maxStartDuration,
      TimeframeUnit.SEC
    ),
    minStartDurationInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.minStartDuration,
      TimeframeUnit.SEC
    ),
    maxResolutionInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.maxResolution,
      TimeframeUnit.SEC
    ),
    minResolutionInMilliSeconds: TimeframeHelper.toMilliseconds(
      props.configuration.validation.stage.minResolution,
      TimeframeUnit.SEC
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

const handleDurationInputChange = (newDurationValue: string) => {
  // When the duration or start duration is not a number, return
  // if (
  //   isNaN(+newDurationValue) ||
  //   isNaN(fields.value[props.index].value.startDuration)
  // )
  //   return;

  // hasInvalidStartDuration.value =
  //   +newDurationValue < fields.value[props.index].value.startDuration;

  emit("executionProfileChange", fields.value[props.index].value);
};

const handleStartDurationInputChange = (newStartDurationValue: string) => {
  // When the duration or start duration is not a number, return
  // if (
  //   isNaN(+newStartDurationValue) ||
  //   isNaN(fields.value[props.index].value.duration)
  // )
  //   return;

  // hasInvalidStartDuration.value =
  //   +newStartDurationValue > fields.value[props.index].value.duration;

  emit("executionProfileChange", fields.value[props.index].value);
};

const handleDeleteBtnClick = () => {
  remove(props.index);
  emit("executionProfileChange", null);
};
</script>

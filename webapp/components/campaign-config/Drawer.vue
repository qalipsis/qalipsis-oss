<template>
  <BaseDrawer
    title="Configuration of campaign"
    :open="open"
    :footer-hidden="disabled"
    @close="emit('update:open', false)"
    @confirm-btn-click="handleConfirmBtnClick"
  >
    <form>
      <div class="grid grid-cols-12 gap-y-4 gap-x-2">
        <div class="col-span-12">
          <FormRadioButtonGroup
            label="Timeout Campaign"
            form-control-name="timeoutType"
            :options="timeoutOptions"
            :disabled="disabled"
          />
        </div>
        <div
          v-if="values.timeoutType === 'hard' || values.timeoutType === 'soft'"
          class="col-span-8"
        >
          <FormInputSelect
            label="Duration"
            form-input-control-name="durationValue"
            form-select-control-name="durationUnit"
            :input-field-validation-schema="fieldValidationSchema.durationValue"
            :select-field-validation-schema="fieldValidationSchema.durationUnit"
            :options="durationUnitOptions"
            :input-disabled="disabled"
            :select-disabled="disabled"
          />
        </div>
        <div class="col-span-12">
          <FormCheckbox
            label="Schedule"
            form-control-name="scheduled"
            :disabled="disabled"
          />
        </div>
        <template v-if="values.scheduled">
          <div class="col-span-6">
            <FormDateTimePicker
              label="Date time"
              form-control-name="scheduledTime"
              format="MM/dd/yyyy HH:mm"
              :min-date="new Date()"
              :disabled="disabled"
              :field-validation-schema="fieldValidationSchema.scheduledTime"
            />
          </div>
          <div class="col-span-6">
            <FormAutoComplete
              label="Timezone"
              form-control-name="timezone"
              :options="timezoneOptions"
              :field-validation-schema="fieldValidationSchema.timezone"
              :disabled="disabled"
            >
            </FormAutoComplete>
          </div>
          <div
            v-if="values.scheduledTime && values.timezone"
            class="col-span-12"
          >
            <FormCheckbox
              label="Repeat"
              form-control-name="repeatEnabled"
              :disabled="disabled"
            />
          </div>
          <template v-if="values.repeatEnabled">
            <div class="col-span-12">
              <FormRadioButtonGroup
                label="Repeat every"
                form-control-name="repeatTimeRange"
                :options="repeatTimeRangeOptions"
                :disabled="disabled"
                @change="handleRepeatTimeRangeOptionChange"
              />
            </div>
            <div
              class="col-span-12"
              :class="{ hidden: values.repeatTimeRange !== 'HOURLY' }"
            >
              <div class="w-80">
                <FormMultiCircleCheck
                  form-control-name="repeatValues"
                  :options="hourlyOptions"
                  :disabled="disabled"
                />
              </div>
            </div>
            <div
              class="col-span-12"
              :class="{ hidden: values.repeatTimeRange !== 'DAILY' }"
            >
              <div class="w-80">
                <FormMultiCircleCheck
                  form-control-name="repeatValues"
                  :options="dailyOptions"
                  :disabled="disabled"
                />
              </div>
            </div>
            <div
              class="col-span-12"
              :class="{ hidden: values.repeatTimeRange !== 'MONTHLY' }"
            >
              <div class="w-80">
                <FormMultiCircleCheck
                  form-control-name="repeatValues"
                  :options="monthlyOptions"
                  :disabled="disabled"
                />
              </div>
              <div class="w-80">
                <div class="mt-2 mb-2">Relative before the end of month</div>
                <FormMultiCircleCheck
                  form-control-name="relativeRepeatValues"
                  :options="relativeDayOfMonthOptions"
                  :disabled="disabled"
                />
              </div>
            </div>
            <div class="col-span-12">
              <label>Schedule:</label>
              <span>{{ repeatText }}</span>
            </div>
          </template>
        </template>
      </div>
    </form>
  </BaseDrawer>
</template>

<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as zod from 'zod'

const props = defineProps<{
  open: boolean
  campaignConfigurationForm: CampaignConfigurationForm
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'submit', v: CampaignConfigurationForm): void
  (e: 'update:open', v: boolean): void
}>()

const initialFormValue: CampaignConfigurationForm = {
  timeoutType: props.campaignConfigurationForm?.timeoutType ?? TimeoutTypeConstant.NONE,
  durationValue: props.campaignConfigurationForm?.durationValue ?? '',
  durationUnit: props.campaignConfigurationForm?.durationUnit ?? TimeframeUnitConstant.MS,
  scheduled: props.campaignConfigurationForm?.scheduled ?? false,
  repeatEnabled: props.campaignConfigurationForm?.repeatEnabled ?? false,
  repeatTimeRange: props.campaignConfigurationForm?.repeatTimeRange ?? 'DAILY',
  repeatValues: props.campaignConfigurationForm?.repeatValues ?? [],
  relativeRepeatValues: props.campaignConfigurationForm?.relativeRepeatValues ?? [],
  scheduledTime: props.campaignConfigurationForm?.scheduledTime ?? null,
  timezone: props.campaignConfigurationForm?.timezone ?? '',
}

const { handleSubmit, setFieldValue, values, meta, validate } = useForm<CampaignConfigurationForm>({
  initialValues: {
    ...initialFormValue,
  },
})

const fieldValidationSchema = {
  durationValue: toTypedSchema(
    zod.coerce
      .number({ invalid_type_error: 'You must specify a number' })
      .nullable()
      .refine(
        (value: number | null) => {
          if (values.timeoutType === TimeoutTypeConstant.NONE) return true

          if (value) return true

          return false
        },
        {
          message: 'The duration cannot be empty',
        }
      )
  ),
  durationUnit: toTypedSchema(zod.string().nullable()),
  scheduledTime: toTypedSchema(
    zod
      .date()
      .nullable()
      .refine(
        (value: Date | null) => {
          // The schedule time can be empty when the campaign is not scheduled.
          if (!values.scheduled) return true

          if (value) return true

          return false
        },
        {
          message: 'Date time cannot be empty',
        }
      )
  ),
  timezone: toTypedSchema(
    zod
      .string()
      .nullable()
      .refine(
        (value: string | null) => {
          // Timezone can be empty when the campaign is not scheduled.
          if (!values.scheduled) return true

          if (value) return true

          return false
        },
        {
          message: 'Timezone cannot be empty',
        }
      )
  ),
}
const timeoutOptions = CampaignDetailsConfig.CAMPAIGN_TIMEOUT_OPTIONS
const durationUnitOptions = TimeframeHelper.getTimeframeUnitOptions()
const timezoneOptions = TimeframeHelper.getTimezoneOptions()
const repeatTimeRangeOptions = CampaignDetailsConfig.REPEAT_TIME_RANGE_OPTIONS
const hourlyOptions = CampaignDetailsConfig.HOURLY_REPEAT_OPTIONS
const dailyOptions = CampaignDetailsConfig.DAILY_REPEAT_OPTIONS
const monthlyOptions = CampaignDetailsConfig.MONTHLY_REPEAT_OPTIONS
const relativeDayOfMonthOptions = CampaignDetailsConfig.RELATIVE_DAY_OF_MONTH_OPTIONS

const DAY_OF_WEEK_MAP: { [key: string]: string } = {
  0: 'Monday',
  1: 'Tuesday',
  2: 'Wednesday',
  3: 'Thursday',
  4: 'Friday',
  5: 'Saturday',
  6: 'Sunday',
}

const repeatText = computed(() => {
  const minutes = `${values.scheduledTime!.getMinutes()}`.padStart(2, '0')
  const time = `${values.scheduledTime!.getHours()}:${minutes}`

  switch (values.repeatTimeRange) {
    case 'HOURLY':
      return values.repeatValues.length === 0 || values.repeatValues.length === hourlyOptions.length
        ? 'occurs every hour'
        : `occurs every day at ${[...values.repeatValues]
            .sort()
            .map((hour) => `${+hour % 24}:${minutes}`)
            .join(', ')}`
    case 'DAILY':
      return values.repeatValues.length === 0 || values.repeatValues.length === dailyOptions.length
        ? `occurs every day at ${time}`
        : `occurs every ${[...values.repeatValues]
            .sort()
            .map((it) => `${DAY_OF_WEEK_MAP[it.toString()]}`)
            .join(', ')} at ${time}`
    case 'MONTHLY':
      const shouldOnlyDisplayTime =
        values.repeatValues.length === monthlyOptions.length ||
        (values.repeatValues.length === 0 && values.relativeRepeatValues.length === 0)
      const shouldOnlyDisplayRelativeValues = values.repeatValues.length === 0 && values.relativeRepeatValues.length > 0
      const shouldOnlyDisplaySelectedDays = values.repeatValues.length > 0 && values.relativeRepeatValues.length === 0

      if (shouldOnlyDisplayTime) {
        return `occurs every day at ${time}`
      } else if (shouldOnlyDisplayRelativeValues) {
        return `occurs every month ${_concatenatedRelativeRepeatValues(
          values.relativeRepeatValues
        )} before the end of the month, at ${time}`
      } else if (shouldOnlyDisplaySelectedDays) {
        return `occurs every month on ${[...values.repeatValues]
          .sort()
          .map((day) => `${day}${TimeframeHelper.getOrdinalNumberSuffix(+day)}`)
          .join(', ')}, at ${time}`
      }

      return `occurs every month on ${[...values.repeatValues]
        .sort()
        .map((day) => `${day}${TimeframeHelper.getOrdinalNumberSuffix(+day)}`)
        .join(', ')}, and ${_concatenatedRelativeRepeatValues(
        values.relativeRepeatValues
      )} before the end of the month, at ${time}`
    default:
      return ''
  }
})

const _concatenatedRelativeRepeatValues = (relativeValues: string[]) => {
  // Convert numbers to strings with "days" or "last day"
  const formatted = [...relativeValues]
    .sort()
    .reverse()
    .map((day) => (day === '-1' ? 'last day' : `${-day} days`))

  if (formatted.length === 1) {
    return formatted[0]
  } else if (formatted.length === 2) {
    return `${formatted[0]} and ${formatted[1]}`
  } else {
    const last = formatted.pop()
    return `${formatted.join(', ')}, and ${last}`
  }
}

const handleRepeatTimeRangeOptionChange = () => {
  setFieldValue('repeatValues', [])
}

const handleConfirmBtnClick = handleSubmit(async (values: CampaignConfigurationForm) => {
  validate()
  if (meta.value.valid) {
    emit('submit', values)
    emit('update:open', false)
  }
})
</script>

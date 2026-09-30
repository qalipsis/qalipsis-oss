<template>
  <BaseDrawer
    :open="open"
    @close="emit('update:open', false)"
    :title="title"
    :footer-hidden="disabled"
    confirm-btn-text="Apply"
    @confirm-btn-click="handleConfirmBtnClick"
  >
    <form class="p-2">
      <div class="grid grid-cols-12 gap-2">
        <div class="col-span-12 mt-2 mb-4">
          <span class="text-gray-500 dark:text-gray-100 text-base">Execution profile</span>
        </div>
        <div class="col-span-12">
          <template v-for="(_, index) in executionProfileFields">
            <ScenarioExecutionProfile
              :index="index"
              :configuration="configuration"
              :deleteHidden="index === 0"
              :disabled="disabled"
            />
            <span
              v-if="invalidExecutionProfileIndexes.includes(index)"
              class="text-red-600 dark:text-red-300"
            >
              The ramp up duration value should be less or equal than the duration value!
            </span>
          </template>
          <p :class="ruleClass(hasValidMinionsSummary)">
            Total of minions should not exceed {{ maxMinionsCountText }}
          </p>
          <p :class="ruleClass(hasValidDurationSummary)">
            Total duration cannot exceed {{ maxDurationText }}
          </p>
        </div>
        <div
          v-if="!disabled"
          class="col-span-12"
        >
          <BaseButton
            icon="qls-icon-plus"
            btn-style="outlined"
            class="w-full"
            text="Add new"
            @click="handleAddExecutionProfileBtnClick"
          />
        </div>
        <div class="col-span-12 my-5">
          <BaseDivideLine />
        </div>
        <div class="col-span-12 flex items-baseline gap-x-2">
          <span class="text-gray-500 dark:text-gray-100">Zone</span>
          <span class="text-gray-400 dark:text-gray-300 text-sm">optional</span>
        </div>
        <div class="col-span-12">
          <ScenarioZone
            v-for="(_, index) in zoneFields"
            :index="index"
            :zone-options="zoneOptions"
            :disabled="disabled"
          />
          <p :class="ruleClass(hasValidZonesCount)">
            Until {{ effectiveMaxZonesCount }} different zones can be added
          </p>
          <p :class="ruleClass(hasValidZoneShareSummary)">
            When zones are configured, the sum of their shares should be equal 100%
          </p>
        </div>
        <div
          class="col-span-12"
          :class="{ 'cursor-not-allowed': !canZoneBeAdded }"
          v-if="!disabled"
        >
          <BaseTooltip :text="zoneAdditionRestriction">
            <BaseButton
              icon="qls-icon-plus"
              btn-style="outlined"
              class="w-full"
              text="Add new"
              :disabled="!canZoneBeAdded"
              @click="handleAddZoneBtnClick"
            />
          </BaseTooltip>
        </div>
        <div class="col-span-12 my-5">
          <BaseDivideLine />
        </div>
      </div>
    </form>
    <div
      class="pr-3 py-2 chart-container"
      v-if="canChartBeRendered && chartOptions"
    >
      <apexchart
        :options="chartOptions"
        :height="250"
        :series="chartDataSeries"
      />
    </div>
  </BaseDrawer>
</template>

<script setup lang="ts">
import {type ApexOptions} from 'apexcharts'
import {useFieldArray, useForm} from 'vee-validate'

const { fetchZones } = useZonesApi()

// Styles of the validation rules of the campaign, depending on whether the configuration matches them.
const RULE_CLASS = 'text-gray-500 dark:text-gray-400 text-sm pt-2'
const VIOLATED_RULE_CLASS = 'text-red-600 dark:text-red-300 text-sm pt-2'

const props = defineProps<{
  open: boolean
  scenario: ScenarioSummary
  configuration: DefaultCampaignConfiguration
  scenarioForm?: ScenarioConfigurationForm
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'submit', v: ScenarioConfigurationForm): void
}>()

const title = computed(() => `Configuration of ${props.scenario.name}`)
const maxDurationInMilliSeconds = computed(() =>
  TimeframeHelper.isoStringToTargetTimeframeUnit(props.configuration.validation.maxExecutionDuration),
)
const maxDurationText = computed(() =>
    TimeframeHelper.isoStringToHumanReadable(props.configuration.validation.maxExecutionDuration),
)
const maxMinionsCountText = computed(() =>
    ScenarioDetailsHelper.toDisplayNumber(props.configuration.validation.maxMinionsCount),
)
const zoneOptions = ref<FormMenuOption[]>([])
const canChartBeRendered = ref(false)
const chartOptions = ref<ApexOptions | null>(null)
const chartDataSeries = ref<ApexAxisChartSeries>([])
const isConfirmBtnClicked = ref(false)

const { handleSubmit, values, meta } = useForm<ScenarioConfigurationForm>({
  initialValues: {
    executionProfileStages: props.scenarioForm?.executionProfileStages ?? [
      {
        minionsCount: ScenarioHelper.defaultStageMinionsCount(props.configuration.validation),
        duration: TimeframeHelper.isoStringToTargetTimeframeUnit(props.configuration.validation.stage.minDuration, 'SEC'),
        durationUnit: TimeframeUnitConstant.SEC,
        rampUpDuration: TimeframeHelper.isoStringToTargetTimeframeUnit(
          props.configuration.validation.stage.minStartDuration, 'SEC',
        ),
        rampUpDurationUnit: TimeframeUnitConstant.SEC,
        resolution: TimeframeHelper.isoStringToTargetTimeframeUnit(props.configuration.validation.stage.minResolution),
      },
    ],
    zones: props.scenarioForm?.zones ?? [],
  },
})

const { push: pushExecutionProfile, fields: executionProfileFields, update: updateExecutionProfile } =
  useFieldArray<ExecutionProfileStage>('executionProfileStages')
const { push: pushZones, fields: zoneFields } = useFieldArray<ZoneForm>('zones')

const _toMs = (value: number, unit?: string): number =>
  TimeframeHelper.toMs(+value, (unit ?? TimeframeUnitConstant.SEC) as TimeframeUnit)

const invalidExecutionProfileIndexes = computed(() =>
  values.executionProfileStages.reduce<number[]>((acc, stage, index) => {
    if (isNaN(+stage.rampUpDuration) || isNaN(+stage.duration)) return acc
    if (_toMs(+stage.rampUpDuration, stage.rampUpDurationUnit) > _toMs(+stage.duration, stage.durationUnit))
      acc.push(index)
    return acc
  }, [])
)

const totalMinionsCount = computed(() =>
    values.executionProfileStages.reduce((acc, s) => acc + +s.minionsCount, 0)
)

const hasValidMinionsSummary = computed(
    () => totalMinionsCount.value <= props.configuration.validation.maxMinionsCount
)

const hasValidDurationSummary = computed(() => {
  const total = values.executionProfileStages.reduce((acc, s) => acc + _toMs(+s.duration, s.durationUnit), 0)
  return total <= maxDurationInMilliSeconds.value
})

const hasValidZoneShareSummary = computed(() =>
  values.zones.length === 0 || values.zones.reduce((acc, z) => acc + +z.share, 0) === 100
)

// A zone can only be used once in a scenario, hence no more zone can be added when they are all used.
const enabledZonesCount = computed(() => zoneOptions.value.filter((option) => !option.disabled).length)

// Heads of previous versions do not declare any limit of zones, the available ones are then the only restriction.
const maxZonesCount = computed(() => props.configuration.validation.maxZonesCount ?? enabledZonesCount.value)

const hasValidZonesCount = computed(() => values.zones.length <= maxZonesCount.value)

// A scenario cannot be distributed on more zones than the available ones, whatever the limit of the subscription.
const effectiveMaxZonesCount = computed(() =>
    enabledZonesCount.value > 0 ? Math.min(maxZonesCount.value, enabledZonesCount.value) : maxZonesCount.value,
)

const canZoneBeAdded = computed(
    () => values.zones.length < Math.min(maxZonesCount.value, enabledZonesCount.value)
)

const zoneAdditionRestriction = computed(() => {
  if (canZoneBeAdded.value) return undefined
  if (enabledZonesCount.value === 0) return 'There is no zone available to distribute the execution of this scenario.'
  return values.zones.length >= maxZonesCount.value
      ? `A scenario cannot be distributed on more than ${maxZonesCount.value} zones.`
    : `The ${enabledZonesCount.value} available zones are all used, the execution cannot be distributed further.`
})

// The violations are only reported once the user tried to submit the configuration, whereas the rules
// themselves are always visible.
const ruleClass = (isRuleMatched: boolean) =>
  isRuleMatched || !isConfirmBtnClicked.value ? RULE_CLASS : VIOLATED_RULE_CLASS

const _setScenarioConfigChartDataSeries = (executionProfileStages: ExecutionProfileStage[]) => {
  canChartBeRendered.value = false
  // Notes: Needs to add the timeout to rerender the chart
  setTimeout(() => {
    const normalizedStages = executionProfileStages.map((s) => ({
      ...s,
      rampUpDuration: _toMs(+s.rampUpDuration, s.rampUpDurationUnit),
      duration: _toMs(+s.duration, s.durationUnit),
    }))
    const chartData = ScenarioHelper.toScenarioConfigChartData(normalizedStages)
    chartDataSeries.value = chartData.chartDataSeries
    chartOptions.value = chartData.chartOptions
    canChartBeRendered.value = true
  }, 100)
}

watch(
  () => values.executionProfileStages,
  (stages) => _setScenarioConfigChartDataSeries(stages),
  { deep: true, immediate: true },
)

// The ramp-up of a stage happens within its duration, hence a longer ramp-up extends the duration
// instead of leaving the user with a configuration to fix.
watch(
  () => values.executionProfileStages.map((stage) => _toMs(+stage.rampUpDuration, stage.rampUpDurationUnit)),
  (rampUpDurations) => {
    rampUpDurations.forEach((rampUpDuration, index) => {
      const stage = values.executionProfileStages[index]
      if (!stage || isNaN(rampUpDuration) || rampUpDuration <= _toMs(+stage.duration, stage.durationUnit)) return

      updateExecutionProfile(index, {
        ...stage,
        duration: TimeframeHelper.fromMs(
          rampUpDuration,
          (stage.durationUnit ?? TimeframeUnitConstant.SEC) as TimeframeUnit,
        ),
      })
    })
  },
)

onMounted(() => {
  _initZoneOptions()
})

const handleConfirmBtnClick = handleSubmit(async (values: ScenarioConfigurationForm) => {
  isConfirmBtnClicked.value = true
  const hasValidFormInput =
    meta.value.valid &&
    invalidExecutionProfileIndexes.value.length === 0 &&
    hasValidDurationSummary.value &&
    hasValidMinionsSummary.value &&
      hasValidZoneShareSummary.value &&
      hasValidZonesCount.value
  if (hasValidFormInput) {
    emit('submit', values)
    emit('update:open', false)
  }
})

const _initZoneOptions = async () => {
  // Prepares the available zone options for configuring the scenario, sorted as they are displayed.
  const zones = await fetchZones()
  zoneOptions.value = zones
    .map((zone) => ({
      label: zone.title,
      value: zone.key,
      disabled: !zone.enabled,
      imagePath: zone.imagePath,
      description: zone.description,
    }))
    .sort((option, otherOption) => option.label.localeCompare(otherOption.label))
}

const handleAddExecutionProfileBtnClick = () => {
  pushExecutionProfile({
    minionsCount: ScenarioHelper.defaultStageMinionsCount(props.configuration.validation, totalMinionsCount.value),
    duration: TimeframeHelper.isoStringToTargetTimeframeUnit(props.configuration.validation.stage.minDuration, 'SEC'),
    durationUnit: TimeframeUnitConstant.SEC,
    rampUpDuration: TimeframeHelper.isoStringToTargetTimeframeUnit(
      props.configuration.validation.stage.minStartDuration, 'SEC',
    ),
    rampUpDurationUnit: TimeframeUnitConstant.SEC,
    resolution: TimeframeHelper.isoStringToTargetTimeframeUnit(props.configuration.validation.stage.minResolution),
  })
}

const handleAddZoneBtnClick = () => {
  pushZones({
    // The zone takes the whole share that is not distributed yet, hence 100% for the very first one.
    share: Math.max(0, 100 - values.zones.reduce((acc, z) => acc + +z.share, 0)),
    name: '',
  })
}
</script>

<style lang="scss" scoped>
// Workaround to hide the export csv button.
.chart-container :deep(.apexcharts-menu-item.exportCSV) {
  display: none;
}
</style>

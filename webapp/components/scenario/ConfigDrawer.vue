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
          <template v-if="isConfirmBtnClicked">
            <span
              v-if="!hasValidMinionsSummary"
              class="text-red-600 dark:text-red-300 pt-2"
            >
              The summary of the minions count should not exceed
              {{ configuration.validation.maxMinionsCount }}
            </span>
            <span
              v-if="!hasValidDurationSummary"
              class="text-red-600 dark:text-red-300 pt-2"
            >
              The summary of the duration should not exceed
              {{ maxDurationInMilliSeconds }} ms
            </span>
          </template>
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
        <div class="col-span-12">
          <span class="text-gray-500 dark:text-gray-100">Zone</span>
        </div>
        <div class="col-span-12">
          <ScenarioZone
            v-for="(_, index) in zoneFields"
            :index="index"
            :zone-options="zoneOptions"
            :disabled="disabled"
          />
          <template v-if="isConfirmBtnClicked">
            <span
              v-if="!hasValidZoneShareSummary && values.zones.length > 0"
              class="text-red-600 pt-2"
            >
              The sum of the share zones should be equal 100%
            </span>
          </template>
        </div>
        <div
          class="col-span-12"
          v-if="!disabled"
        >
          <BaseButton
            icon="qls-icon-plus"
            btn-style="outlined"
            class="w-full"
            text="Add new"
            @click="handleAddZoneBtnClick"
          />
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
import { type ApexOptions } from 'apexcharts'
import { useFieldArray, useForm } from 'vee-validate'

const { fetchZones } = useZonesApi()

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

const zoneOptions = ref<FormMenuOption[]>([])
const canChartBeRendered = ref(false)
const chartOptions = ref<ApexOptions | null>(null)
const chartDataSeries = ref<ApexAxisChartSeries>([])
const isConfirmBtnClicked = ref(false)

const { handleSubmit, values, meta } = useForm<ScenarioConfigurationForm>({
  initialValues: {
    executionProfileStages: props.scenarioForm?.executionProfileStages ?? [
      {
        minionsCount: props.configuration.validation.stage.minMinionsCount,
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

const { push: pushExecutionProfile, fields: executionProfileFields } =
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

const hasValidMinionsSummary = computed(() => {
  const total = values.executionProfileStages.reduce((acc, s) => acc + +s.minionsCount, 0)
  return total <= props.configuration.validation.maxMinionsCount
})

const hasValidDurationSummary = computed(() => {
  const total = values.executionProfileStages.reduce((acc, s) => acc + _toMs(+s.duration, s.durationUnit), 0)
  return total <= maxDurationInMilliSeconds.value
})

const hasValidZoneShareSummary = computed(() =>
  values.zones.length === 0 || values.zones.reduce((acc, z) => acc + +z.share, 0) === 100
)

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
    hasValidZoneShareSummary.value
  if (hasValidFormInput) {
    emit('submit', values)
    emit('update:open', false)
  }
})

const _initZoneOptions = async () => {
  // Prepares the available zone options for configuring the scenario.
  const zones = await fetchZones()
  zoneOptions.value = zones.map((zone) => ({
    label: zone.title,
    value: zone.key,
    disabled: !zone.enabled,
  }))
}

const handleAddExecutionProfileBtnClick = () => {
  pushExecutionProfile({
    minionsCount: props.configuration.validation.stage.minMinionsCount,
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
    share: 20,
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

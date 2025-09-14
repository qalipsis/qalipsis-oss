<template>
  <BaseDrawer
    :open="open"
    @close="emit('update:open', false)"
    :title="title"
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
              @executionProfileChange="handleExecutionProfileChange($event, index)"
            />
            <span
              v-if="invalidExecutionProfileIndexes.includes(index)"
              class="text-red-600"
            >
              The start duration value should be less or equal than the duration value!
            </span>
          </template>
          <template v-if="isConfirmBtnClicked">
            <span
              v-if="!hasValidMinionsSummary"
              class="text-red-600 pt-2"
            >
              The summary of the minions count should not exceed
              {{ configuration.validation.maxMinionsCount }}
            </span>
            <span
              v-if="!hasValidDurationSummary"
              class="text-red-600 pt-2"
            >
              The summary of the duration should not exceed
              {{ maxDurationInMilliSeconds }} ms
            </span>
          </template>
        </div>
        <div class="col-span-12">
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
            @zoneSharedInputChange="handleZoneSharedInputChange()"
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
        <div class="col-span-12">
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
      class="pr-3 py-2"
      v-if="canChartBeRendered"
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
}>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'submit', v: ScenarioConfigurationForm): void
}>()

const title = computed(() => `Configuration of ${props.scenario.name}`)
const maxDurationInMilliSeconds = computed(() =>
  TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.maxExecutionDuration)
)

const zoneOptions = ref<FormMenuOption[]>([])
const canChartBeRendered = ref(false)
const chartOptions = ref<ApexOptions>({
  ...ScenarioDetailsConfig.CHART_OPTIONS,
})
const chartDataSeries = ref<ApexAxisChartSeries>([])
const invalidExecutionProfileIndexes = ref<number[]>([])
const hasValidZoneShareSummary = ref(true)
const hasValidMinionsSummary = ref(true)
const hasValidDurationSummary = ref(true)
const isConfirmBtnClicked = ref(false)

const { handleSubmit, values, meta, validate } = useForm<ScenarioConfigurationForm>({
  initialValues: {
    executionProfileStages: props.scenarioForm?.executionProfileStages ?? [
      {
        minionsCount: props.configuration.validation.stage.minMinionsCount,
        duration: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minDuration),
        startDuration: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minStartDuration),
        resolution: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minResolution),
      },
    ],
    zones: props.scenarioForm?.zones ?? [],
  },
})

const { push: pushExecutionProfile, fields: executionProfileFields } =
  useFieldArray<ExecutionProfileStage>('executionProfileStages')
const { push: pushZones, fields: zoneFields } = useFieldArray<ZoneForm>('zones')

onMounted(() => {
  _initZoneOptions()
  const initialExecutionProfiles: ExecutionProfileStage[] = props.scenarioForm?.executionProfileStages ?? [
    {
      minionsCount: props.configuration.validation.stage.minMinionsCount,
      duration: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minDuration),
      startDuration: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minStartDuration),
      resolution: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minResolution),
    },
  ]
  _setScenarioConfigChartDataSeries(initialExecutionProfiles)
})

const handleConfirmBtnClick = handleSubmit(async (values: ScenarioConfigurationForm) => {
  validate()
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
  zoneOptions.value = zones
    .filter((zone) => zone.enabled)
    .map((zone) => ({
      label: zone.title,
      value: zone.key,
    }))
}

const handleExecutionProfileChange = (executionProfileStage: ExecutionProfileStage | null, index: number) => {
  // Validates the start duration and duration
  _validateStartDurationAndDuration(index, executionProfileStage)
  // Validates the summary of minions
  _validateMinionsSummary()
  // Validates the summary of the duration
  _validateDurationSummary()
  _setScenarioConfigChartDataSeries(values.executionProfileStages)
}

const handleAddExecutionProfileBtnClick = () => {
  pushExecutionProfile({
    minionsCount: props.configuration.validation.stage.minMinionsCount,
    duration: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minDuration),
    startDuration: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minStartDuration),
    resolution: TimeframeHelper.isoStringToMilliseconds(props.configuration.validation.stage.minResolution),
  })
  _setScenarioConfigChartDataSeries(values.executionProfileStages)
  // Validates the summary of minions
  _validateMinionsSummary()
  // Validates the summary of the duration
  _validateDurationSummary()
}

const handleZoneSharedInputChange = () => {
  _validateZoneSharedSummary()
}

const handleAddZoneBtnClick = () => {
  pushZones({
    share: 20,
    name: '',
  })
  // Validates the summary of zone shared
  _validateZoneSharedSummary()
}

const _validateZoneSharedSummary = () => {
  const summaryOfZoneShare = values.zones.reduce((acc, cur) => {
    acc += +cur.share

    return acc
  }, 0)
  hasValidZoneShareSummary.value = values.zones.length === 0 || summaryOfZoneShare === 100
}

const _validateDurationSummary = () => {
  const summaryOfDuration = values.executionProfileStages
    .map((executionProfileStage) => executionProfileStage.duration)
    .reduce((acc, cur) => {
      acc += +cur
      return acc
    }, 0)
  hasValidDurationSummary.value = summaryOfDuration <= maxDurationInMilliSeconds.value
}

const _validateMinionsSummary = () => {
  const summaryOfMinions = values.executionProfileStages
    .map((executionProfileStage) => executionProfileStage.minionsCount)
    .reduce((acc, cur) => {
      acc += +cur
      return acc
    }, 0)
  hasValidMinionsSummary.value = summaryOfMinions <= props.configuration.validation.maxMinionsCount
}

const _validateStartDurationAndDuration = (index: number, executionProfileStage: ExecutionProfileStage | null) => {
  // When the duration or start duration is not a number, return
  if (!executionProfileStage || isNaN(+executionProfileStage.startDuration) || isNaN(+executionProfileStage.duration))
    return

  const hasValidStartDuration = +executionProfileStage.startDuration <= +executionProfileStage.duration

  if (hasValidStartDuration) {
    invalidExecutionProfileIndexes.value = invalidExecutionProfileIndexes.value.filter((i) => i !== index)
  } else {
    if (!invalidExecutionProfileIndexes.value.some((i) => i === index)) {
      invalidExecutionProfileIndexes.value.push(index)
    }
  }
}

const _setScenarioConfigChartDataSeries = (executionProfileStages: ExecutionProfileStage[]) => {
  canChartBeRendered.value = false
  // Notes: Needs to add the timeout to rerender the chart
  setTimeout(() => {
    const chartData = ScenarioHelper.toScenarioConfigChartData(executionProfileStages)
    chartDataSeries.value = chartData.chartDataSeries
    chartOptions.value = chartData.chartOptions
    canChartBeRendered.value = true
  }, 100)
}
</script>

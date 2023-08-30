<template>
    <a-dropdown
        v-if="scenarioOptions"
          :trigger="['click']"
          placement="bottomRight"
        >
          <template #overlay>
            <a-menu class="scenario-menu">
              <a-menu-item 
                v-if="!hasOnlyOneScenarioOption"
                @click="handleSummaryOptionClick()"
                style="padding: 0"
                :key="scenarioSummaryId"
              >
                <div class="scenario-option flex items-center" :class="{ 'scenario-option--active': isScenarioSummarySelected }">
                  {{ scenarioSummaryName }}
                </div>
              </a-menu-item>
              <hr class="divide-line" />
              <span class="text-grey-1 text-sm pr-3 pl-3">Scenarios</span>
              <a-menu-item 
                v-for="scenario in scenarioOptions"
                :key="scenario.value"
                class="mb-1"
                style="padding: 0"
                @click="handleScenarioOptionClick(scenario)"
              >
                <div class="scenario-option flex items-center" :class="{ 'scenario-option--active': scenario.isActive}">
                  {{ scenario.label }}
                </div>
              </a-menu-item>
            </a-menu>
          </template>
          <a-button
            class="dropdown-menu-btn flex items-center"
            :disabled="hasOnlyOneScenarioOption">
            <div class="pr-2" :class="{ 'text-grey-1': hasOnlyOneScenarioOption }">
                {{ selectedScenarioNamesLabel }}
            </div>
            <BaseIcon
              icon="/icons/icon-arrow-down-light-black.svg"
              :class="{ 'icon-disabled': hasOnlyOneScenarioOption }"
              :width="18"
              :height="18"
            />
          </a-button>
    </a-dropdown>
</template>

<script setup lang="ts">
import { ScenarioOption } from 'utils/scenario';

const scenarioSummaryId = ScenarioHelper.SCENARIO_SUMMARY_ID
const scenarioSummaryName = ScenarioHelper.SCENARIO_SUMMARY_NAME;

const props = defineProps<{
    scenarioNames: string[],
    selectedScenarioNames: string[]
}>();
const emit = defineEmits<{
    (e: 'scenarioChange', v: string[]): void
}>()

const isScenarioSummarySelected = ref(false);
const hasOnlyOneScenarioOption = ref(false);
const scenarioOptions = ref<ScenarioOption[]>([]);
const selectedScenarioNames = ref<string[]>([]);
const selectedScenarioNamesLabel = ref('');

onMounted(() => {
    scenarioOptions.value = props.scenarioNames
        .map(scenarioName => ({
            label: scenarioName,
            value: scenarioName,
            isActive: props.selectedScenarioNames.includes(scenarioName)
        }));

    // Checks if the summary option should be displayed.
    hasOnlyOneScenarioOption.value = props.scenarioNames.length === 1;

    // Sets the selected scenario options to be the only one scenario option.
    if (hasOnlyOneScenarioOption.value) {
        selectedScenarioNames.value = [props.scenarioNames[0]];
        selectedScenarioNamesLabel.value = props.scenarioNames[0];
        return;
    }

    // Checks if the summary options should be selected
    isScenarioSummarySelected.value = props.selectedScenarioNames.length === props.scenarioNames.length;

    // Sets the selected scenario names text
    selectedScenarioNamesLabel.value = isScenarioSummarySelected.value ? scenarioSummaryName : props.selectedScenarioNames.join(',');
})

const handleSummaryOptionClick = () => {
    // Do nothing when the summary options is currently selected and the summary option is clicked
    if (isScenarioSummarySelected.value) return;

    isScenarioSummarySelected.value = true;
    selectedScenarioNames.value = props.scenarioNames;
    selectedScenarioNamesLabel.value = scenarioSummaryName;
    scenarioOptions.value.forEach(scenarioOption => scenarioOption.isActive = true)
    emit('scenarioChange', props.scenarioNames);
}

const handleScenarioOptionClick = (scenarioOption: ScenarioOption) => {
    // Do nothing when the last selected scenario option is going to be unselected.
    const isUnselectingLastOption = selectedScenarioNames.value.length === 1
        && scenarioOption.value === selectedScenarioNames.value[0]
    if (isUnselectingLastOption) return;
    
    scenarioOption.isActive = !scenarioOption.isActive;
    
    const activeScenarioNames = scenarioOptions.value
        .filter(scenarioOption => scenarioOption.isActive)
        .map(scenarioOption => scenarioOption.label);
    isScenarioSummarySelected.value = activeScenarioNames.length === props.scenarioNames.length;

    selectedScenarioNames.value = activeScenarioNames;
    selectedScenarioNamesLabel.value = isScenarioSummarySelected.value ? scenarioSummaryName : activeScenarioNames.join(',');

    emit('scenarioChange', activeScenarioNames)
}   
</script>

<style scoped lang="scss">
@import "../../assets/scss/color";
@import "../../assets/scss/variables";
.divide-line {
  margin: .25rem 0;
}
.dropdown-menu-btn {
  height: $item-height;
}

.scenario-option {
  height: 2rem;
  min-width: 14rem;
  position: relative;
  padding: .5rem .75rem;
  border-radius: $default-radius;

  &--active {
    background-color: rgba(0, 0, 0, 0.06);
    
    &:after {
      position: absolute;
      content: '';
      width: .5rem;
      height: .5rem;
      border-radius: 50%;
      right: 10px;
      top: auto;
      background-color: $primary-color;
    }
  }
}

</style>
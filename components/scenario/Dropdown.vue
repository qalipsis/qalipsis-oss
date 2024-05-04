<template>
  <Menu as="div" class="relative inline-block text-left">
    <MenuButton :disabled="hasOnlyOneScenarioOption" class="outline-none">
      <div 
        class="h-10 flex items-center"
        :class="{
          'cursor-not-allowed': hasOnlyOneScenarioOption
        }">
        <div class="pr-2" :class="{ 'text-gray-500': hasOnlyOneScenarioOption }">
          {{ selectedScenarioNamesLabel }}
        </div>
        <BaseIcon
          icon="/icons/icon-arrow-down-light-black.svg"
          :class="
            hasOnlyOneScenarioOption
              ? TailwindClassHelper.grayColorFilterClass
              : ''
          "
          :width="18"
          :height="18"
        />
      </div>
    </MenuButton>
    <transition
      enter-active-class="transition duration-100 ease-out"
      enter-from-class="transform scale-95 opacity-0"
      enter-to-class="transform scale-100 opacity-100"
      leave-active-class="transition duration-75 ease-in"
      leave-from-class="transform scale-100 opacity-100"
      leave-to-class="transform scale-95 opacity-0"
    >
      <MenuItems
        class="absolute right-0 w-72 rounded-md bg-white shadow-xl focus:outline-none p-2 z-10"
      >
        <MenuItem
          v-if="!hasOnlyOneScenarioOption"
          as="template"
        >
          <div
            @click="handleSummaryOptionClick()"
            class="flex items-center cursor-pointer"
            :class="[
              isScenarioSummarySelected
                ? TailwindClassHelper.scenarioDropdownItemActiveClass
                : '',
              TailwindClassHelper.scenarioDropdownItemClass,
            ]"
          >
            {{ scenarioSummaryName }}
          </div>
        </MenuItem>
        <div class="my-1">
          <BaseDivideLine />
        </div>
        <span class="text-gray-500 text-sm pl-2">Scenarios</span>
        <MenuItem
          v-for="scenario in scenarioOptions"
          :key="scenario.value"
          as="template"
        >
          <div
            class="flex items-center mb-1 cursor-pointer"
            :class="[
              scenario.isActive
                ? TailwindClassHelper.scenarioDropdownItemActiveClass
                : '',
              TailwindClassHelper.scenarioDropdownItemClass,
            ]"
            @click="handleScenarioOptionClick(scenario)"
          >
            {{ scenario.label }}
          </div>
        </MenuItem>
      </MenuItems>
    </transition>
  </Menu>
</template>

<script setup lang="ts">
 import { Menu, MenuButton, MenuItems, MenuItem } from '@headlessui/vue';

const scenarioSummaryId = ScenarioDetailsConfig.SCENARIO_SUMMARY_ID;
const scenarioSummaryName = ScenarioDetailsConfig.SCENARIO_SUMMARY_NAME;

const props = defineProps<{
  scenarioNames: string[];
  selectedScenarioNames: string[];
}>();
const emit = defineEmits<{
  (e: "scenarioChange", v: string[]): void;
}>();

const isScenarioSummarySelected = ref(false);
const hasOnlyOneScenarioOption = ref(false);
const scenarioOptions = ref<ScenarioOption[]>([]);
const selectedScenarioNames = ref<string[]>([]);
const selectedScenarioNamesLabel = ref("");

onMounted(() => {
  scenarioOptions.value = props.scenarioNames.map((scenarioName) => ({
    label: scenarioName,
    value: scenarioName,
    isActive: props.selectedScenarioNames.includes(scenarioName),
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
  isScenarioSummarySelected.value =
    props.selectedScenarioNames.length === props.scenarioNames.length;

  // Sets the selected scenario names text
  selectedScenarioNamesLabel.value = isScenarioSummarySelected.value
    ? scenarioSummaryName
    : props.selectedScenarioNames.join(",");
});

const handleSummaryOptionClick = () => {
  // Do nothing when the summary options is currently selected and the summary option is clicked
  if (isScenarioSummarySelected.value) return;

  isScenarioSummarySelected.value = true;
  selectedScenarioNames.value = props.scenarioNames;
  selectedScenarioNamesLabel.value = scenarioSummaryName;
  scenarioOptions.value.forEach(
    (scenarioOption) => (scenarioOption.isActive = true)
  );
  emit("scenarioChange", props.scenarioNames);
};

const handleScenarioOptionClick = (scenarioOption: ScenarioOption) => {
  // Do nothing when the last selected scenario option is going to be unselected.
  const isUnselectingLastOption =
    selectedScenarioNames.value.length === 1 &&
    scenarioOption.value === selectedScenarioNames.value[0];
  if (isUnselectingLastOption) return;

  scenarioOption.isActive = !scenarioOption.isActive;

  const activeScenarioNames = scenarioOptions.value
    .filter((scenarioOption) => scenarioOption.isActive)
    .map((scenarioOption) => scenarioOption.label);
  isScenarioSummarySelected.value =
    activeScenarioNames.length === props.scenarioNames.length;

  selectedScenarioNames.value = activeScenarioNames;
  selectedScenarioNamesLabel.value = isScenarioSummarySelected.value
    ? scenarioSummaryName
    : activeScenarioNames.join(",");

  emit("scenarioChange", activeScenarioNames);
};
</script>

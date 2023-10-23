<template>
  <BaseDrawer
    :open="open"
    :title="title"
    :width="800"
    :confirm-btn-disabled="
      dataSeries?.disabled || Object.keys(errors).length > 0
    "
    :confirm-btn-text="confirmBtnText"
    @close="emit('update:open', false)"
    @confirm-btn-click="handleConfirmBtnClick"
  >
    <a-form class="pl-2 pr-2 pt-2 pb-2">
      <a-row class="form-row">
        <a-col :span="24">
          <FormInput
            label="Name"
            form-control-name="name"
            :field-validation-schema="fieldValidationSchema.name"
            :disabled="dataSeries?.disabled"
          />
        </a-col>
      </a-row>
      <a-row class="form-row">
        <a-col :span="24">
          <FormSelect
            label="Sharing node"
            form-control-name="sharingMode"
            :field-validation-schema="fieldValidationSchema.sharingMode"
            :options="sharingModeOptions"
            :disabled="dataSeries?.disabled"
          />
        </a-col>
      </a-row>
      <a-row class="form-row">
        <a-col :span="8">
          <FormRadioGroup
            label="Time series type"
            form-control-name="dataType"
            :options="dataTypeOptions"
            :disabled="dataTypeOptionDisabled || dataSeries?.disabled"
            @change="handleDataTypeChange"
          />
        </a-col>
        <a-col :span="16">
          <FormAutoComplete
            v-if="hasValueNameFetched"
            form-control-name="valueName"
            :label="valueNameLabel"
            :options="valueNameOptions"
            :disabled="dataSeries?.disabled"
            :field-validation-schema="fieldValidationSchema.valueName"
          />
          <a-spin v-else></a-spin>
        </a-col>
      </a-row>
      <a-row class="form-row" :gutter="8">
        <a-col :span="8">
          <FormSelect
            v-if="hasFieldNameFetched"
            label="Field"
            form-control-name="fieldName"
            :options="fieldNameOptions"
            :disabled="dataSeries?.disabled"
            :field-validation-schema="fieldValidationSchema.fieldName"
            @change="handleFieldNameChange($event)"
          />
          <a-spin v-else></a-spin>
        </a-col>
        <a-col :span="8">
          <FormSelect
            label="Aggregation"
            form-control-name="aggregationOperation"
            :options="aggregationOperatorOptions"
            :disabled="
              aggregationOperationFieldDisabled || dataSeries?.disabled
            "
            :field-validation-schema="
              fieldValidationSchema.aggregationOperation
            "
          />
        </a-col>
        <a-col :span="8">
          <FormInputSelect
            label="Timeframe"
            form-input-control-name="timeframeValue"
            form-select-control-name="timeframeUnit"
            :input-field-validation-schema="
              fieldValidationSchema.timeframeValue
            "
            :select-field-validation-schema="
              fieldValidationSchema.timeframeUnit
            "
            :options="timeframeUnitOptions"
            :input-disabled="dataSeries?.disabled"
            :select-disabled="dataSeries?.disabled"
          />
        </a-col>
      </a-row>
      <a-row :gutter="8">
        <a-col :span="4">
          <label class="form-label">Color</label>
          <div style="position: relative">
            <div
              :class="
                dataSeries?.disabled ? 'cursor-not-allowed' : 'cursor-pointer'
              "
            >
              <div
                class="color-btn"
                :class="{ 'pointer-event-none': dataSeries?.disabled }"
                :style="{ backgroundColor: enrichedColorHexCode }"
                @click="handleColorBtnClick"
              ></div>
            </div>
            <div class="color-picker-wrapper">
              <BaseColorPicker
                v-model:open="colorPickerOpen"
                v-model:hexCodeValue="enrichedColorHexCode"
                :disabled="dataSeries?.disabled"
                @change="handleColorChange($event)"
              />
            </div>
          </div>
        </a-col>
        <a-col :span="4">
          <FormInput
            label="Hex"
            form-control-name="color"
            :disabled="dataSeries?.disabled"
            :field-validation-schema="fieldValidationSchema.color"
            @input="handleColorInput"
          />
        </a-col>
        <a-col :span="4">
          <FormInput
            label="Opacity"
            form-control-name="colorOpacity"
            :disabled="dataSeries?.disabled"
            :field-validation-schema="fieldValidationSchema.colorOpacity"
            @input="handleColorInput"
          />
        </a-col>
      </a-row>
      <hr class="divide-line" />
      <template v-if="hasTagFetched">
        <SeriesFormFilter
          v-for="(field, index) in fields"
          :key="field.key"
          :index="index"
          :tagMap="tagMap"
          :disabled="dataSeries?.disabled"
        />
        <a-row>
          <a-col :span="24">
            <BaseButton
              icon="/icons/icon-plus-grey.svg"
              btn-style="stroke"
              class="full-width"
              text="Add new filter"
              @click="handleAddNewFilterBtnClick"
              :disabled="dataSeries?.disabled"
            />
          </a-col>
        </a-row>
      </template>
      <a-spin v-else></a-spin>
    </a-form>
  </BaseDrawer>
</template>

<script setup lang="ts">
import { useForm, useFieldArray } from "vee-validate";
import { toTypedSchema } from "@vee-validate/zod";
import * as zod from "zod";

type Timer = string | number | NodeJS.Timeout | null | undefined;

const props = defineProps<{
  open: boolean;
  dataSeries?: DataSeriesTableData;
}>();
const emit = defineEmits<{
  (e: "dataSeriesUpdated"): void;
  (e: "update:open", v: boolean): void;
}>();

const {
  fetchValueNames,
  fetchFields,
  fetchTags,
  isValidDisplayName,
  updateDataSeries,
  createDataSeries,
} = useDataSeriesApi();

const initialFormValue: DataSeriesForm = {
  name: props.dataSeries?.displayName ?? "",
  sharingMode: props.dataSeries?.sharingMode ?? null,
  dataType: props.dataSeries?.dataType ?? DataTypeConstant.EVENTS,
  valueName: props.dataSeries?.valueName ?? "",
  fieldName: props.dataSeries?.fieldName ?? "",
  aggregationOperation: props.dataSeries?.aggregationOperation ?? null,
  timeframeValue: props.dataSeries?.formattedTimeframe.value ?? null,
  timeframeUnit:
    props.dataSeries?.formattedTimeframe.unit ?? TimeframeUnitConstant.MS,
  color: props.dataSeries?.color ?? ColorHelper.PRIMARY_COLOR_HEX_CODE,
  colorOpacity: props.dataSeries?.colorOpacity ?? 100,
  filters: props.dataSeries?.filters ?? [],
};
const { handleSubmit, setFieldValue, values, errors } = useForm<DataSeriesForm>(
  {
    initialValues: {
      name: props.dataSeries?.displayName ?? "",
      sharingMode: props.dataSeries?.sharingMode ?? null,
      dataType: props.dataSeries?.dataType ?? DataTypeConstant.EVENTS,
      valueName: props.dataSeries?.valueName ?? "",
      fieldName: props.dataSeries?.fieldName ?? "",
      aggregationOperation: props.dataSeries?.aggregationOperation ?? null,
      timeframeValue: props.dataSeries?.formattedTimeframe.value ?? null,
      timeframeUnit:
        props.dataSeries?.formattedTimeframe.unit ?? TimeframeUnitConstant.MS,
      color: props.dataSeries?.color ?? ColorHelper.PRIMARY_COLOR_HEX_CODE,
      colorOpacity: props.dataSeries?.colorOpacity ?? 100,
      filters: props.dataSeries?.filters ?? [],
    },
  }
);
/**
 * The push function and the fields from the filter field.
 */
const { push, fields } = useFieldArray<DataSeriesFilter>("filters");

const requiredErrorMessage = "is required";
const sharingModeOptions = SeriesHelper.getSharingModeOptions();
const dataTypeOptions = SeriesHelper.getTimeSeriesTypeOptions();
const timeframeUnitOptions = TimeframeHelper.getTimeframeUnitOptions();
const aggregationOperatorOptions = SeriesHelper.getAggregationOperatorOptions();
// @ts-ignore
const fieldValidationSchema = {
  name: toTypedSchema(
    zod
      .string()
      .nonempty(requiredErrorMessage)
      .min(3)
      .max(200)
      .nullable()
      .refine(
        (value: string | null) => {
          if (nameValidationTimer) clearTimeout(nameValidationTimer);

          return new Promise((resolve) => {
            nameValidationTimer = setTimeout(async () => {
              if (value && value.trim() !== props.dataSeries?.displayName) {
                const isValid = await isValidDisplayName(value.trim());
                resolve(isValid);
              }

              resolve(true);
            }, 200);
          });
        },
        {
          message: "This name has been reserved already",
        }
      )
  ),
  sharingMode: toTypedSchema(
    zod.string().nonempty(requiredErrorMessage).nullable()
  ),
  dataType: toTypedSchema(
    zod.string().nonempty(requiredErrorMessage).nullable()
  ),
  valueName: toTypedSchema(
    zod.string().nonempty(requiredErrorMessage).nullable()
  ),
  fieldName: toTypedSchema(
    zod
      .string()
      .nullable()
      .refine(
        (value) => {
          if (
            values.aggregationOperation ===
            QueryAggregationOperatorConstant.COUNT
          )
            return true;

          return value ? true : false;
        },
        {
          message: requiredErrorMessage,
        }
      )
  ),
  aggregationOperation: toTypedSchema(
    zod.string().nonempty(requiredErrorMessage).nullable()
  ),
  timeframeValue: toTypedSchema(
    zod.coerce
      .number({ invalid_type_error: "You must specify a number" })
      .min(0)
      .nullable()
      .refine((value) => value ? true : false, {
        message: "Number is required",
      })
  ),
  timeframeUnit: toTypedSchema(zod.string().nullable()),
  color: toTypedSchema(
    zod
      .string()
      .nullable()
      .refine((value) => ColorHelper.isValidHexCode(value as string))
  ),
  colorOpacity: toTypedSchema(
    zod.coerce
      .number({ invalid_type_error: "You must specify a number" })
      .min(1)
      .max(100, "Value must be between 1 and 100.")
      .nullable()
  ),
};

let dataFields: DataField[] = [];
let nameValidationTimer: Timer = null;
const valueNameOptions = ref<FormMenuOption[]>([]);
const fieldNameOptions = ref<FormMenuOption[]>([]);
const enrichedColorHexCode = ref(
  ColorHelper.enrichHexCodeWithOpacity(values.color, values.colorOpacity)
);
const aggregationOperationFieldDisabled = ref(false);
const tagMap = ref<{ [key: string]: string[] }>({});
const colorPickerOpen = ref(false);
const hasTagFetched = ref(false);
const hasValueNameFetched = ref(false);
const hasFieldNameFetched = ref(false);

const title = computed(() =>
  props.dataSeries ? "Update a series" : "Create a series"
);
const confirmBtnText = computed(() =>
  props.dataSeries ? "Save changes" : "Create"
);
const dataTypeOptionDisabled = computed(() =>
  props.dataSeries ? true : false
);
const valueNameLabel = computed(() =>
  values.dataType === DataTypeConstant.EVENTS ? "Event name" : "Meter name"
);

onMounted(async () => {
  const dataType = props.dataSeries?.dataType ?? DataTypeConstant.EVENTS;
  _prepareValueNameFieldOptions(dataType);
  _prepareTagMap(dataType);
  await _prepareFieldOptions(dataType);
  _shouldAggregationOperationFieldDisabled(values.fieldName);
});

onBeforeUnmount(() => {
  if (nameValidationTimer) clearTimeout(nameValidationTimer);
});

const handleColorInput = () => {
  if (errors.value.color || errors.value.colorOpacity) return;

  enrichedColorHexCode.value = ColorHelper.enrichHexCodeWithOpacity(
    values.color,
    values.colorOpacity
  );
};

const handleColorChange = (enrichedHexCodeWithOpacity: string) => {
  setFieldValue("color", ColorHelper.getHexCode(enrichedHexCodeWithOpacity));
  setFieldValue(
    "colorOpacity",
    ColorHelper.getOpacity(enrichedHexCodeWithOpacity)
  );
};

/**
 * Handles the field name change event.
 *
 * @param selectedFieldName The selected field name from the field options.
 */
const handleFieldNameChange = (selectedFieldName: string) => {
  _shouldAggregationOperationFieldDisabled(selectedFieldName);
};

const handleDataTypeChange = (newDataType: string) => {
  const dataType = newDataType as DataType;
  _prepareValueNameFieldOptions(dataType);
  _prepareFieldOptions(dataType);
  _prepareTagMap(dataType);
};

const handleColorBtnClick = () => {
  colorPickerOpen.value = true;
};

const handleAddNewFilterBtnClick = () => {
  push({
    name: "",
    value: "",
  });
};

const handleConfirmBtnClick = handleSubmit(async (values: DataSeriesForm) => {
  const isEditingSeries = props.dataSeries ? true : false;
  if (isEditingSeries) {
    _updateDataSeries(values);
  } else {
    _createDataSeries(values);
  }
});

const _updateDataSeries = async (formValues: DataSeriesForm) => {
  const dataSeriesPatchRequests = SeriesHelper.toDataSeriesPatchRequest(
    initialFormValue,
    formValues
  );

  if (dataSeriesPatchRequests.length === 0) {
    NotificationHelper.info("No changes detected");
    return;
  }

  try {
    await updateDataSeries(
      props.dataSeries!.reference,
      dataSeriesPatchRequests
    );
    emit("update:open", false);
    emit("dataSeriesUpdated");
    NotificationHelper.success(`${formValues.name} has been updated!`);
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
};

const _createDataSeries = async (formValues: DataSeriesForm) => {
  const dataSeriesPatchRequests =
    SeriesHelper.toDataSeriesCreationRequest(formValues);

  try {
    await createDataSeries(dataSeriesPatchRequests);
    emit("update:open", false);
    emit("dataSeriesUpdated");
    NotificationHelper.success(
      `${formValues.name} has been successfully created!`
    );
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
};

const _shouldAggregationOperationFieldDisabled = (fieldName: string) => {
  const selectedFieldOption = dataFields.find((f) => f.name === fieldName);
  if (selectedFieldOption?.type !== DataFieldTypeConstant.NUMBER) {
    setFieldValue(
      "aggregationOperation",
      QueryAggregationOperatorConstant.COUNT
    );
  }
  aggregationOperationFieldDisabled.value =
    selectedFieldOption?.type !== DataFieldTypeConstant.NUMBER ? true : false;
};

const _prepareTagMap = async (dataType: DataType) => {
  hasTagFetched.value = false;
  try {
    tagMap.value = await fetchTags(dataType);
    hasTagFetched.value = true;
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
};

const _prepareValueNameFieldOptions = async (dataType: DataType) => {
  hasValueNameFetched.value = false;
  try {
    const valueNames = await fetchValueNames(dataType);
    valueNameOptions.value = valueNames.map((valueName) => ({
      label: valueName,
      value: valueName,
    }));
    hasValueNameFetched.value = true;
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
};

const _prepareFieldOptions = async (dataType: DataType) => {
  hasFieldNameFetched.value = false;
  try {
    dataFields = await fetchFields(dataType);
    fieldNameOptions.value = dataFields.map((field) => ({
      label: field.name,
      value: field.name,
    }));
    hasFieldNameFetched.value = true;
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
};
</script>

<style scoped lang="scss">
@import "../../assets/scss/variables";

.color-btn {
  height: $item-height;
  border-radius: $default-radius;
}

.color-picker-wrapper {
  position: absolute;
  z-index: 1;
}

.form-row {
  height: 6rem;
}
</style>

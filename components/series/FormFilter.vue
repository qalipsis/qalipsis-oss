<template>
  <a-row :gutter="8" class="filter-row">
    <a-col :span="6">
      <FormAutoComplete
        label="Tag"
        :form-control-name="`filters[${index}].name`"
        :options="tagNameOptions"
        :disabled="disabled"
        :field-validation-schema="tagSchema.name"
        @select="handleTagSelect($event)"
      />
    </a-col>
    <a-col :span="7">
      <FormSelect
        label="Operations"
        :options="operatorOptions"
        :form-control-name="`filters[${index}].operator`"
        :field-validation-schema="tagSchema.operator"
        :disabled="disabled"
      />
    </a-col>
    <a-col :span="10">
      <FormAutoComplete
        label="Tag value"
        :options="tagValueOptions"
        :form-control-name="`filters[${index}].value`"
        :field-validation-schema="tagSchema.value"
        :disabled="disabled"
      />
    </a-col>
    <a-col :span="1">
      <div class="delete-btn-wrapper cursor-pointer" @click="remove(index)">
        <BaseIcon icon="/icons/icon-delete-small.svg" />
      </div>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { useFieldArray } from "vee-validate";
import { toTypedSchema } from "@vee-validate/zod";
import * as zod from "zod";

const props = defineProps<{
  /**
   * The index of the filter.
   */
  index: number;
  /**
   * The tags for the filter.
   */
  tagMap: { [key: string]: string[] };
  /**
   * A flag to indicate if the filters should be disabled
   */
  disabled?: boolean;
}>();

/**
 * The remove and push function, and the fields from the filter field.
 */
const { remove, fields } = useFieldArray<DataSeriesFilter>("filters");
const requiredErrorMessage = "is required";

const tagSchema = {
  name: toTypedSchema(zod.string().nonempty(requiredErrorMessage).nullable()),
  operator: toTypedSchema(
    zod.string().nonempty(requiredErrorMessage).nullable()
  ),
  value: toTypedSchema(zod.string().nonempty(requiredErrorMessage).nullable()),
};

/**
 * The tag name options.
 */
const tagNameOptions = computed<FormMenuOption[]>(() =>
  Object.keys(props.tagMap).map((t) => ({
    label: t,
    value: t,
  }))
);

const operatorOptions = SeriesHelper.getFilterOperatorOptions();
const tagValueOptions = ref<FormMenuOption[]>([]);

onMounted(() => {
  _prepareTagValueOptions(fields.value[props.index].value.name);
});

const handleTagSelect = (tagName: string) => {
  if (!tagName) return;

  _prepareTagValueOptions(tagName);
};

const _prepareTagValueOptions = (tagName: string) => {
  const tagValues = props.tagMap[tagName];
  tagValueOptions.value = tagValues
    ? tagValues.map((tv) => ({
        label: tv,
        value: tv,
      }))
    : [];
};
</script>

<style scoped lang="scss">
@import "../../assets/scss/color";

.delete-btn-wrapper {
  padding-top: 2.25rem;

  img {
    width: 1rem;
    height: 1rem;
  }

  img:hover {
    filter: $primary-color-svg;
  }
}

.filter-row {
  height: 5.5rem;
}
</style>

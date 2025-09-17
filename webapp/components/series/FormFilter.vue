<template>
  <div class="grid grid-cols-12 gap-2">
    <div class="col-span-3 h-24">
      <FormAutoComplete
          label="Tag"
          :form-control-name="`filters[${index}].name`"
          :options="tagNameOptions"
          :disabled="disabled"
          :field-validation-schema="tagSchema.name"
          @select="handleTagSelect($event)"
      />
    </div>
    <div class="col-span-4 h-24">
      <FormSelect
          label="Operations"
          :options="operatorOptions"
          :form-control-name="`filters[${index}].operator`"
          :field-validation-schema="tagSchema.operator"
          :disabled="disabled"
      />
    </div>
    <div class="col-span-5 h-24">
      <div class="flex items-center">
        <div class="flex-grow h-24">
          <FormAutoComplete
              label="Tag value"
              :options="tagValueOptions"
              :form-control-name="`filters[${index}].value`"
              :field-validation-schema="tagSchema.value"
              :disabled="disabled"
          />
        </div>
        <div v-if="!disabled" class="flex-shrink-0 pt-10 pl-2 cursor-pointer h-24" @click="remove(index)">
          <BaseIcon
              class="text-xl hover:text-primary-500 text-gray-700"
              icon="qls-icon-delete"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {useFieldArray} from "vee-validate";
import {toTypedSchema} from "@vee-validate/zod";
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
const {remove, fields} = useFieldArray<DataSeriesFilter>("filters");
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

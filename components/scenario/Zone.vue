<template>
  <a-row :gutter="8" class="mb-2">
    <a-col :span="16">
      <FormSelect
        label="Name"
        :form-control-name="`zones[${index}].name`"
        :options="zoneOptions"
        :field-validation-schema="zoneSchema.name"
      />
    </a-col>
    <a-col :span="7">
      <FormInput
        label="Share"
        suffix="%"
        :form-control-name="`zones[${index}].share`"
        :field-validation-schema="zoneSchema.share"
        @input="emit('zoneSharedInputChange', fields[index].value.share)"
      />
    </a-col>
    <a-col :span="1">
      <div
        class="cursor-pointer pt-7 scenario-form-delete-btn-wrapper"
        @click="remove(index)"
      >
        <BaseIcon icon="/icons/icon-delete-small.svg" />
      </div>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { toTypedSchema } from "@vee-validate/zod";
import { FormMenuOption } from "utils/form";
import { ZoneForm } from "utils/scenario";
import { useFieldArray } from "vee-validate";
import * as zod from "zod";

defineProps<{
  index: number;
  zoneOptions: FormMenuOption[];
  zoneForm?: ZoneForm;
}>();

const emit = defineEmits<{
  (e: "zoneSharedInputChange", v: number): void
}>()

const { remove, fields } = useFieldArray<ZoneForm>("zones");

const zoneSchema = {
  name: toTypedSchema(
    zod.string().nonempty("is required")
    .refine(
      (_) => {
        const selectedZoneNames = fields.value.map(v => v.value.name);
        // The newly selected zone name should be unique.
        return selectedZoneNames.length === new Set(selectedZoneNames).size;
      },
      {
        message: `You cannot have duplicated zones`,
      }
    )
  ),
  share: toTypedSchema(
    zod.coerce
      .number({ invalid_type_error: "You must specify a number" })
      .min(1)
      .max(100)
      .nullable()
  ),
};
</script>

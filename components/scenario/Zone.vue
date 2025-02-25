<template>
  <div class="grid grid-cols-3 gap-2 mb-2">
    <div class="col-span-2">
      <FormSelect
        label="Name"
        :form-control-name="`zones[${index}].name`"
        :options="zoneOptions"
        :field-validation-schema="zoneSchema.name"
      />
    </div>
    <div class="col-span-1">
      <div class="flex items-center">
        <div class="flex-grow">
          <FormInput
            label="Share"
            suffix="%"
            :form-control-name="`zones[${index}].share`"
            :field-validation-schema="zoneSchema.share"
            @input="emit('zoneSharedInputChange', fields[index].value.share)"
          />
        </div>
        <div
          class="flex-shrink-0 flex items-center pt-8 px-2 cursor-pointer  hover:text-primary-500 text-gray-600"
          @click="handleDeleteBtnClick"
        >
          <BaseIcon
            class="text-xl"
            icon="qls-icon-delete"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { toTypedSchema } from "@vee-validate/zod";
import { useFieldArray } from "vee-validate";
import * as zod from "zod";

const props = defineProps<{
  index: number;
  zoneOptions: FormMenuOption[];
  zoneForm?: ZoneForm;
}>();

const emit = defineEmits<{
  (e: "zoneSharedInputChange", v: number | null): void
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

const handleDeleteBtnClick = () => {
  remove(props.index);
  emit('zoneSharedInputChange', null)
}

</script>

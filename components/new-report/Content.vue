<template>
    <div class="page-content-container">
        <CampaignsTable 
            :rowSelectionEnabled="true"
            :name-clickable="false"
            :maxSelectedRows="10"
            :extraQueryParams="{
                'excluded-status': 'SCHEDULED'
            }"
        />
    </div>
</template>

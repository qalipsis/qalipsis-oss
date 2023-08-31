<template>
    <div class="page-content-container">
        <CampaignsTable 
            :rowSelectionEnabled="true"
            :name-clickable="false"
            :maxSelectedRows="10"
        />
    </div>
</template>

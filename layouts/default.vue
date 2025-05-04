<template>
    <section class="flex w-screen h-screen bg-white text-primary-900 dark:text-gray-100 dark:bg-gray-900">
      <Sidebar />
      <div class="max-h-screen overflow-y-auto flex-grow" >
        <slot />
      </div>
    </section>
</template>

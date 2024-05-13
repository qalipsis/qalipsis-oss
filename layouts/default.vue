<template>
    <section class="flex w-screen h-screen">
      <Sidebar />
      <div class="max-h-screen overflow-y-auto bg-white text-primary-950 flex-grow" >
        <slot />
      </div>
    </section>
</template>

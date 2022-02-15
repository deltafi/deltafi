<template>
  <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
    <h1 class="h2">
      {{ props.heading }}
    </h1>
    <slot />
  </div>
</template>

<script>
export default {
  name: "PageHeader",
  props: {
    heading: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    return { props };
  },
};
</script>

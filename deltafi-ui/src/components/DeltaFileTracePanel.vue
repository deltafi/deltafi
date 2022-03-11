<template>
  <div>
    <CollapsiblePanel id="traceCollapsible" class="trace-panel" header="Trace">
      <div id="traceChart" preserveAspectRatio="none" class="chart"></div>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import { reactive, defineProps, onMounted, computed } from "vue";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import * as d3 from "d3";
const timestampFormat = "YYYY-MM-DD HH:mm:ss.SSS";
const { formatTimestamp } = useUtilFunctions();

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const deltaFile = reactive(props.deltaFileData);

const actions = computed(() => {
  let actions = deltaFile.actions.map((action) => {
    const timeElapsed = new Date(action.modified) - new Date(action.created);
    action.created = new Date(action.created);
    action.modified = new Date(action.modified);
    action.end = new Date(action.created) - new Date(deltaFile.created) + timeElapsed;
    if (action.end === 0) {
      action.end = timeElapsed;
    }
    return {
      ...action,
      elapsed: timeElapsed,
    };
  });
  const total = new Date(deltaFile.modified) - new Date(deltaFile.created);
  actions.unshift({
    name: "DeltaFileFlow",
    elapsed: total,
    end: total,
    modified: deltaFile.modified,
    created: deltaFile.created,
  });
  return actions;
});

onMounted(() => {
  HorizontalWaterfallChart("#traceChart", actions.value);
});

const HorizontalWaterfallChart = (attachTo, data) => {
  const svgWidth = 500;
  const numTicks = 4;
  const rowWidth = 10; //Height of each row
  const leftMargin = 30;
  const lineHeight = data.length * rowWidth;
  const svgHeight = lineHeight + 18;
  const maxData = data[0].elapsed;
  // Append the chart and pad it a bit
  d3.select(attachTo).selectAll('svg').remove();
  let chart = d3.select(attachTo).append("svg").attr("class", "chart").attr("preserveAspectRatio", "none").attr("viewBox", `0 10 ${svgWidth} ${svgHeight - rowWidth}`);

  // create tooltip element
  const tooltip = d3.select("#traceCollapsible").append("div").attr("class", "d3-tooltip").style("position", "absolute").style("z-index", "10").style("visibility", "hidden");
  const showToolTip = (d, event) => {
    tooltip
      .html(`Action: ${d.name} </br> Created: ${formatTimestamp(new Date(d.created), timestampFormat)} </br> Modified:   ${formatTimestamp(new Date(d.modified), timestampFormat)}</br> Elapsed: ${d.elapsed}ms`)
      .style("visibility", "visible")
      .style("top", `${event.offsetY - 15}px`)
      .style("left", `${event.offsetX}px`);
  };
  const hideToolTip = () => {
    tooltip.html('').style("visibility", "hidden");
  };

  // Set the x-axis scale
  let x = d3.scale.linear().domain([0, maxData]).range(["0px", "400px"]);
  let yScale = d3.scale
    .linear()
    .domain([0, data.length])
    .range([svgHeight - 18, 0]);

  // The main graph area
  chart = chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 3)`)
    .attr("class", "gMainGraphArea");
  let yGridLine = d3.svg
    .axis()
    .scale(yScale)
    .tickSize(svgWidth, 0, 0)
    .tickFormat("")
    .tickValues([...Array(data.length).keys()])
    .orient("right")
    .ticks(data.length - 1);
  chart
    .append("g")
    .style("stroke", "#dddddd")
    .attr("transform", `translate(${leftMargin}, 10)`)
    .call(yGridLine);

  // Set the vertical lines for axis
  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 20)`)
    .selectAll("line")
    .data(x.ticks(numTicks))
    .enter()
    .append("line")
    .attr("x1", x)
    .attr("x2", x)
    .attr("y1", 0)
    .attr("y2", 0)
    .transition()
    .duration(1000)
    .attr("y2", lineHeight - 10)
    .style("stroke", "#dddddd");

  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 15)`)
    .selectAll("rect")
    .data(data)
    .enter()
    .append("rect")
    .attr("class", "rectWF")
    .attr("x", function (d) {
      return x(d.end - d.elapsed);
    })
    .attr("y", function (d, i) {
      return i * rowWidth + 1;
    })
    .attr("rx", 2)
    .attr("ry", 2)
    .attr("height", rowWidth - 2)
    .attr("width", 10)
    .attr("id", (d) => {
      return d.name;
    })
    .on("mouseover", function (d) {
      showToolTip(d, d3.event);
      d3.selectAll(".rectWF").style("opacity", 0.5);
      d3.select(this).style("opacity", 1);
    })
    .on("mouseout", function () {
      hideToolTip();
      d3.selectAll(".rectWF").style("opacity", 1);
    })
    .transition()
    .duration(1000)
    .attr("width", function (d) {
      return x(d.elapsed);
    });

  // Set the values on the bars

  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 15)`)
    .selectAll("text")
    .data(data)
    .enter()
    .append("text")
    .attr("class", "bar")
    .on("mouseover", function (d) {
      showToolTip(d, d3.event);
      d3.selectAll(".rectWF").style("opacity", 0.5);
      d3.select(`#${d.name}`).style("opacity", 1);
    })
    .attr("x", function (d) {
      return x(d.end - d.elapsed / 2);
    })
    .attr("y", function (d, i) {
      return i * rowWidth + rowWidth * 0.5;
    })
    .attr("dy", "1") // vertical-align: middle
    .attr("text-anchor", "middle") // text-align: right
    .text(function (d) {
      return d.elapsed + "ms";
    });

  // Set the numbering on the lines for axis
  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 15)`)
    .selectAll(".rule")
    .data(x.ticks(numTicks))
    .enter()
    .append("text")
    .attr("class", "rule")
    .attr("x", x)
    .attr("y", 0)
    .attr("dy", -3)
    .attr("text-anchor", "middle")
    .text(String);

  let ll = chart.append("g").attr("class", "gAxis");

  ll.selectAll("text")
    .data(data)
    .enter()
    .append("text")
    .attr("x", leftMargin - 0)
    .attr("y", function (d, i) {
      return i * rowWidth + rowWidth * 0.8;
    })
    .attr("dx", -2) // padding-right
    .attr("dy", 13) // vertical-align: middle
    .attr("text-anchor", "end") // text-align: right
    .text(function (d) {
      return d.name;
    });
};
</script>

<style lang="scss">
@import "@/styles/components/deltafile-trace-panel.scss";
</style>

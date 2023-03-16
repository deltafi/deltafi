<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

<template>
  <div>
    <CollapsiblePanel id="traceCollapsible" class="trace-panel" header="Trace">
      <div id="traceChart" preserveAspectRatio="none" class="chart"></div>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import { ref, defineProps, onMounted, computed, watch } from "vue";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import * as d3 from "d3";
import dayjs from "dayjs";
import _ from "lodash";

const timestampFormat = "YYYY-MM-DD HH:mm:ss.SSS";
const { formatTimestamp } = useUtilFunctions();

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const deltaFile = ref(JSON.parse(JSON.stringify(props.deltaFileData)));

const deltaFileActions = computed(() => {
  let actions = deltaFile.value.actions.map((action) => {
    if (action.queued !== null) {
      action.created = new Date(action.queued);
      action.createdOrginal = new Date(action.queued);
    } else {
      action.created = new Date(action.created);
      action.createdOrginal = new Date(action.created);
    }
    const timeElapsed = new Date(action.modified) - new Date(action.created);
    action.modified = new Date(action.modified);
    action.modifiedOrginal = new Date(action.modified);
    action.end = new Date(action.created) - new Date(deltaFile.value.created) + timeElapsed;
    action.stop = new Date(action.stop);
    action.stopOrignal = new Date(action.stop);
    action.start = new Date(action.start);
    action.startOrignal = new Date(action.start);
    const startTimeElapsed = new Date(action.stop) - new Date(action.start);
    action.startTimeElapsed = startTimeElapsed > 0 ? startTimeElapsed : 0.1;
    action.startTimeElapsedOrginal = startTimeElapsed;
    action.startEnd = new Date(action.start) - new Date(deltaFile.value.created) + action.startTimeElapsed;
    if (action.end === 0) {
      action.end = timeElapsed;
    }

    return {
      ...action,
      elapsed: timeElapsed,
    };
  });
  if (actions.some((action) => action.state === "RETRIED")) {
    actions = handleRetried(actions);
  }
  let total = new Date(Math.max(...actions.map((e) => new Date(e.modified)))) - new Date(deltaFile.value.created);

  actions.unshift({
    name: "DeltaFileFlow",
    elapsed: total,
    end: total,
    modified: actions[actions.length - 1].modifiedOrginal,
    created: deltaFile.value.createdOrginal,
  });
  return actions;
});

const handleRetried = (newActions) => {
  let retriedActions = [
    {
      name: "",
      modified: Date(),
    },
  ];
  for (let x in newActions) {
    if (newActions[x].state === "RETRIED" && !retriedActions.some((retried) => retried.name === newActions[x].name)) {
      retriedActions.push({ name: newActions[x].name, modified: newActions[x].modified });
    } else {
      if (retriedActions.some((retried) => retried.name === newActions[x].name)) {
        const retriedIndex = retriedActions.findIndex((retried) => retried.name === newActions[x].name);
        const shiftTime = new Date(newActions[x].created) - new Date(retriedActions[retriedIndex].modified);
        newActions[x].start = new Date(dayjs(newActions[x].start).subtract(shiftTime, "millisecond")).toISOString();
        newActions[x].stop = new Date(dayjs(newActions[x].stop).subtract(shiftTime, "millisecond")).toISOString();
        newActions[x].created = retriedActions[retriedIndex].modified;
        newActions[x].modified = new Date(dayjs(newActions[x].created).add(newActions[x].elapsed, "millisecond")).toISOString();
        newActions[x].end = new Date(newActions[x].created) - new Date(deltaFile.value.created) + newActions[x].elapsed;
        newActions[x].startEnd = new Date(newActions[x].start) - new Date(deltaFile.value.created) + newActions[x].startTimeElapsed;
        retriedActions.splice(retriedIndex, 1);
      }
      if (newActions[x].state === "RETRIED") {
        retriedActions.push({ name: newActions[x].name, modified: newActions[x].modified });
      }
    }
  }
  return newActions;
};
onMounted(() => {
  HorizontalWaterfallChart("#traceChart", deltaFileActions.value);
});

watch(props.deltaFileData, () => {
  deltaFile.value = JSON.parse(JSON.stringify(props.deltaFileData));
  HorizontalWaterfallChart("#traceChart", deltaFileActions.value);
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
  d3.select(attachTo).selectAll("svg").remove();
  let chart = d3
    .select(attachTo)
    .append("svg")
    .attr("class", "chart")
    .attr("preserveAspectRatio", "none")
    .attr("viewBox", `0 10 ${svgWidth} ${svgHeight - rowWidth}`);

  // create tooltip element
  const tooltip = d3.select("#traceCollapsible").append("div").attr("class", "d3-tooltip").style("position", "absolute").style("z-index", "2000").style("visibility", "hidden");
  const showToolTip = (d, event) => {
    let tipString = d.name !== "IngressAction" && d.name !== "DeltaFileFlow" ? `Action: ${d.name} </br> Queued: ${formatTimestamp(d.createdOrginal, timestampFormat)} </br> Modified:   ${formatTimestamp(d.modifiedOrginal, timestampFormat)}</br> Total Elapsed: ${d.elapsed}ms </br> Action Start: ${formatTimestamp(d.startOrignal, timestampFormat)} </br> Action Stop: ${formatTimestamp(d.stopOrignal, timestampFormat)} </br> Action Elapsed: ${d.startTimeElapsedOrginal}ms ` : `Action: ${d.name} </br> Queued: ${formatTimestamp(d.createdOrginal, timestampFormat)} </br> Modified:   ${formatTimestamp(d.modifiedOrginal, timestampFormat)}</br> Total Elapsed: ${d.elapsed}ms`;
    tooltip
      .html(tipString)
      .style("visibility", "visible")
      .style("top", `${event.offsetY - 14}px`)
      .style("left", `${event.offsetX}px`);
  };
  const hideToolTip = () => {
    tooltip.html("").style("visibility", "hidden");
  };

  // Set the x-axis scale
  let x = d3.scale.linear().domain([0, maxData]).range(["0px", "400px"]);
  let yScale = d3.scale
    .linear()
    .domain([0, data.length])
    .range([svgHeight - 18, 0]);

  // The main graph area
  chart = chart.append("g").attr("transform", `translate(${leftMargin}, 3)`).attr("class", "gMainGraphArea");
  let yGridLine = d3.svg
    .axis()
    .scale(yScale)
    .tickSize(svgWidth, 0, 0)
    .tickFormat("")
    .tickValues([...Array(data.length).keys()])
    .orient("right")
    .ticks(data.length - 1);
  chart.append("g").style("stroke", "#dddddd").attr("transform", `translate(${leftMargin}, 10)`).call(yGridLine);

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
    .attr("class", function (d) {
      if (_.isEqual(d.state, "ERROR")) {
        return "error-bar";
      } else if (_.isEqual(d.state, "RETRIED") || _.isEqual(d.state, "FILTERED")) {
        return "warning-bar";
      } else {
        return "normal-bar";
      }
    })
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
    })
    .transition()
    .duration(1000)
    .attr("width", function (d) {
      return x(d.elapsed);
    });

  // add start stop
  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 15)`)
    .selectAll("rect")
    .data(data)
    .enter()
    .append("rect")
    .attr("class", "rectWF")
    .attr("class", function (d) {
      if (_.isEqual(d.name, "DeltaFileFlow")) {
        return "normal-bar";
      } else {
        return "start-stop-bar";
      }
    })
    .attr("x", function (d) {
      return d.name !== "DeltaFileFlow" && d.name !== "IngressAction" ? x(d.startEnd - d.startTimeElapsed) : x(0);
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
    })
    .on("mouseout", function () {
      d3.selectAll(".rectWF").style("opacity", 0)
      hideToolTip();
    })
    .transition()
    .duration(1000)
    .attr("width", function (d) {
      return d.name !== "DeltaFileFlow" && d.name !== "IngressAction" ? x(d.startTimeElapsed) : x(0);
    });

  // Set the values on the bars

  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 15)`)
    .selectAll("text")
    .data(data)
    .enter()
    .append("text")
    .attr("class", function (d) {
      if (_.isEqual(d.state, "ERROR")) {
        return "error-bar";
      } else if (_.isEqual(d.state, "RETRIED") || _.isEqual(d.state, "FILTERED")) {
        return "warning-bar";
      } else {
        return "normal-bar";
      }
    })
    .on("mouseover", function (d) {
      showToolTip(d, d3.event);
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
  chart.append("g").attr("transform", `translate(${leftMargin}, 15)`).selectAll(".rule").data(x.ticks(numTicks)).enter().append("text").attr("class", "rule").attr("x", x).attr("y", 0).attr("dy", -3).attr("text-anchor", "middle").text(String);

  let ll = chart.append("g").attr("class", "gAxis");

  ll.selectAll("text")
    .data(data)
    .enter()
    .append("text")
    .attr("x", leftMargin - 0)
    .attr("y", function (d, i) {
      return i * rowWidth + rowWidth * 0.8;
    })
    .attr("dx", -5) // padding-right
    .attr("dy", 13) // vertical-align: middle
    .attr("text-anchor", "end") // text-align: right
    .text(function (d) {
      return d.name.split("", 24).reduce((o, c) => (o.length === 23 ? `${o}${c}...` : `${o}${c}`), "");
    });
};
</script>

<style lang="scss">
@import "@/styles/components/deltafile-trace-panel.scss";
</style>

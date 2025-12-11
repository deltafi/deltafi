<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
    <component :is="hideHeader ? 'div' : CollapsiblePanel" id="traceCollapsible" class="trace-panel" :header="hideHeader ? undefined : 'Trace'">
      <div id="traceChart" preserveAspectRatio="none" class="chart" />
    </component>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from "vue";
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
  hideHeader: {
    type: Boolean,
    default: false,
  },
});

const deltaFileFlows = computed(() => {
  const flows = [];
  deltaFile.value.flows.forEach((action) => {
    action.actions.forEach((a) => {
      flows.push({
        dataSource: action.name,
        ...a,
      });
    });
  });
  return flows;
});

const deltaFile = ref(JSON.parse(JSON.stringify(props.deltaFileData)));

const deltaFileActions = computed(() => {
  let actions = deltaFileFlows.value.map((action) => {
    if (action.queued !== null) {
      action.created = new Date(action.queued);
      action.createdOriginal = new Date(action.queued);
    } else {
      action.created = new Date(action.created);
      action.createdOriginal = new Date(action.created);
    }
    const timeElapsed = new Date(action.modified) - new Date(action.created);
    action.modified = new Date(action.modified);
    action.modifiedOriginal = new Date(action.modified);
    action.end = new Date(action.created) - new Date(deltaFile.value.created) + timeElapsed;
    action.stop = new Date(action.stop);
    action.stopOriginal = new Date(action.stop);
    action.start = new Date(action.start);
    action.startOriginal = new Date(action.start);
    const startTimeElapsed = new Date(action.stop) - new Date(action.start);
    action.startTimeElapsed = startTimeElapsed > 0 ? startTimeElapsed : 0.1;
    action.startTimeElapsedOriginal = startTimeElapsed;
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
  const total = new Date(Math.max(...actions.map((e) => new Date(e.modified)))) - new Date(deltaFile.value.created);

  actions.unshift({
    name: "DeltaFileFlow",
    elapsed: total,
    end: total,
    modified: actions[actions.length - 1].modifiedOriginal,
    created: deltaFile.value.createdOriginal,
  });
  return actions;
});

const handleRetried = (newActions) => {
  const retriedActions = [
    {
      name: "",
      modified: Date(),
    },
  ];
  for (const x in newActions) {
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
  const rowWidth = 10;
  const leftMargin = 30;
  const lineHeight = data.length * rowWidth;
  const svgHeight = lineHeight + 18;
  const maxData = data[0].elapsed;

  d3.select(attachTo).selectAll("svg").remove();
  const svg = d3
    .select(attachTo)
    .append("svg")
    .attr("class", "chart")
    .attr("preserveAspectRatio", "none")
    .attr("viewBox", `0 10 ${svgWidth} ${svgHeight - rowWidth}`);

  const tooltip = d3.select("#traceCollapsible").append("div").attr("class", "d3-tooltip").style("position", "absolute").style("z-index", "2000").style("visibility", "hidden");

  const showToolTip = (event, d) => {
    const tipString = d.name !== "IngressAction" && d.name !== "DeltaFileFlow" ? `Data Source: ${d.dataSource} </br> Action: ${d.name} </br> Queued: ${formatTimestamp(d.createdOriginal, timestampFormat)} </br> Modified:   ${formatTimestamp(d.modifiedOriginal, timestampFormat)}</br> Total Elapsed: ${d.elapsed}ms </br> Action Start: ${formatTimestamp(d.startOriginal, timestampFormat)} </br> Action Stop: ${formatTimestamp(d.stopOriginal, timestampFormat)} </br> Action Elapsed: ${d.startTimeElapsedOriginal}ms ` : d.name !== "DeltaFileFlow" ? `Data Source: ${d.dataSource} </br> Action: ${d.name} </br> Queued: ${formatTimestamp(d.createdOriginal, timestampFormat)} </br> Modified:   ${formatTimestamp(d.modifiedOriginal, timestampFormat)}</br> Total Elapsed: ${d.elapsed}ms` : `Action: ${d.name} </br> Queued: ${formatTimestamp(d.createdOriginal, timestampFormat)} </br> Modified:   ${formatTimestamp(d.modifiedOriginal, timestampFormat)}</br> Total Elapsed: ${d.elapsed}ms`;
    tooltip
      .html(tipString)
      .style("visibility", "visible")
      .style("top", `${event.offsetY - 14}px`)
      .style("left", `${event.offsetX}px`);
  };

  const hideToolTip = () => {
    tooltip.html("").style("visibility", "hidden");
  };

  const x = d3.scaleLinear().domain([0, maxData]).range([0, 400]);
  const yScale = d3
    .scaleLinear()
    .domain([0, data.length])
    .range([svgHeight - 18, 0]);

  const chart = svg.append("g").attr("transform", `translate(${leftMargin}, 3)`).attr("class", "gMainGraphArea");

  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 10)`)
    .call(
      d3
        .axisRight(yScale)
        .tickSize(svgWidth)
        .tickFormat("")
        .tickValues([...Array(data.length).keys()])
    )
    .selectAll(".tick line")
    .attr("stroke", "#dddddd"); // Changes x-axis color line color to gray

  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 20)`)
    .selectAll("line")
    .data(x.ticks(numTicks))
    .enter()
    .append("line")
    .attr("x1", (d) => x(d))
    .attr("x2", (d) => x(d))
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
    .attr("class", (d) => (d.state === "ERROR" ? "error-bar" : d.state === "RETRIED" || d.state === "FILTERED" ? "warning-bar" : "normal-bar"))
    .attr("x", (d) => x(d.end - d.elapsed))
    .attr("y", (d, i) => i * rowWidth + 1)
    .attr("rx", 2)
    .attr("ry", 2)
    .attr("height", rowWidth - 2)
    .attr("width", 10)
    .on("mouseover", showToolTip)
    .on("mouseout", hideToolTip)
    .transition()
    .duration(1000)
    .attr("width", (d) => x(d.elapsed));

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
    .on("mouseover", showToolTip)
    .on("mouseout", hideToolTip)
    .attr("id", (d) => {
      return d.name;
    })
    .transition()
    .duration(1000)
    .attr("width", function (d) {
      return d.name !== "DeltaFileFlow" && d.name !== "IngressAction" ? x(d.startTimeElapsed) : x(0);
    });

  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 15)`)
    .selectAll("text")
    .data(data)
    .enter()
    .append("text")
    .attr("class", (d) => (d.state === "ERROR" ? "error-bar" : d.state === "RETRIED" || d.state === "FILTERED" ? "warning-bar" : "normal-bar"))
    .on("mouseover", showToolTip)
    .attr("x", (d) => x(d.end - d.elapsed / 2))
    .attr("y", (d, i) => i * rowWidth + rowWidth * 0.5)
    .attr("dy", "1")
    .attr("text-anchor", "middle")
    .text((d) => `${d.elapsed}ms`);

  chart
    .append("g")
    .attr("transform", `translate(${leftMargin}, 15)`)
    .selectAll(".rule")
    .data(x.ticks(numTicks))
    .enter()
    .append("text")
    .attr("class", "rule")
    .attr("x", (d) => x(d))
    .attr("y", 0)
    .attr("dy", -3)
    .attr("text-anchor", "middle")
    .text(String);

  chart
    .append("g")
    .attr("class", "gAxis")
    .selectAll("text")
    .data(data)
    .enter()
    .append("text")
    .attr("x", leftMargin)
    .attr("y", (d, i) => i * rowWidth + rowWidth * 0.8)
    .attr("dx", -5)
    .attr("dy", 13)
    .attr("text-anchor", "end")
    .text((d) => (d.name.length > 24 ? d.name.slice(0, 23) + "..." : d.name));
};
</script>

<style>
.trace-panel {
  .domain {
    display: none !important;
  }

  .chart {
    font: 0.3rem sans-serif;

    rect {
      stroke: #888;
      stroke-width: 0.2;
      stroke-linejoin: round;
    }

    rect.normal-bar {
      fill: #b8daff;
    }

    rect.warning-bar {
      fill: #ffeeba;
    }

    rect.error-bar {
      fill: #f5c6cb;
    }

    rect.start-stop-bar {
      fill: #fff;
      opacity: 0.45;
    }

    text {
      fill: #333333;
    }
  }

  .d3-tooltip {
    position: absolute;
    text-align: left;
    padding: 8px;
    margin-top: -20px;
    font: 1rem sans-serif;
    background: #333333;
    pointer-events: none;
    color: white;
    border-radius: 5px;
  }

  .gMainGraphArea {
    margin: 0;
    padding: 0;
  }
}
</style>

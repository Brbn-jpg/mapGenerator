<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import type { MapRecord } from "../api/maps";
import type { BiomeId } from "../constants/biomes";
import { useTerrainBuffer } from "../composables/useTerrainBuffer";
import { useDrawing, type BrushType } from "../composables/useDrawing";
import { useMapStream } from "../composables/useMapStream";
import { useKeyboardShortcuts } from "../composables/useKeyboardShortcuts";
import MapSidebar from "./MapSidebar.vue";
import MapCanvas from "./MapCanvas.vue";

const seed = ref(Math.floor(Math.random() * 100000));
const size = ref(1000);
const temp = ref(0.2);
const moisture = ref(0.0);
const continent = ref(1.2);
const city = ref(0.1);

const terrain = useTerrainBuffer();
const drawing = useDrawing();

type BiomeStat = { id: number; count: number; pct: number };
const biomeStats = ref<BiomeStat[]>([]);

const stream = useMapStream({
  onInit: (s) => { terrain.init(s); biomeStats.value = []; },
  onChunk: (cx, cy, ch, csz) => terrain.ingestChunk(cx, cy, ch, csz),
  onDone: () => { biomeStats.value = terrain.getBiomeCounts(); },
});

const generate = () =>
  stream.generate({
    size: size.value,
    seed: seed.value,
    temp: temp.value,
    moisture: moisture.value,
    continent: continent.value,
    city: city.value,
  });

const loadMap = (record: MapRecord) => stream.loadExisting(record);

const toggleHighlight = (id: BiomeId) => {
  terrain.highlightedBiome.value = terrain.highlightedBiome.value === id ? null : id;
};

const exportPng = () => {
  const w = stream.currentRenderSize.value;
  const tmp = document.createElement("canvas");
  tmp.width = w;
  tmp.height = w;
  const tctx = tmp.getContext("2d")!;
  tctx.fillStyle = "#143d70";
  tctx.fillRect(0, 0, w, w);
  tctx.drawImage(terrain.landCanvas, 0, 0);
  drawing.renderPaths(tctx);
  tmp.toBlob((blob) => {
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `map-${stream.mapId.value}.png`;
    a.click();
    URL.revokeObjectURL(url);
  });
};

const mapCanvasRef = ref<InstanceType<typeof MapCanvas> | null>(null);
const BRUSH_ORDER: BrushType[] = ["pen", "marker", "dashed", "eraser"];

useKeyboardShortcuts({
  randomizeSeed: () => (seed.value = Math.floor(Math.random() * 100000)),
  toggleDrawMode: () => (drawing.isDrawMode.value = !drawing.isDrawMode.value),
  toggleTopology: () => (terrain.showTopology.value = !terrain.showTopology.value),
  undoDrawing: () => drawing.undo(),
  clearDrawing: () => drawing.clear(),
  generate: () => {
    if (!stream.loading.value) generate();
  },
  resetCamera: () => mapCanvasRef.value?.camera.reset(),
  selectBrush: (i) => {
    const t = BRUSH_ORDER[i];
    if (t) {
      drawing.brushType.value = t;
      if (!drawing.isDrawMode.value) drawing.isDrawMode.value = true;
    }
  },
});

onMounted(() => stream.refreshHistory());
onUnmounted(() => stream.closeStream());
</script>

<template>
  <div class="map-generator">
    <MapSidebar
      :seed="seed"
      :size="size"
      :temp="temp"
      :moisture="moisture"
      :continent="continent"
      :city="city"
      :show-topology="terrain.showTopology.value"
      :is-draw-mode="drawing.isDrawMode.value"
      :draw-color="drawing.drawColor.value"
      :brush-type="drawing.brushType.value"
      :brush-width="drawing.brushWidth.value"
      :drawn-paths-count="drawing.drawnPaths.value.length"
      :loading="stream.loading.value"
      :progress="stream.progress.value"
      :map-id="stream.mapId.value"
      :highlighted-biome="terrain.highlightedBiome.value"
      :previous-maps="stream.previousMaps.value"
      :biome-stats="biomeStats"
      @update:seed="seed = $event"
      @update:size="size = $event"
      @update:temp="temp = $event"
      @update:moisture="moisture = $event"
      @update:continent="continent = $event"
      @update:city="city = $event"
      @update:show-topology="terrain.showTopology.value = $event"
      @update:is-draw-mode="drawing.isDrawMode.value = $event"
      @update:draw-color="drawing.drawColor.value = $event"
      @update:brush-type="drawing.brushType.value = $event"
      @update:brush-width="drawing.brushWidth.value = $event"
      @generate="generate"
      @clear-drawing="drawing.clear"
      @undo-drawing="drawing.undo"
      @export-png="exportPng"
      @load-map="loadMap"
      @toggle-highlight="toggleHighlight"
    />
    <MapCanvas
      ref="mapCanvasRef"
      :terrain="terrain"
      :drawing="drawing"
      :render-size="stream.currentRenderSize.value"
    />
  </div>
</template>

<style scoped>
.map-generator {
  display: flex;
  width: 100vw;
  height: 100vh;
  background-color: var(--bg-color, #0b0f19);
  color: var(--text-primary, #e2e8f0);
  overflow: hidden;
  font-family: "Inter", system-ui, sans-serif;
}
</style>

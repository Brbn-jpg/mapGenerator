<script setup lang="ts">
import { computed } from "vue";
import type { MapRecord } from "../api/maps";
import type { BiomeId } from "../constants/biomes";
import { BRUSH_LABELS, type BrushType } from "../composables/useDrawing";
import { generateMapName } from "../utils/mapName";
import BiomeLegend from "./BiomeLegend.vue";
import MapHistory from "./MapHistory.vue";

const props = defineProps<{
  seed: number;
  size: number;
  temp: number;
  moisture: number;
  continent: number;
  city: number;
  showTopology: boolean;
  isDrawMode: boolean;
  drawColor: string;
  brushType: BrushType;
  brushWidth: number;
  drawnPathsCount: number;
  loading: boolean;
  progress: number;
  mapId: string;
  highlightedBiome: BiomeId | null;
  previousMaps: MapRecord[];
}>();

const emit = defineEmits<{
  (e: "update:seed", v: number): void;
  (e: "update:size", v: number): void;
  (e: "update:temp", v: number): void;
  (e: "update:moisture", v: number): void;
  (e: "update:continent", v: number): void;
  (e: "update:city", v: number): void;
  (e: "update:showTopology", v: boolean): void;
  (e: "update:isDrawMode", v: boolean): void;
  (e: "update:drawColor", v: string): void;
  (e: "update:brushType", v: BrushType): void;
  (e: "update:brushWidth", v: number): void;
  (e: "generate"): void;
  (e: "clearDrawing"): void;
  (e: "undoDrawing"): void;
  (e: "exportPng"): void;
  (e: "loadMap", record: MapRecord): void;
  (e: "toggleHighlight", id: BiomeId): void;
}>();

const randomizeSeed = () => emit("update:seed", Math.floor(Math.random() * 100000));

const brushOptions: BrushType[] = ["pen", "marker", "dashed", "eraser"];
const showLagWarning = computed(() => props.size >= 2000);
const mapName = computed(() => props.mapId ? generateMapName(props.seed) : null);
</script>

<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <h1>MapGen</h1>
      <p class="subtitle">Procedural Generation</p>
      <Transition name="map-name">
        <p v-if="mapName" class="map-name">{{ mapName }}</p>
      </Transition>
    </div>

    <div class="controls">
      <div class="control-group">
        <label>Map Size</label>
        <input
          :value="props.size"
          @input="emit('update:size', Number(($event.target as HTMLInputElement).value))"
          type="number"
          min="10"
          max="10000"
          class="dark-input"
        />
        <div v-if="showLagWarning" class="warning-box">
          <strong>⚠ Large maps may lag your browser.</strong>
          For sizes around 10000×10000 enable hardware acceleration in your browser settings (Chrome → Settings → System → "Use graphics acceleration when available"). Generation streams in chunks but rendering at high zoom can still stall the main thread.
        </div>
      </div>

      <div class="control-group">
        <label>World Seed</label>
        <div class="seed-row">
          <input
            :value="props.seed"
            @input="emit('update:seed', Number(($event.target as HTMLInputElement).value))"
            type="number"
            class="dark-input seed-input"
          />
          <button class="btn btn-icon" @click="randomizeSeed" title="Randomize seed">&#9880;</button>
        </div>
      </div>

      <div class="divider"></div>

      <div class="control-group slider-group">
        <label>Temperature <span class="slider-value">{{ props.temp.toFixed(2) }}</span></label>
        <p class="slider-hint">Global climate offset. Lower → more tundra, taiga and frozen ocean. Higher → more savanna, desert and badlands.</p>
        <input
          :value="props.temp"
          @input="emit('update:temp', Number(($event.target as HTMLInputElement).value))"
          type="range" min="-0.5" max="0.5" step="0.01" class="dark-slider"
        />
      </div>

      <div class="control-group slider-group">
        <label>Moisture <span class="slider-value">{{ props.moisture.toFixed(2) }}</span></label>
        <p class="slider-hint">Humidity bias. Lower → deserts, steppe and bare rock dominate. Higher → forests, jungles and swamps spread.</p>
        <input
          :value="props.moisture"
          @input="emit('update:moisture', Number(($event.target as HTMLInputElement).value))"
          type="range" min="-0.5" max="0.5" step="0.01" class="dark-slider"
        />
      </div>

      <div class="control-group slider-group">
        <label>Continent <span class="slider-value">{{ props.continent.toFixed(2) }}</span></label>
        <p class="slider-hint">Landmass shape. Below 1 → scattered archipelagos and small islands. Above 1.5 → large connected continents.</p>
        <input
          :value="props.continent"
          @input="emit('update:continent', Number(($event.target as HTMLInputElement).value))"
          type="range" min="0.5" max="2" step="0.01" class="dark-slider"
        />
      </div>

      <div class="control-group slider-group">
        <label>City Density <span class="slider-value">{{ props.city.toFixed(2) }}</span></label>
        <p class="slider-hint">Probability that habitable land becomes a city tile. 0 → wilderness only, 0.5 → densely settled world.</p>
        <input
          :value="props.city"
          @input="emit('update:city', Number(($event.target as HTMLInputElement).value))"
          type="range" min="0.0" max="0.5" step="0.01" class="dark-slider"
        />
      </div>

      <div class="divider"></div>

      <div class="control-group switch-group">
        <label class="switch-label">
          <span class="toggle-text">Show Topology</span>
          <div class="toggle-switch">
            <input
              :checked="props.showTopology"
              @change="emit('update:showTopology', ($event.target as HTMLInputElement).checked)"
              type="checkbox"
            />
            <span class="slider"></span>
          </div>
        </label>
      </div>

      <div class="divider"></div>

      <template v-if="props.isDrawMode">
        <div class="control-group">
          <label>Brush Type</label>
          <div class="brush-grid">
            <button
              v-for="b in brushOptions"
              :key="b"
              class="brush-chip"
              :class="{ 'is-active': props.brushType === b }"
              @click="emit('update:brushType', b)"
            >
              {{ BRUSH_LABELS[b] }}
            </button>
          </div>
        </div>

        <div class="control-group slider-group">
          <label>Brush Width <span class="slider-value">{{ props.brushWidth }} px</span></label>
          <input
            :value="props.brushWidth"
            @input="emit('update:brushWidth', Number(($event.target as HTMLInputElement).value))"
            type="range" min="1" max="32" step="1" class="dark-slider"
          />
        </div>

        <div class="control-group" v-if="props.brushType !== 'eraser'">
          <label>Brush Color</label>
          <div class="color-picker-wrapper">
            <input
              :value="props.drawColor"
              @input="emit('update:drawColor', ($event.target as HTMLInputElement).value)"
              type="color"
            />
            <span class="color-value">{{ props.drawColor }}</span>
          </div>
        </div>
      </template>

      <div class="action-buttons">
        <button
          class="btn btn-mode"
          :class="{ 'is-active': props.isDrawMode }"
          @click="emit('update:isDrawMode', !props.isDrawMode)"
        >
          {{ props.isDrawMode ? "Drawing Mode" : "Drag Mode" }}
        </button>

        <button
          class="btn btn-mode"
          @click="emit('undoDrawing')"
          v-if="props.drawnPathsCount > 0"
          title="Undo last stroke"
        >
          Undo
        </button>

        <button
          class="btn btn-danger"
          @click="emit('clearDrawing')"
          v-if="props.drawnPathsCount > 0"
        >
          Clear
        </button>
      </div>

      <button
        class="btn btn-primary generate-btn"
        @click="emit('generate')"
        :disabled="props.loading"
      >
        <span v-if="props.loading" class="loader"></span>
        <span v-else>Generate New World</span>
      </button>

      <div v-if="props.loading" class="progress-wrap">
        <div class="progress-bar" :style="{ width: props.progress + '%' }"></div>
        <span class="progress-label">{{ props.progress }}%</span>
      </div>

      <div v-if="props.mapId && !props.loading" class="map-info-box">
        <div class="info-label">Active Map ID</div>
        <div class="info-value">{{ props.mapId }}</div>
        <div class="info-hint">Use mouse to drag & scroll to zoom</div>
        <button class="btn btn-export" @click="emit('exportPng')">Export PNG</button>
      </div>

      <MapHistory
        :items="props.previousMaps"
        :active-id="props.mapId"
        @select="(r) => emit('loadMap', r)"
      />

      <details class="shortcuts">
        <summary>Keyboard Shortcuts</summary>
        <ul>
          <li><kbd>R</kbd> randomize seed</li>
          <li><kbd>G</kbd> generate</li>
          <li><kbd>Space</kbd> toggle draw / drag</li>
          <li><kbd>1</kbd>–<kbd>4</kbd> brush: pen / marker / dashed / eraser</li>
          <li><kbd>Ctrl</kbd>+<kbd>Z</kbd> undo</li>
          <li><kbd>Del</kbd> clear drawing</li>
          <li><kbd>H</kbd> toggle topology</li>
          <li><kbd>Esc</kbd> reset camera</li>
        </ul>
      </details>

      <BiomeLegend
        :highlighted="props.highlightedBiome"
        @toggle="(id) => emit('toggleHighlight', id)"
      />
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  width: 320px;
  min-width: 320px;
  background: var(--panel-bg, rgba(20, 25, 35, 0.95));
  border-right: 1px solid var(--panel-border, rgba(255, 255, 255, 0.05));
  display: flex;
  flex-direction: column;
  padding: 24px;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.4);
  z-index: 10;
  overflow-y: auto;
}
.sidebar-header { margin-bottom: 32px; }
.sidebar-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #60a5fa, #a78bfa);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: -0.5px;
}
.subtitle {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: var(--text-secondary, #94a3b8);
  text-transform: uppercase;
  letter-spacing: 1px;
}
.map-name {
  margin: 10px 0 0 0;
  font-size: 15px;
  font-weight: 600;
  font-style: italic;
  color: #a78bfa;
  letter-spacing: 0.2px;
}
.map-name-enter-active { transition: opacity 0.5s ease, transform 0.5s ease; }
.map-name-enter-from { opacity: 0; transform: translateY(-6px); }
.map-name-leave-active { transition: opacity 0.3s ease; }
.map-name-leave-to { opacity: 0; }
.controls { display: flex; flex-direction: column; gap: 20px; }
.control-group { display: flex; flex-direction: column; gap: 8px; }
.control-group label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary, #94a3b8);
}
.dark-input {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--panel-border, rgba(255, 255, 255, 0.1));
  color: white;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 15px;
  outline: none;
  transition: all 0.2s ease;
}
.dark-input:focus {
  border-color: var(--accent-color, #3b82f6);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}
.switch-group { margin-top: 4px; }
.switch-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  width: 100%;
}
.toggle-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #e2e8f0);
}
.toggle-switch { position: relative; width: 44px; height: 24px; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.slider {
  position: absolute;
  cursor: pointer;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: rgba(255, 255, 255, 0.1);
  transition: 0.3s;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 2px;
  bottom: 2px;
  background-color: white;
  transition: 0.3s;
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}
input:checked + .slider {
  background-color: var(--accent-color, #3b82f6);
  border-color: var(--accent-color, #3b82f6);
}
input:checked + .slider:before { transform: translateX(20px); }
.divider {
  height: 1px;
  background: var(--panel-border, rgba(255, 255, 255, 0.08));
  margin: 4px 0;
}
.color-picker-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--panel-border, rgba(255, 255, 255, 0.1));
  padding: 8px 12px;
  border-radius: 8px;
}
.color-picker-wrapper input[type="color"] {
  appearance: none;
  -webkit-appearance: none;
  border: none;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  padding: 0;
  background: none;
}
.color-picker-wrapper input[type="color"]::-webkit-color-swatch-wrapper { padding: 0; }
.color-picker-wrapper input[type="color"]::-webkit-color-swatch {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
}
.color-value {
  font-family: monospace;
  font-size: 13px;
  color: var(--text-secondary, #94a3b8);
}
.action-buttons { display: flex; gap: 10px; }
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 8px;
  border: none;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  flex: 1;
}
.btn-mode {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-primary, #e2e8f0);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.btn-mode:hover { background: rgba(255, 255, 255, 0.1); }
.btn-mode.is-active {
  background: rgba(236, 72, 153, 0.15);
  color: #f472b6;
  border-color: rgba(236, 72, 153, 0.3);
}
.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: #fca5a5;
  border: 1px solid rgba(239, 68, 68, 0.3);
  flex: 0.8;
}
.btn-danger:hover { background: rgba(239, 68, 68, 0.25); }
.generate-btn {
  background: var(--accent-color, #3b82f6);
  color: white;
  padding: 16px;
  font-size: 16px;
  margin-top: 12px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}
.generate-btn:hover:not(:disabled) {
  background: var(--accent-hover, #2563eb);
  transform: translateY(-1px);
}
.generate-btn:disabled {
  background: #334155;
  color: #94a3b8;
  cursor: not-allowed;
  box-shadow: none;
}
.loader {
  width: 20px;
  height: 20px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-bottom-color: white;
  border-radius: 50%;
  display: inline-block;
  box-sizing: border-box;
  animation: rotation 1s linear infinite;
}
@keyframes rotation {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
.map-info-box {
  background: rgba(0, 0, 0, 0.3);
  border-radius: 8px;
  padding: 16px;
  margin-top: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.info-label {
  font-size: 11px;
  color: var(--text-secondary, #94a3b8);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.info-value {
  font-family: monospace;
  font-size: 14px;
  color: #60a5fa;
  word-break: break-all;
  margin-bottom: 12px;
}
.info-hint {
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 6px;
}
.info-hint::before { content: "ℹ️"; font-size: 14px; }
.seed-row { display: flex; gap: 8px; align-items: center; }
.seed-input { flex: 1; }
.btn-icon {
  flex: none;
  width: 40px;
  height: 42px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e2e8f0;
  border-radius: 8px;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}
.btn-icon:hover { background: rgba(255, 255, 255, 0.12); }
.progress-wrap {
  position: relative;
  height: 8px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  overflow: hidden;
}
.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #a78bfa);
  border-radius: 4px;
  transition: width 0.3s ease;
}
.progress-label {
  position: absolute;
  right: 0;
  top: -18px;
  font-size: 11px;
  color: #94a3b8;
}
.btn-export {
  margin-top: 10px;
  width: 100%;
  padding: 8px;
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.3);
  color: #a5b4fc;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-export:hover { background: rgba(99, 102, 241, 0.25); }
.slider-group label {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.slider-value {
  font-family: monospace;
  font-size: 13px;
  color: #60a5fa;
}
.slider-hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.4;
  color: #64748b;
}
.warning-box {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(234, 179, 8, 0.1);
  border: 1px solid rgba(234, 179, 8, 0.3);
  font-size: 11px;
  line-height: 1.5;
  color: #fde68a;
}
.warning-box strong {
  display: block;
  margin-bottom: 4px;
  color: #fde047;
  font-weight: 600;
}
.brush-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.brush-chip {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #cbd5e1;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.brush-chip:hover {
  background: rgba(255, 255, 255, 0.1);
}
.brush-chip.is-active {
  background: rgba(96, 165, 250, 0.15);
  border-color: rgba(96, 165, 250, 0.5);
  color: #93c5fd;
}
.shortcuts > summary {
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  padding: 4px 0;
  list-style: none;
}
.shortcuts > summary::-webkit-details-marker { display: none; }
.shortcuts > summary::before {
  content: "▸";
  display: inline-block;
  margin-right: 6px;
  transition: transform 0.15s;
}
.shortcuts[open] > summary::before { transform: rotate(90deg); }
.shortcuts > summary:hover { color: #e2e8f0; }
.shortcuts ul {
  list-style: none;
  padding: 6px 0 0 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.shortcuts li {
  font-size: 11px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 8px;
}
.shortcuts kbd {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  padding: 1px 6px;
  font-family: monospace;
  font-size: 10px;
  color: #e2e8f0;
  min-width: 14px;
  text-align: center;
}
.dark-slider {
  appearance: none;
  -webkit-appearance: none;
  width: 100%;
  height: 4px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.1);
  outline: none;
  cursor: pointer;
}
.dark-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #3b82f6;
  cursor: pointer;
  box-shadow: 0 0 4px rgba(59, 130, 246, 0.5);
  transition: background 0.2s;
}
.dark-slider::-webkit-slider-thumb:hover { background: #60a5fa; }
</style>

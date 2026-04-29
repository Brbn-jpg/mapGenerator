<script setup lang="ts">
import { BIOME_COLORS, BIOME_NAMES, type BiomeId } from "../constants/biomes";

const props = defineProps<{ highlighted: BiomeId | null }>();
const emit = defineEmits<{ (e: "toggle", id: BiomeId): void }>();
</script>

<template>
  <details class="legend">
    <summary>Biome Legend</summary>
    <div class="legend-list">
      <div
        v-for="(name, id) in BIOME_NAMES"
        :key="id"
        class="legend-item"
        :class="{
          'is-highlighted': props.highlighted === Number(id),
          'is-dimmed': props.highlighted !== null && props.highlighted !== Number(id),
        }"
        @click="emit('toggle', Number(id))"
      >
        <span class="legend-swatch" :style="{ background: BIOME_COLORS[Number(id)] ?? '#888' }"></span>
        <span class="legend-name">{{ name }}</span>
      </div>
    </div>
  </details>
</template>

<style scoped>
.legend > summary {
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  padding: 4px 0;
  list-style: none;
}
.legend > summary::-webkit-details-marker { display: none; }
.legend > summary::before {
  content: "▸";
  display: inline-block;
  margin-right: 6px;
  transition: transform 0.15s;
}
.legend[open] > summary::before { transform: rotate(90deg); }
.legend > summary:hover { color: #e2e8f0; }
.legend-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  max-height: 260px;
  overflow-y: auto;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: opacity 0.15s, background 0.15s;
}
.legend-item:hover {
  background: rgba(255, 255, 255, 0.05);
}
.legend-item.is-highlighted {
  background: rgba(96, 165, 250, 0.15);
  outline: 1px solid rgba(96, 165, 250, 0.4);
}
.legend-item.is-dimmed {
  opacity: 0.35;
}
.legend-swatch {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.legend-name {
  font-size: 12px;
  color: #cbd5e1;
}
</style>

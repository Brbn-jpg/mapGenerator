<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from "vue";
import { BIOME_NAMES, type BiomeId } from "../constants/biomes";
import { useCamera } from "../composables/useCamera";
import { useDrawing } from "../composables/useDrawing";
import { useWaveRenderer } from "../composables/useWaveRenderer";
import { renderCities } from "../composables/renderCities";
import type { useTerrainBuffer } from "../composables/useTerrainBuffer";

type Terrain = ReturnType<typeof useTerrainBuffer>;

const props = defineProps<{
  terrain: Terrain;
  drawing: ReturnType<typeof useDrawing>;
  renderSize: number;
}>();

const VIEWPORT_W = 1000;
const VIEWPORT_H = 800;

const canvasRef = ref<HTMLCanvasElement | null>(null);
const camera = useCamera(canvasRef, {
  viewportW: VIEWPORT_W,
  viewportH: VIEWPORT_H,
  initialZoom: 0.6,
  initialOffsetX: VIEWPORT_W / 2 - 300,
  initialOffsetY: VIEWPORT_H / 2 - 300,
});

const waves = useWaveRenderer(3);

// Off-screen canvas for user-drawn paths so the eraser can punch holes
// without cutting through the underlying terrain.
const pathsCanvas = document.createElement("canvas");
pathsCanvas.width = VIEWPORT_W;
pathsCanvas.height = VIEWPORT_H;
const pathsCtx = pathsCanvas.getContext("2d")!;

const hoveredBiome = ref<string | null>(null);
const hoveredCoords = ref<{ x: number; y: number } | null>(null);

defineExpose({ canvasRef, camera });

const onMouseDown = (e: MouseEvent) => {
  if (props.drawing.isDrawMode.value) {
    camera.isDragging.value = true;
    props.drawing.beginPath(
      camera.getWorldCoords(e.clientX, e.clientY),
      camera.zoom.value,
    );
  } else {
    camera.beginPan(e.clientX, e.clientY);
  }
};

const onMouseMove = (e: MouseEvent) => {
  const { x, y } = camera.getWorldCoords(e.clientX, e.clientY);
  const wx = Math.floor(x);
  const wy = Math.floor(y);
  const tileId: BiomeId | null = props.terrain.biomeAt(wx, wy);
  if (tileId !== null) {
    hoveredBiome.value = BIOME_NAMES[tileId] ?? null;
    hoveredCoords.value = { x: wx, y: wy };
  } else {
    hoveredBiome.value = null;
    hoveredCoords.value = null;
  }

  if (!camera.isDragging.value) return;
  if (props.drawing.isDrawMode.value) {
    props.drawing.extendPath(camera.getWorldCoords(e.clientX, e.clientY));
  } else {
    camera.updatePan(e.clientX, e.clientY);
  }
};

const onMouseUp = () => {
  if (camera.isDragging.value && props.drawing.isDrawMode.value) {
    props.drawing.commitPath();
  }
  camera.endPan();
};

const onMouseLeave = () => {
  hoveredBiome.value = null;
  hoveredCoords.value = null;
};

let rafId: number | null = null;
let t = 0;

const animate = () => {
  t += 0.01;
  const ctx = canvasRef.value?.getContext("2d");
  if (ctx) {
    ctx.fillStyle = "#050a14";
    ctx.fillRect(0, 0, VIEWPORT_W, VIEWPORT_H);
    ctx.save();
    ctx.translate(camera.offsetX.value, camera.offsetY.value);
    ctx.scale(camera.zoom.value, camera.zoom.value);

    const w = props.renderSize;
    if (w > 0) {
      ctx.beginPath();
      ctx.rect(0, 0, w, w);
      ctx.clip();

      // Waves
      waves.tick(w, t);
      ctx.drawImage(waves.waveCanvas, 0, 0);

      // Terrain
      props.terrain.flushLand();
      ctx.drawImage(props.terrain.landCanvas, 0, 0);

      // Highlight
      if (props.terrain.highlightedBiome.value !== null) {
        props.terrain.flushHighlight();
        ctx.fillStyle = "rgba(0,0,0,0.7)";
        ctx.fillRect(0, 0, w, w);
        ctx.drawImage(props.terrain.highlightCanvas, 0, 0);
      }

      // Cities
      renderCities(ctx, props.terrain.getCities(), camera.zoom.value);
    }
    ctx.restore();

    // User-drawn paths on a dedicated layer so the eraser only removes path pixels.
    pathsCtx.setTransform(1, 0, 0, 1, 0, 0);
    pathsCtx.clearRect(0, 0, VIEWPORT_W, VIEWPORT_H);
    pathsCtx.setTransform(
      camera.zoom.value, 0, 0, camera.zoom.value,
      camera.offsetX.value, camera.offsetY.value,
    );
    props.drawing.renderPaths(pathsCtx);
    pathsCtx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.drawImage(pathsCanvas, 0, 0);
  }
  rafId = requestAnimationFrame(animate);
};

onMounted(() => {
  if (canvasRef.value) {
    canvasRef.value.width = VIEWPORT_W;
    canvasRef.value.height = VIEWPORT_H;
  }
  rafId = requestAnimationFrame(animate);
});

onUnmounted(() => {
  if (rafId) cancelAnimationFrame(rafId);
});

watch(
  () => props.terrain.showTopology.value,
  () => props.terrain.rebuildStaticMap(),
);
watch(
  () => props.terrain.highlightedBiome.value,
  () => props.terrain.rebuildHighlight(),
);
</script>

<template>
  <div class="canvas-wrapper">
    <div
      class="canvas-container"
      :class="{
        'is-drawing': props.drawing.isDrawMode.value,
        'is-dragging': camera.isDragging.value && !props.drawing.isDrawMode.value,
      }"
    >
      <canvas
        ref="canvasRef"
        @mousedown="onMouseDown"
        @mousemove="onMouseMove"
        @mouseup="onMouseUp"
        @mouseleave="onMouseLeave"
        @wheel="camera.onWheel"
        :style="{
          width: VIEWPORT_W + 'px',
          height: VIEWPORT_H + 'px',
          imageRendering: 'pixelated',
        }"
      ></canvas>
      <div v-if="hoveredBiome" class="hover-info">
        <span class="hover-biome">{{ hoveredBiome }}</span>
        <span class="hover-coords" v-if="hoveredCoords">{{ hoveredCoords.x }}, {{ hoveredCoords.y }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.canvas-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at center, #1e293b 0%, #0f172a 100%);
  position: relative;
}
.canvas-wrapper::before {
  content: "";
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-image: radial-gradient(rgba(255,255,255,0.1) 1px, transparent 1px);
  background-size: 24px 24px;
  pointer-events: none;
}
.canvas-container {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: #050a14;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 24px 48px rgba(0,0,0,0.5), 0 0 0 1px rgba(255,255,255,0.05);
  position: relative;
  z-index: 1;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.canvas-container:hover {
  box-shadow: 0 32px 64px rgba(0,0,0,0.6), 0 0 0 1px rgba(255,255,255,0.1);
}
.is-drawing canvas { cursor: crosshair !important; }
.is-dragging canvas { cursor: grabbing !important; }
canvas { max-width: 100%; display: block; }
.hover-info {
  position: absolute;
  bottom: 12px; left: 12px;
  background: rgba(10,15,28,0.85);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px;
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  pointer-events: none;
  backdrop-filter: blur(4px);
}
.hover-biome { font-size: 13px; font-weight: 600; color: #e2e8f0; }
.hover-coords { font-family: monospace; font-size: 11px; color: #64748b; }
</style>

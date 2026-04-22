<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from "vue";
import axios from "axios";

const seed = ref(Math.floor(Math.random() * 100000));
const size = ref(1000); 
const currentRenderSize = ref(1000); 

const mapId = ref("");
const loading = ref(false);
const showTopology = ref(true);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const CHUNK_SIZE = 32;

// Viewport dimensions
const VIEWPORT_W = 800;
const VIEWPORT_H = 600;

// Camera state
const zoom = ref(0.6);
const offsetX = ref(VIEWPORT_W / 2 - 300);
const offsetY = ref(VIEWPORT_H / 2 - 300);
const isDragging = ref(false);
let lastMouseX = 0;
let lastMouseY = 0;

// === DRAWING STATE ===
const isDrawMode = ref(false); 
const drawColor = ref("#ff3366");
const drawnPaths = ref<{ points: { x: number; y: number }[]; color: string }[]>([]); 
let currentPath: { x: number; y: number }[] = []; 

const getWorldCoords = (clientX: number, clientY: number) => {
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return { x: 0, y: 0 };
  const mouseX = clientX - rect.left;
  const mouseY = clientY - rect.top;
  return {
    x: (mouseX - offsetX.value) / zoom.value,
    y: (mouseY - offsetY.value) / zoom.value,
  };
};

// Animation state
const animationFrameId = ref<number | null>(null);
const time = ref(0);

// Global map buffer and city list
const mapBuffer = ref<Uint32Array | null>(null);
let imgData: ImageData | null = null;
let citiesList: { x: number; y: number }[] = [];

// Off-screen canvas for pre-rendering static terrain
const landCanvas = document.createElement("canvas");
const landCtx = landCanvas.getContext("2d", { willReadFrequently: true });

const colors: Record<number, string> = {
  0: "#000033", 1: "#000080", 2: "#1E90FF", 3: "#A5F2F3",
  4: "#FFF8DC", 5: "#F4A460", 6: "#8B7D6B", 7: "#556B2F",
  8: "#F0F8FF", 9: "#BDB76B", 10: "#2F4F4F", 11: "#C2B280",
  12: "#7CFC00", 13: "#228B22", 14: "#2E8B57", 15: "#EDC9AF",
  16: "#E6DAA6", 17: "#8FBC8F", 18: "#004B49", 19: "#8B4513",
  20: "#696969", 21: "#A9A9A9", 22: "#FFFFFF", 23: "#FF1493",
};

const parsedColors = Object.entries(colors).reduce(
  (acc, [id, hex]) => {
    acc[Number(id)] = {
      r: parseInt(hex.slice(1, 3), 16),
      g: parseInt(hex.slice(3, 5), 16),
      b: parseInt(hex.slice(5, 7), 16),
    };
    return acc;
  },
  {} as Record<number, { r: number; g: number; b: number }>,
);

// === CAMERA & DRAWING CONTROLS ===
const onMouseDown = (e: MouseEvent) => {
  isDragging.value = true;
  if (isDrawMode.value) {
    currentPath = [getWorldCoords(e.clientX, e.clientY)];
  } else {
    lastMouseX = e.clientX;
    lastMouseY = e.clientY;
  }
};

const onMouseMove = (e: MouseEvent) => {
  if (!isDragging.value) return;

  if (isDrawMode.value) {
    currentPath.push(getWorldCoords(e.clientX, e.clientY));
  } else {
    const dx = e.clientX - lastMouseX;
    const dy = e.clientY - lastMouseY;
    offsetX.value += dx;
    offsetY.value += dy;
    lastMouseX = e.clientX;
    lastMouseY = e.clientY;
  }
};

const onMouseUp = () => {
  if (isDragging.value && isDrawMode.value && currentPath.length > 0) {
    drawnPaths.value.push({ points: [...currentPath], color: drawColor.value });
    currentPath = [];
  }
  isDragging.value = false;
};

const clearDrawing = () => {
  drawnPaths.value = [];
};

const onWheel = (e: WheelEvent) => {
  e.preventDefault();
  const scaleFactor = e.deltaY > 0 ? 0.9 : 1.1;

  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return;
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;

  const worldX = (mouseX - offsetX.value) / zoom.value;
  const worldY = (mouseY - offsetY.value) / zoom.value;

  zoom.value *= scaleFactor;
  zoom.value = Math.max(0.1, Math.min(5, zoom.value));

  offsetX.value = mouseX - worldX * zoom.value;
  offsetY.value = mouseY - worldY * zoom.value;
};

// === ANIMATION LOOP ===
const startAnimation = () => {
  if (animationFrameId.value) return;

  const animate = () => {
    time.value += 0.01;
    const ctx = canvasRef.value?.getContext("2d");

    if (ctx && mapBuffer.value && mapBuffer.value.length > 0) {
      // 1. Clear Viewport
      ctx.fillStyle = "#050a14";
      ctx.fillRect(0, 0, VIEWPORT_W, VIEWPORT_H);

      ctx.save();
      // Apply camera transform
      ctx.translate(offsetX.value, offsetY.value);
      ctx.scale(zoom.value, zoom.value);

      const w = currentRenderSize.value;

      // Clip drawing to map bounds
      ctx.beginPath();
      ctx.rect(0, 0, w, w);
      ctx.clip();
      
      const scale = w / 1000;
      const step = Math.max(2, 32 * scale);
      const margin = 40 * scale;

      // 2. Render procedural water waves
      ctx.fillStyle = "#143d70"; 
      ctx.fillRect(0, 0, w, w);

      ctx.lineWidth = Math.max(0.5, 2 * scale);
      ctx.strokeStyle = "#0d2c54";

      for (let y = -margin; y <= w + margin; y += step) {
        ctx.beginPath();
        ctx.moveTo(0, y + margin);

        for (let x = 0; x <= w; x += step) {
          const smoothBase = Math.sin(x * (0.015 / scale) + time.value + y * (0.015 / scale));
          const sharpPeaks = 1 - Math.abs(Math.sin(x * (0.08 / scale) - time.value * 1.3 + y * (0.2 / scale)));

          const wave = (smoothBase * 3 + sharpPeaks * 8) * scale;
          const jitterX = Math.sin(x * (1.5 / scale) + y * (2.1 / scale)) * (1.2 * scale);
          const jitterY = Math.cos(x * (2.3 / scale) - y * (1.7 / scale)) * (1.2 * scale);
          ctx.lineTo(x + jitterX, y - wave + jitterY + margin);
        }

        // Close wave path within map bounds
        ctx.lineTo(w, y + step + margin);
        ctx.lineTo(0, y + step + margin);
        ctx.closePath();

        const isAlternate = Math.round(y / step) % 2 === 0;
        ctx.fillStyle = isAlternate ? "#1a5094" : "#1d5ba8";
        ctx.fill();
        ctx.stroke();
      }

      // 3. Overlay static terrain island
      ctx.drawImage(landCanvas, 0, 0);

      // 4. Draw cities on top
      if (citiesList.length > 0) {
        ctx.fillStyle = colors[23];
        citiesList.forEach((city) =>
          ctx.fillRect(city.x - 2, city.y - 2, 5, 5),
        );
      }

      // 5. DRAW USER PATHS
      ctx.lineCap = "round";
      ctx.lineJoin = "round";

      const drawPath = (points: { x: number; y: number }[]) => {
        if (points.length < 2) return;
        ctx.beginPath();
        ctx.moveTo(points[0].x, points[0].y);
        for (let i = 1; i < points.length; i++) {
          ctx.lineTo(points[i].x, points[i].y);
        }
        ctx.stroke();
      };

      drawnPaths.value.forEach((item) => {
        ctx.lineWidth = 4 / zoom.value;
        ctx.strokeStyle = item.color;
        drawPath(item.points);
      });

      if (currentPath.length > 0) {
        ctx.lineWidth = 4 / zoom.value;
        ctx.strokeStyle = drawColor.value;
        drawPath(currentPath);
      }

      ctx.restore();
    }
    animationFrameId.value = requestAnimationFrame(animate);
  };
  animationFrameId.value = requestAnimationFrame(animate);
};

const stopAnimation = () => {
  if (animationFrameId.value) {
    cancelAnimationFrame(animationFrameId.value);
    animationFrameId.value = null;
  }
};

onMounted(() => {
  if (canvasRef.value) {
    canvasRef.value.width = VIEWPORT_W;
    canvasRef.value.height = VIEWPORT_H;
  }
  startAnimation();
});
onUnmounted(() => stopAnimation());

const generateMap = async () => {
  loading.value = true;
  mapId.value = "";
  currentRenderSize.value = size.value;

  mapBuffer.value = new Uint32Array(currentRenderSize.value * currentRenderSize.value);
  citiesList = [];
  landCanvas.width = currentRenderSize.value;
  landCanvas.height = currentRenderSize.value;
  imgData = landCtx!.createImageData(currentRenderSize.value, currentRenderSize.value);

  try {
    const response = await axios.post("http://localhost:8080/generate", {
      size: size.value,
      seed: seed.value,
    });
    mapId.value = response.data;
    startPolling(mapId.value);
  } catch (error) {
    console.error("Error generating map:", error);
    loading.value = false;
  }
};

const startPolling = async (id: string) => {
  let lastChunkId = 0;
  const poll = async () => {
    if (mapId.value !== id) return;
    try {
      const chunkResponse = await axios.get(`http://localhost:8080/generate/${id}/chunks`, {
          params: { afterId: lastChunkId || undefined },
      });
      const newChunks = chunkResponse.data || [];
      const mapResponse = await axios.get(`http://localhost:8080/generate/${id}`);
      const status = mapResponse.data.status;

      newChunks.forEach((chunk: any) => {
        if (chunk.id > lastChunkId) lastChunkId = chunk.id;

        for (let i = 0; i < chunk.chunk.length; i++) {
          const lx = i % CHUNK_SIZE;
          const ly = Math.floor(i / CHUNK_SIZE);
          const worldX = chunk.chunkX + lx;
          const worldY = chunk.chunkY + ly;

          if (worldX < currentRenderSize.value && worldY < currentRenderSize.value) {
            const globalIndex = worldY * currentRenderSize.value + worldX;
            const packedData = chunk.chunk[i];
            mapBuffer.value![globalIndex] = packedData;
            updateSingleStaticPixel(globalIndex, packedData, worldX, worldY);
          }
        }
      });

      if (newChunks.length > 0 && imgData) {
        landCtx!.putImageData(imgData, 0, 0);
      }

      if (status && status.toString().toUpperCase() === "COMPLETED") {
        loading.value = false;
        return;
      }
      setTimeout(poll, 500);
    } catch (error) {
      console.error("Polling error:", error);
      loading.value = false;
    }
  };
  poll();
};

// Renders a single pixel to the static off-screen layer
// Water tiles use alpha to blend with animated waves underneath
const updateSingleStaticPixel = (
  globalIndex: number,
  packedData: number,
  worldX: number,
  worldY: number,
) => {
  const tileId = packedData & 0xff;
  const shadowByte = (packedData >> 8) & 0xff;

  if (tileId === 23) citiesList.push({ x: worldX, y: worldY });

  const baseColor = parsedColors[tileId] || { r: 0, g: 0, b: 0 };
  let { r, g, b } = baseColor;
  let alpha = 255; 

  if (tileId === 0) alpha = 160;      // Abyss
  else if (tileId === 1) alpha = 100; // Ocean
  else if (tileId === 2) alpha = 40;  // Shallows/Rivers
  else if (tileId === 3) alpha = 200; // Ice

  // Apply topology shadows to land only
  if (showTopology.value && shadowByte !== 128 && tileId > 3) {
    const intensity = Math.abs(shadowByte - 128) / 128;
    if (shadowByte < 128) {
      const a = 1 - intensity * 0.7;
      r *= a; g *= a; b *= a;
    } else {
      const a = intensity * 0.4;
      r += (255 - r) * a; g += (255 - g) * a; b += (255 - b) * a;
    }
  }

  const pxIndex = globalIndex * 4;
  imgData!.data[pxIndex] = r;
  imgData!.data[pxIndex + 1] = g;
  imgData!.data[pxIndex + 2] = b;
  imgData!.data[pxIndex + 3] = alpha;
};

// Fully redraw the static map layer (e.g., when toggling topology)
const rebuildStaticMap = () => {
  if (!imgData || !mapBuffer.value) return;
  citiesList = [];
  for (let i = 0; i < mapBuffer.value.length; i++) {
    const packedData = mapBuffer.value[i];
    if (packedData === 0 && i !== 0) continue;
    updateSingleStaticPixel(i, packedData, i % currentRenderSize.value, Math.floor(i / currentRenderSize.value));
  }
  landCtx!.putImageData(imgData, 0, 0); 
};

watch(showTopology, () => rebuildStaticMap());
</script>

<template>
  <div class="map-generator">
    <h1>Map Generator</h1>
    <div class="controls">
      <div class="input-group">
        <label>Size:</label>
        <input v-model.number="size" type="number" min="10" max="1000" />
      </div>
      <div class="input-group">
        <label>Seed:</label>
        <input v-model.number="seed" type="number" />
      </div>
      <div class="input-group checkbox">
        <label>Topology:</label>
        <input v-model="showTopology" type="checkbox" />
      </div>
      <div class="input-group" v-if="isDrawMode">
        <label>Color:</label>
        <input v-model="drawColor" type="color" />
      </div>
      <button 
        @click="isDrawMode = !isDrawMode" 
        :style="{ backgroundColor: isDrawMode ? '#ff3366' : '#2196F3' }">
        {{ isDrawMode ? 'Draw Mode: ON' : 'Drag Mode' }}
      </button>
      <button @click="clearDrawing" style="background-color: #f44336;" v-if="drawnPaths.length > 0">
        Clear
      </button>
      <button @click="generateMap" :disabled="loading">
        {{ loading ? "Generating..." : "Generate New Map" }}
      </button>
    </div>

    <div v-if="mapId" class="map-info">
      <p>Map ID: {{ mapId }} (Use Mouse to Drag & Wheel to Zoom)</p>
    </div>

    <div class="canvas-container">
      <canvas
        ref="canvasRef"
        @mousedown="onMouseDown"
        @mousemove="onMouseMove"
        @mouseup="onMouseUp"
        @mouseleave="onMouseUp"
        @wheel="onWheel"
        :style="{
          width: VIEWPORT_W + 'px',
          height: VIEWPORT_H + 'px',
          imageRendering: 'pixelated',
          cursor: isDrawMode ? 'crosshair' : (isDragging ? 'grabbing' : 'grab'),
        }"
      ></canvas>
    </div>
  </div>
</template>

<style scoped>
.map-generator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 20px;
}

.controls {
  display: flex;
  gap: 15px;
  align-items: flex-end;
  background: #f4f4f4;
  padding: 15px;
  border-radius: 8px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.input-group.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
}

.input-group.checkbox input {
  width: 18px;
  height: 18px;
}

input {
  padding: 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

button {
  padding: 10px 20px;
  background-color: #4caf50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: bold;
}

button:disabled {
  background-color: #cccccc;
}

.canvas-container {
  border: 2px solid #333;
  background: #050a14;
  display: flex;
  overflow: hidden;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

canvas {
  max-width: 100%;
}
</style>

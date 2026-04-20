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

const animationFrameId = ref<number | null>(null);
const time = ref(0);

const mapBuffer = ref<Uint32Array | null>(null);
let imgData: ImageData | null = null;
let citiesList: { x: number; y: number }[] = [];

// OPTIMIZATION MAGIC: Virtual canvas (invisible in HTML) holding the STATIC LAND
const landCanvas = document.createElement("canvas");
const landCtx = landCanvas.getContext("2d", { willReadFrequently: true });

const colors: Record<number, string> = {
  0: "#000033",
  1: "#000080",
  2: "#1E90FF",
  3: "#A5F2F3",
  4: "#FFF8DC",
  5: "#F4A460",
  6: "#8B7D6B",
  7: "#556B2F",
  8: "#F0F8FF",
  9: "#BDB76B",
  10: "#2F4F4F",
  11: "#C2B280",
  12: "#7CFC00",
  13: "#228B22",
  14: "#2E8B57",
  15: "#EDC9AF",
  16: "#E6DAA6",
  17: "#8FBC8F",
  18: "#004B49",
  19: "#8B4513",
  20: "#696969",
  21: "#A9A9A9",
  22: "#FFFFFF",
  23: "#FF1493",
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

// === MAIN ANIMATION LOOP (Super lightweight) ===
const startAnimation = () => {
  if (animationFrameId.value) return;

  const animate = () => {
    time.value += 0.015; // Smooth wave speed
    const ctx = canvasRef.value?.getContext("2d");

    if (ctx && mapBuffer.value && mapBuffer.value.length > 0) {
      const w = currentRenderSize.value;
      const scale = w / 1000; // Scaling relative to default 1000 size
      const step = Math.max(2, 32 * scale); // Wave grid density proportional to map
      const margin = 40 * scale;

      // 1. DRAWING WATER AT THE VERY BOTTOM
      ctx.fillStyle = "#143d70";
      ctx.fillRect(0, 0, w, w);

      ctx.lineWidth = Math.max(0.5, 2 * scale); // Scaled brush thickness
      ctx.strokeStyle = "#0d2c54"; // Comic wave contour

      // Generating overlapping wave bands from top to bottom
      for (let y = -margin; y <= w + margin; y += step) {
        ctx.beginPath();
        ctx.moveTo(0, y + margin);

        for (let x = 0; x <= w + step; x += step) {
          // Smooth sine waves combining two different frequencies
          const wave1 =
            Math.sin(x * (0.03 / scale) + time.value + y * (0.02 / scale)) * 4;
          const wave2 = Math.cos(x * (0.015 / scale) - time.value * 0.7) * 3;

          // Combine waves and scale amplitude.
          // IMPORTANT: We SUBTRACT the wave from Y because Canvas Y-axis goes down!
          const combinedWave = (wave1 + wave2) * scale;

          ctx.lineTo(x, y - combinedWave + margin);
        }
        ctx.lineTo(w, y + margin);
        ctx.lineTo(0, y + margin);
        ctx.closePath();

        // Alternate color for every second band to enhance texture (posterization effect)
        const isAlternate = Math.round(y / step) % 2 === 0;
        ctx.fillStyle = isAlternate ? "#1a5094" : "#1d5ba8";
        ctx.fill();
        ctx.stroke(); // Sharp line separating the waves
      }

      // 2. OVERLAYING LAND ON TOP OF WATER (Hardware accelerated)
      // Land has semi-transparent water pixels, allowing waves to show through
      ctx.drawImage(landCanvas, 0, 0);

      // 3. CITIES ON THE VERY TOP
      if (citiesList.length > 0) {
        ctx.fillStyle = colors[23];
        citiesList.forEach((city) => {
          ctx.fillRect(city.x - 2, city.y - 2, 5, 5);
        });
      }
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

onMounted(() => startAnimation());
onUnmounted(() => stopAnimation());

const generateMap = async () => {
  loading.value = true;
  mapId.value = "";
  currentRenderSize.value = size.value;

  // Reset buffers and adjust dimensions of both canvases
  mapBuffer.value = new Uint32Array(
    currentRenderSize.value * currentRenderSize.value,
  );
  citiesList = [];

  landCanvas.width = currentRenderSize.value;
  landCanvas.height = currentRenderSize.value;
  imgData = landCtx!.createImageData(
    currentRenderSize.value,
    currentRenderSize.value,
  );

  const ctx = canvasRef.value?.getContext("2d");
  if (ctx && canvasRef.value) {
    canvasRef.value.width = currentRenderSize.value;
    canvasRef.value.height = currentRenderSize.value;
    ctx.clearRect(0, 0, currentRenderSize.value, currentRenderSize.value);
  }

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
      const chunkResponse = await axios.get(
        `http://localhost:8080/generate/${id}/chunks`,
        {
          params: { afterId: lastChunkId || undefined },
        },
      );
      const newChunks = chunkResponse.data || [];
      const mapResponse = await axios.get(
        `http://localhost:8080/generate/${id}`,
      );
      const status = mapResponse.data.status;

      newChunks.forEach((chunk: any) => {
        if (chunk.id > lastChunkId) lastChunkId = chunk.id;

        for (let i = 0; i < chunk.chunk.length; i++) {
          const lx = i % CHUNK_SIZE;
          const ly = Math.floor(i / CHUNK_SIZE);
          const worldX = chunk.chunkX + lx;
          const worldY = chunk.chunkY + ly;

          if (
            worldX < currentRenderSize.value &&
            worldY < currentRenderSize.value
          ) {
            const globalIndex = worldY * currentRenderSize.value + worldX;
            const packedData = chunk.chunk[i];

            mapBuffer.value![globalIndex] = packedData;
            updateSingleStaticPixel(globalIndex, packedData, worldX, worldY);
          }
        }
      });

      // Push changes to the virtual landCanvas once new chunks are processed
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

// Renders the "static world" (executed only when a chunk is loaded)
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
  let r = baseColor.r;
  let g = baseColor.g;
  let b = baseColor.b;
  let alpha = 255; // Land is fully opaque by default

  // MAGIC TRICK: Water transparency!
  // Different water types have different opacities to tint the waves underneath
  if (tileId === 0)
    alpha = 160; // Abyss - very dark, hard to see the bottom
  else if (tileId === 1)
    alpha = 100; // Ocean - waves are perfectly visible
  else if (tileId === 2)
    alpha = 40; // Shallows (River) - almost transparent water
  else if (tileId === 3) alpha = 200; // Frozen Ocean - almost solid ice

  // Apply topology shadows ONLY to land (tileId > 3)
  if (showTopology.value && shadowByte !== 128 && tileId > 3) {
    const intensity = Math.abs(shadowByte - 128) / 128;
    if (shadowByte < 128) {
      const a = 1 - intensity * 0.7;
      r *= a;
      g *= a;
      b *= a;
    } else {
      const a = intensity * 0.4;
      r += (255 - r) * a;
      g += (255 - g) * a;
      b += (255 - b) * a;
    }
  }

  const pxIndex = globalIndex * 4;
  const pixels = imgData!.data;
  pixels[pxIndex] = r;
  pixels[pxIndex + 1] = g;
  pixels[pxIndex + 2] = b;
  pixels[pxIndex + 3] = alpha;
};

const rebuildStaticMap = () => {
  if (!imgData || !mapBuffer.value) return;
  citiesList = [];
  for (
    let globalIndex = 0;
    globalIndex < mapBuffer.value.length;
    globalIndex++
  ) {
    const packedData = mapBuffer.value[globalIndex];
    if (packedData === 0 && globalIndex !== 0) continue;
    const worldX = globalIndex % currentRenderSize.value;
    const worldY = Math.floor(globalIndex / currentRenderSize.value);
    updateSingleStaticPixel(globalIndex, packedData, worldX, worldY);
  }
  landCtx!.putImageData(imgData, 0, 0); // Update the Land layer
};

watch(showTopology, () => {
  rebuildStaticMap();
});
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
      <button @click="generateMap" :disabled="loading">
        {{ loading ? "Generating..." : "Generate New Map" }}
      </button>
    </div>

    <div v-if="mapId" class="map-info">
      <p>Map ID: {{ mapId }}</p>
    </div>

    <div class="canvas-container">
      <canvas
        ref="canvasRef"
        :style="{
          width: '500px',
          height: '500px',
          imageRendering: 'pixelated',
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
  background: #eee;
  display: flex;
}

canvas {
  max-width: 100%;
}
</style>

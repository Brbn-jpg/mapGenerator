<script setup lang="ts">
import { ref } from "vue";
import axios from "axios";

const size = ref(100);
const seed = ref(Math.floor(Math.random() * 100000));
const mapId = ref("");
const loading = ref(false);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const CHUNK_SIZE = 32;

const colors: Record<number, string> = {
  // --- WATER (Height < 0.4) ---
  0: "#000033", // Abyss (Very deep)
  1: "#000080", // Ocean (Standard)
  2: "#1E90FF", // Shallow water / Shelf
  3: "#A5F2F3", // Frozen Ocean / Ice floes (Very low temp)

  // --- COAST (Height 0.4 - 0.45) ---
  4: "#FFF8DC", // Bright sandy beach (Dry, hot)
  5: "#F4A460", // Sandy beach (Standard)
  6: "#8B7D6B", // Rocky/Gravel beach (Cold climate)
  7: "#556B2F", // Mangroves (Hot and very wet climate)

  // --- LAND: COLD CLIMATE (Height 0.45 - 0.8, Temp < 0.35) ---
  8: "#F0F8FF", // Snow desert / Ice sheet
  9: "#BDB76B", // Tundra / Permafrost
  10: "#2F4F4F", // Taiga (Dark coniferous forest)

  // --- LAND: TEMPERATE CLIMATE (Height 0.45 - 0.8, Temp 0.35 - 0.65) ---
  11: "#C2B280", // Steppe / Dry grasses
  12: "#7CFC00", // Green Plains / Grass
  13: "#228B22", // Mixed / Deciduous Forest
  14: "#2E8B57", // Swamps (Very high humidity)

  // --- LAND: HOT CLIMATE (Height 0.45 - 0.8, Temp > 0.65) ---
  15: "#EDC9AF", // Sandy Desert (Sahara)
  16: "#E6DAA6", // Savanna
  17: "#8FBC8F", // Dry shrubs (Scrubland / Chaparral)
  18: "#004B49", // Tropical Jungle / Rainforest

  // --- MOUNTAINS AND PEAKS (Height > 0.8) ---
  19: "#8B4513", // Sun-scorched rocks / Canyon (Hot and dry)
  20: "#696969", // Bare, gray rocks (Standard)
  21: "#A9A9A9", // Alpine Tundra (Cold, harsh peaks)
  22: "#FFFFFF", // Snowy peak / Glacier

  // --- CITY ---
  23: "#FF1493",
};

const generateMap = async () => {
  loading.value = true;
  mapId.value = "";

  // Clear canvas before starting
  const ctx = canvasRef.value?.getContext("2d");
  if (ctx && canvasRef.value) {
    ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height);
    canvasRef.value.width = size.value;
    canvasRef.value.height = size.value;
  }

  try {
    const response = await axios.post("http://localhost:8080/generate", {
      size: size.value,
      seed: seed.value,
    });
    mapId.value = response.data;

    // Start polling for chunks
    startPolling(mapId.value);
  } catch (error) {
    console.error("Error generating map:", error);
    alert("Failed to generate map. Is the backend running?");
    loading.value = false;
  }
};

const startPolling = async (id: string) => {
  let lastChunkId = 0;

  const poll = async () => {
    // If user started a new generation, stop this old poll
    if (mapId.value !== id) {
      console.log("Stopping old poll for ID:", id);
      return;
    }

    try {
      // 1. Fetch NEW chunks
      const chunkResponse = await axios.get(
        `http://localhost:8080/generate/${id}/chunks`,
        {
          params: { afterId: lastChunkId || undefined },
        },
      );
      const newChunks = chunkResponse.data || [];

      // 2. Fetch current status
      const mapResponse = await axios.get(
        `http://localhost:8080/generate/${id}`,
      );
      const status = mapResponse.data.status;

      console.log(
        `Poll ID: ${id}. Status: ${status}. New chunks: ${newChunks.length}`,
      );

      // 3. Draw new chunks
      newChunks.forEach((chunk: any) => {
        drawChunk(chunk);
        if (chunk.id > lastChunkId) {
          lastChunkId = chunk.id;
        }
      });

      // 4. Terminate if COMPLETED
      if (status && status.toString().toUpperCase() === "COMPLETED") {
        loading.value = false;
        console.log("Map generation finished for ID:", id);
        return;
      }

      // 5. Schedule next poll
      setTimeout(poll, 500);
    } catch (error) {
      console.error("Polling error:", error);
      loading.value = false;
    }
  };

  poll();
};

const drawChunk = (chunk: any) => {
  if (!canvasRef.value) return;
  const ctx = canvasRef.value.getContext("2d");
  if (!ctx) return;

  const data = chunk.chunk;
  const imgData = ctx.createImageData(CHUNK_SIZE, CHUNK_SIZE);
  const cities: { x: number; y: number }[] = [];

  for (let i = 0; i < data.length; i++) {
    const tileId = data[i];

    if (tileId === 23) {
      const lx = i % CHUNK_SIZE;
      const ly = Math.floor(i / CHUNK_SIZE);
      cities.push({ x: chunk.chunkX + lx, y: chunk.chunkY + ly });
    }

    const colorHex = colors[tileId] || "#000000";
    const r = parseInt(colorHex.slice(1, 3), 16);
    const g = parseInt(colorHex.slice(3, 5), 16);
    const b = parseInt(colorHex.slice(5, 7), 16);

    imgData.data[i * 4] = r;
    imgData.data[i * 4 + 1] = g;
    imgData.data[i * 4 + 2] = b;
    imgData.data[i * 4 + 3] = 255;
  }

  ctx.putImageData(imgData, chunk.chunkX, chunk.chunkY);

  if (cities.length > 0) {
    ctx.fillStyle = colors[23] || "#ffaaff";
    cities.forEach((city) => {
      ctx.fillRect(city.x - 2, city.y - 2, 15, 15);
    });
  }
};
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

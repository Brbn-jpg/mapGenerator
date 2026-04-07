<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import axios from 'axios'

const size = ref(100)
const seed = ref(Math.floor(Math.random() * 100000))
const mapData = ref<number[]>([])
const mapId = ref('')
const loading = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)

const colors: Record<number, string> = {
  0: '#0000FF', // Ocean
  1: '#F4A460', // Beach (Sand)
  2: '#228B22', // Grass
  3: '#808080', // Mountains
  4: '#FFFFFF'  // Snowy Peaks
}

const generateMap = async () => {
  loading.value = true
  try {
    const response = await axios.post('http://localhost:8080/generate', {
      size: size.value,
      seed: seed.value
    })
    mapId.value = response.data
    await fetchMap(mapId.value)
  } catch (error) {
    console.error('Error generating map:', error)
    alert('Failed to generate map. Is the backend running?')
  } finally {
    loading.value = false
  }
}

const fetchMap = async (id: string) => {
  try {
    const response = await axios.get(`http://localhost:8080/generate/${id}`)
    mapData.value = response.data.map
    nextTick(() => {
      drawMap()
    })
  } catch (error) {
    console.error('Error fetching map:', error)
  }
}

const drawMap = () => {
  if (!canvasRef.value || !mapData.value.length) return
  
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const s = size.value
  canvas.width = s
  canvas.height = s

  const imgData = ctx.createImageData(s, s)
  for (let i = 0; i < mapData.value.length; i++) {
    const tileId = mapData.value[i]
    const colorHex = colors[tileId] || '#000000'
    
    // Parse hex to RGB
    const r = parseInt(colorHex.slice(1, 3), 16)
    const g = parseInt(colorHex.slice(3, 5), 16)
    const b = parseInt(colorHex.slice(5, 7), 16)
    
    imgData.data[i * 4] = r
    imgData.data[i * 4 + 1] = g
    imgData.data[i * 4 + 2] = b
    imgData.data[i * 4 + 3] = 255
  }
  ctx.putImageData(imgData, 0, 0)
}
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
        {{ loading ? 'Generating...' : 'Generate New Map' }}
      </button>
    </div>

    <div v-if="mapId" class="map-info">
      <p>Map ID: {{ mapId }}</p>
    </div>

    <div class="canvas-container">
      <canvas ref="canvasRef" :style="{ width: '500px', height: '500px', imageRendering: 'pixelated' }"></canvas>
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

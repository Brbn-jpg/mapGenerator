<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from "vue";
import axios from "axios";

const seed = ref(Math.floor(Math.random() * 100000));
const size = ref(1000);
const currentRenderSize = ref(1000);

const temp = ref(0.2);
const moisture = ref(0.0);
const continent = ref(1.2);
const city = ref(0.1);

const mapId = ref("");
const loading = ref(false);
const showTopology = ref(true);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const CHUNK_SIZE = 32;

// Viewport dimensions
const VIEWPORT_W = 1000;
const VIEWPORT_H = 800;

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
const drawnPaths = ref<{ points: { x: number; y: number }[]; color: string }[]>(
  [],
);
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
let landDirty = false;

// Off-screen canvas for pre-rendering static terrain
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
  24: "#C85A17",
  25: "#E3C565",
};

const biomeNames: Record<number, string> = {
  0: "Abyss", 1: "Ocean", 2: "Shallow Water", 3: "Frozen Ocean",
  4: "Light Sand", 5: "Beach", 6: "Rocky Beach", 7: "Mangroves",
  8: "Snow Desert", 9: "Tundra", 10: "Taiga", 11: "Steppe",
  12: "Plains", 13: "Mixed Forest", 14: "Swamps", 15: "Desert",
  16: "Savanna", 17: "Dry Shrubs", 18: "Jungle", 19: "Canyons",
  20: "Bare Rocks", 21: "Alpine Tundra", 22: "Eternal Snow",
  23: "City", 24: "Badlands", 25: "Dunes",
};

const hoveredBiome = ref<string | null>(null);
const hoveredCoords = ref<{ x: number; y: number } | null>(null);

const receivedChunks = ref(0);
const totalChunks = ref(0);
const progress = computed(() =>
  totalChunks.value > 0 ? Math.round((receivedChunks.value / totalChunks.value) * 100) : 0,
);

const showLegend = ref(false);
const highlightedBiome = ref<number | null>(null);

interface MapRecord { id: number; seed: number; size: number; status: string; }
const previousMaps = ref<MapRecord[]>([]);

const fetchHistory = async () => {
  try {
    const res = await axios.get("http://localhost:8080/generate/last");
    previousMaps.value = res.data;
  } catch (e) {
    console.error("Failed to fetch history", e);
  }
};

const loadMap = async (map: MapRecord) => {
  loading.value = true;
  mapId.value = String(map.id);
  currentRenderSize.value = map.size;
  receivedChunks.value = 0;
  totalChunks.value = Math.ceil(map.size / CHUNK_SIZE) ** 2;
  mapBuffer.value = new Uint32Array(map.size * map.size);
  citiesList = [];
  landCanvas.width = map.size;
  landCanvas.height = map.size;
  imgData = landCtx!.createImageData(map.size, map.size);

  try {
    const res = await axios.get(`http://localhost:8080/generate/${map.id}/chunks`);
    (res.data || []).forEach((chunk: any) => {
      for (let i = 0; i < chunk.chunk.length; i++) {
        const worldX = chunk.chunkX + (i % CHUNK_SIZE);
        const worldY = chunk.chunkY + Math.floor(i / CHUNK_SIZE);
        if (worldX < currentRenderSize.value && worldY < currentRenderSize.value) {
          const globalIndex = worldY * currentRenderSize.value + worldX;
          mapBuffer.value![globalIndex] = chunk.chunk[i];
          updateSingleStaticPixel(globalIndex, chunk.chunk[i], worldX, worldY);
        }
      }
      receivedChunks.value++;
    });
    landDirty = true;
  } catch (e) {
    console.error("Failed to load map", e);
  } finally {
    loading.value = false;
  }
};

const toggleHighlight = (id: number) => {
  highlightedBiome.value = highlightedBiome.value === id ? null : id;
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
  const { x, y } = getWorldCoords(e.clientX, e.clientY);
  const wx = Math.floor(x);
  const wy = Math.floor(y);
  if (mapBuffer.value && wx >= 0 && wy >= 0 && wx < currentRenderSize.value && wy < currentRenderSize.value) {
    const tileId = mapBuffer.value[wy * currentRenderSize.value + wx] & 0xff;
    hoveredBiome.value = biomeNames[tileId] ?? null;
    hoveredCoords.value = { x: wx, y: wy };
  } else {
    hoveredBiome.value = null;
    hoveredCoords.value = null;
  }

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

const onMouseLeaveCanvas = () => {
  hoveredBiome.value = null;
  hoveredCoords.value = null;
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
          const smoothBase = Math.sin(
            x * (0.015 / scale) + time.value + y * (0.015 / scale),
          );
          const sharpPeaks =
            1 -
            Math.abs(
              Math.sin(
                x * (0.08 / scale) - time.value * 1.3 + y * (0.2 / scale),
              ),
            );

          const wave = (smoothBase * 3 + sharpPeaks * 8) * scale;
          const jitterX =
            Math.sin(x * (1.5 / scale) + y * (2.1 / scale)) * (1.2 * scale);
          const jitterY =
            Math.cos(x * (2.3 / scale) - y * (1.7 / scale)) * (1.2 * scale);
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

      // 3. Flush dirty terrain pixels and overlay static terrain island
      if (landDirty && imgData) {
        landCtx!.putImageData(imgData, 0, 0);
        landDirty = false;
      }
      ctx.drawImage(landCanvas, 0, 0);

      // 4. Draw cities on top
      if (citiesList.length > 0) {
        citiesList.forEach((city) => {
          const x = city.x;
          const y = city.y;
          // Use hash to decide if we draw a building and which type
          // This prevents every single pixel of a 3x3 city block from having a building
          const hash =
            (Math.abs(Math.sin(x * 12.9898 + y * 78.233)) * 43758.5453) % 1;

          if (hash < 0.3) return; // Empty space/pavement

          ctx.lineWidth = Math.max(0.5, 1 / zoom.value);
          ctx.strokeStyle = "#000000";

          if (hash > 0.75) {
            // Skyscraper
            const h = 8 + Math.floor(hash * 8);
            const w = 5;
            // Concrete Wall
            ctx.fillStyle = "#a9a9a9";
            ctx.fillRect(x - w / 2, y - h + 2, w, h);
            ctx.strokeRect(x - w / 2, y - h + 2, w, h);
            // Windows
            ctx.fillStyle = "#ffff00";
            for (let row = 0; row < Math.floor(h / 4); row++) {
              ctx.fillRect(x - 1.5, y - h + 4 + row * 4, 1, 1);
              ctx.fillRect(x + 0.5, y - h + 4 + row * 4, 1, 1);
            }
          } else {
            // House
            const w = 5;
            const h = 4;
            // Wall
            ctx.fillStyle = "#ffffff";
            ctx.fillRect(x - w / 2, y - h / 2 + 1, w, h);
            ctx.strokeRect(x - w / 2, y - h / 2 + 1, w, h);
            // Roof (Brick)
            ctx.fillStyle = "#8b0000";
            ctx.beginPath();
            ctx.moveTo(x - w / 2 - 1, y - h / 2 + 1);
            ctx.lineTo(x, y - h / 2 - 2);
            ctx.lineTo(x + w / 2 + 1, y - h / 2 + 1);
            ctx.closePath();
            ctx.fill();
            ctx.stroke();
            // Door
            ctx.fillStyle = "#5d4037";
            ctx.fillRect(x, y, 1.5, 2.5);
            // Small Window
            ctx.fillStyle = "#add8e6";
            ctx.fillRect(x - 1.5, y - 0.5, 1, 1);
          }
        });
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
  fetchHistory();
});
onUnmounted(() => stopAnimation());

const exportPng = () => {
  const tmp = document.createElement("canvas");
  tmp.width = currentRenderSize.value;
  tmp.height = currentRenderSize.value;
  const tCtx = tmp.getContext("2d")!;
  tCtx.fillStyle = "#143d70";
  tCtx.fillRect(0, 0, tmp.width, tmp.height);
  tCtx.drawImage(landCanvas, 0, 0);

  tCtx.lineCap = "round";
  tCtx.lineJoin = "round";
  tCtx.lineWidth = 4;
  drawnPaths.value.forEach((item) => {
    if (item.points.length < 2) return;
    tCtx.strokeStyle = item.color;
    tCtx.beginPath();
    tCtx.moveTo(item.points[0].x, item.points[0].y);
    for (let i = 1; i < item.points.length; i++) tCtx.lineTo(item.points[i].x, item.points[i].y);
    tCtx.stroke();
  });

  tmp.toBlob((blob) => {
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `map-${mapId.value}.png`;
    a.click();
    URL.revokeObjectURL(url);
  });
};

const generateMap = async () => {
  loading.value = true;
  mapId.value = "";
  receivedChunks.value = 0;
  totalChunks.value = Math.ceil(size.value / CHUNK_SIZE) ** 2;
  currentRenderSize.value = size.value;

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

  try {
    const response = await axios.post("http://localhost:8080/generate", {
      size: size.value,
      seed: seed.value,
      temp: temp.value,
      moisutre: moisture.value,
      continent: continent.value,
      city: city.value,
    });
    mapId.value = response.data;
    startStream(mapId.value);
  } catch (error) {
    console.error("Error generating map:", error);
    loading.value = false;
  }
};

const startStream = (id: string) => {
  const source = new EventSource(`http://localhost:8080/generate/${id}/stream`);

  source.addEventListener("chunk", (e: MessageEvent) => {
    if (mapId.value !== id) { source.close(); return; }
    const chunk = JSON.parse(e.data);

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

    receivedChunks.value++;
    landDirty = true;
  });

  source.addEventListener("done", () => {
    loading.value = false;
    source.close();
    fetchHistory();
  });

  source.onerror = () => {
    console.error("SSE error");
    loading.value = false;
    source.close();
  };
};

const getProceduralTexture = (tileId: number, x: number, y: number) => {
  // Simple deterministic pseudo-random hash
  const hash = (Math.sin(x * 12.9898 + y * 78.233) * 43758.5453) % 1;
  const rand = Math.abs(hash);

  switch (tileId) {
    case 4: // Light sand
    case 5: // Beach
    case 8: // Snow desert
    case 11: // Steppe
    case 15: // Desert
      return 0.95 + rand * 0.1; // Fine grain

    case 7: // Mangroves
    case 10: // Taiga
    case 13: // Mixed Forest
    case 14: // Swamps
    case 16: // Savanna
    case 17: // Dry Shrubs
    case 18: // Jungle
      return rand < 0.25 ? 0.82 : 1.0; // Scattered tufts/darker patches

    case 6: // Rocky beach
    case 19: // Canyons
    case 20: // Bare Rocks
    case 21: // Alpine Tundra
    case 22: // Eternal Snow
    case 24: // Badlands
      const layer = Math.sin(y * 1.5 + x * 0.2);
      return layer > 0.4 ? 0.92 : 1.08; // Stratified ridges

    case 25: // Dunes
      const ripple = Math.sin(x * 0.4 + Math.sin(y * 0.3) * 2);
      return ripple > 0 ? 1.1 : 0.9; // Wavy sand ripples

    case 9: // Tundra
    case 12: // Plains
      const patch = Math.sin(x * 0.15) * Math.cos(y * 0.15);
      return 1.0 + patch * 0.06; // Soft organic patches

    default:
      return 1.0;
  }
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

  const baseColor = parsedColors[tileId] || { r: 0, g: 0, b: 0 };
  let { r, g, b } = baseColor;
  let alpha = 255;

  if (tileId === 23) {
    citiesList.push({ x: worldX, y: worldY });
    alpha = 0; // Hide marker color, city is drawn in animation loop
  } else if (tileId === 0) {
    alpha = 160; // Abyss
  } else if (tileId === 1) {
    alpha = 100; // Ocean
  } else if (tileId === 2) {
    alpha = 40; // Shallows/Rivers
  } else if (tileId === 3) {
    alpha = 200; // Ice
  }

  // Apply procedural texture to land
  if (tileId > 3 && tileId !== 23) {
    const tex = getProceduralTexture(tileId, worldX, worldY);
    r = Math.max(0, Math.min(255, r * tex));
    g = Math.max(0, Math.min(255, g * tex));
    b = Math.max(0, Math.min(255, b * tex));
  }

  // Apply topology shadows to land only
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

  if (highlightedBiome.value !== null && tileId !== highlightedBiome.value) {
    r = Math.round(r * 0.18);
    g = Math.round(g * 0.18);
    b = Math.round(b * 0.18);
    if (alpha < 255) alpha = Math.round(alpha * 0.18);
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
    updateSingleStaticPixel(
      i,
      packedData,
      i % currentRenderSize.value,
      Math.floor(i / currentRenderSize.value),
    );
  }
  landDirty = true;
};

watch(showTopology, () => rebuildStaticMap());
watch(highlightedBiome, () => rebuildStaticMap());
</script>

<template>
  <div class="map-generator">
    <div class="sidebar">
      <div class="sidebar-header">
        <h1>MapGen</h1>
        <p class="subtitle">Procedural Generation</p>
      </div>

      <div class="controls">
        <div class="control-group">
          <label>Map Size</label>
          <input
            v-model.number="size"
            type="number"
            min="10"
            max="1000"
            class="dark-input"
          />
        </div>

        <div class="control-group">
          <label>World Seed</label>
          <div class="seed-row">
            <input v-model.number="seed" type="number" class="dark-input seed-input" />
            <button class="btn btn-icon" @click="seed = Math.floor(Math.random() * 100000)" title="Randomize seed">&#9880;</button>
          </div>
        </div>

        <div class="divider"></div>

        <div class="control-group slider-group">
          <label>Temperature <span class="slider-value">{{ temp.toFixed(2) }}</span></label>
          <input v-model.number="temp" type="range" min="-0.5" max="0.5" step="0.01" class="dark-slider" />
        </div>

        <div class="control-group slider-group">
          <label>Moisture <span class="slider-value">{{ moisture.toFixed(2) }}</span></label>
          <input v-model.number="moisture" type="range" min="-0.5" max="0.5" step="0.01" class="dark-slider" />
        </div>

        <div class="control-group slider-group">
          <label>Continent <span class="slider-value">{{ continent.toFixed(2) }}</span></label>
          <input v-model.number="continent" type="range" min="0.5" max="2" step="0.01" class="dark-slider" />
        </div>

        <div class="control-group slider-group">
          <label>City Density <span class="slider-value">{{ city.toFixed(2) }}</span></label>
          <input v-model.number="city" type="range" min="0.0" max="0.5" step="0.01" class="dark-slider" />
        </div>

        <div class="divider"></div>

        <div class="control-group switch-group">
          <label class="switch-label">
            <span class="toggle-text">Show Topology</span>
            <div class="toggle-switch">
              <input v-model="showTopology" type="checkbox" />
              <span class="slider"></span>
            </div>
          </label>
        </div>

        <div class="divider"></div>

        <div class="control-group" v-if="isDrawMode">
          <label>Brush Color</label>
          <div class="color-picker-wrapper">
            <input v-model="drawColor" type="color" />
            <span class="color-value">{{ drawColor }}</span>
          </div>
        </div>

        <div class="action-buttons">
          <button
            class="btn btn-mode"
            :class="{ 'is-active': isDrawMode }"
            @click="isDrawMode = !isDrawMode"
          >
            {{ isDrawMode ? "Drawing Mode" : "Drag Mode" }}
          </button>

          <button
            class="btn btn-danger"
            @click="clearDrawing"
            v-if="drawnPaths.length > 0"
          >
            Clear
          </button>
        </div>

        <button
          class="btn btn-primary generate-btn"
          @click="generateMap"
          :disabled="loading"
        >
          <span v-if="loading" class="loader"></span>
          <span v-else>Generate New World</span>
        </button>

        <div v-if="loading" class="progress-wrap">
          <div class="progress-bar" :style="{ width: progress + '%' }"></div>
          <span class="progress-label">{{ progress }}%</span>
        </div>

        <div v-if="mapId && !loading" class="map-info-box">
          <div class="info-label">Active Map ID</div>
          <div class="info-value">{{ mapId }}</div>
          <div class="info-hint">Use mouse to drag & scroll to zoom</div>
          <button class="btn btn-export" @click="exportPng">Export PNG</button>
        </div>

        <div class="history-section" v-if="previousMaps.length > 0">
          <div class="section-label">Recent Maps</div>
          <div class="history-list">
            <div
              v-for="m in previousMaps"
              :key="m.id"
              class="history-item"
              :class="{ 'is-active': mapId === String(m.id) }"
              @click="loadMap(m)"
            >
              <div class="history-item-row">
                <span class="history-id">#{{ m.id }}</span>
                <span class="history-status" :class="m.status.toLowerCase()">{{ m.status }}</span>
              </div>
              <div class="history-item-row history-meta">
                <span>{{ m.size }}×{{ m.size }}</span>
                <span>seed {{ m.seed }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="legend-section">
          <button class="btn-legend-toggle" @click="showLegend = !showLegend">
            Biome Legend {{ showLegend ? '▲' : '▼' }}
          </button>
          <div v-if="showLegend" class="legend-list">
            <div
              v-for="(name, id) in biomeNames"
              :key="id"
              class="legend-item"
              :class="{ 'is-highlighted': highlightedBiome === Number(id), 'is-dimmed': highlightedBiome !== null && highlightedBiome !== Number(id) }"
              @click="toggleHighlight(Number(id))"
            >
              <span class="legend-swatch" :style="{ background: colors[Number(id)] ?? '#888' }"></span>
              <span class="legend-name">{{ name }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="canvas-wrapper">
      <div
        class="canvas-container"
        :class="{
          'is-drawing': isDrawMode,
          'is-dragging': isDragging && !isDrawMode,
        }"
      >
        <canvas
          ref="canvasRef"
          @mousedown="onMouseDown"
          @mousemove="onMouseMove"
          @mouseup="onMouseUp"
          @mouseleave="onMouseLeaveCanvas"
          @wheel="onWheel"
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
  </div>
</template>

<style scoped>
/* Dark Theme UI */
.map-generator {
  display: flex;
  width: 100vw;
  height: 100vh;
  background-color: var(--bg-color, #0b0f19);
  color: var(--text-primary, #e2e8f0);
  overflow: hidden;
  font-family: "Inter", system-ui, sans-serif;
}

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

.sidebar-header {
  margin-bottom: 32px;
}

.sidebar-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #60a5fa, #a78bfa);
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

.controls {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

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

/* Custom Switch */
.switch-group {
  margin-top: 4px;
}

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

.toggle-switch {
  position: relative;
  width: 44px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
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

input:checked + .slider:before {
  transform: translateX(20px);
}

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
  -webkit-appearance: none;
  border: none;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  padding: 0;
  background: none;
}
.color-picker-wrapper input[type="color"]::-webkit-color-swatch-wrapper {
  padding: 0;
}
.color-picker-wrapper input[type="color"]::-webkit-color-swatch {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
}

.color-value {
  font-family: monospace;
  font-size: 13px;
  color: var(--text-secondary, #94a3b8);
}

.action-buttons {
  display: flex;
  gap: 10px;
}

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

.btn-mode:hover {
  background: rgba(255, 255, 255, 0.1);
}

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

.btn-danger:hover {
  background: rgba(239, 68, 68, 0.25);
}

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

/* Loader */
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
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
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
.info-hint::before {
  content: "ℹ️";
  font-size: 14px;
}

/* Canvas Area */
.canvas-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at center, #1e293b 0%, #0f172a 100%);
  position: relative;
}

/* Dot pattern background */
.canvas-wrapper::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: radial-gradient(
    rgba(255, 255, 255, 0.1) 1px,
    transparent 1px
  );
  background-size: 24px 24px;
  pointer-events: none;
}

.canvas-container {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: #050a14;
  border-radius: 12px;
  overflow: hidden;
  box-shadow:
    0 24px 48px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.05);
  position: relative;
  z-index: 1;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
}

.canvas-container:hover {
  box-shadow:
    0 32px 64px rgba(0, 0, 0, 0.6),
    0 0 0 1px rgba(255, 255, 255, 0.1);
}

.is-drawing canvas {
  cursor: crosshair !important;
}

.is-dragging canvas {
  cursor: grabbing !important;
}

canvas {
  max-width: 100%;
  display: block;
}

.seed-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.seed-input {
  flex: 1;
}

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

.btn-icon:hover {
  background: rgba(255, 255, 255, 0.12);
}

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

.btn-export:hover {
  background: rgba(99, 102, 241, 0.25);
}

.history-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-item {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-item:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.12);
}

.history-item.is-active {
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(59, 130, 246, 0.08);
}

.history-item-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.history-id {
  font-family: monospace;
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
}

.history-status {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 2px 6px;
  border-radius: 4px;
}

.history-status.completed {
  background: rgba(34, 197, 94, 0.15);
  color: #86efac;
}

.history-status.in_progress {
  background: rgba(234, 179, 8, 0.15);
  color: #fde047;
}

.history-meta {
  font-size: 11px;
  color: #64748b;
}

.legend-section {
  margin-top: 4px;
}

.btn-legend-toggle {
  width: 100%;
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  text-align: left;
  padding: 4px 0;
  letter-spacing: 0.5px;
}

.btn-legend-toggle:hover {
  color: #e2e8f0;
}

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
  border: 1px solid rgba(255,255,255,0.1);
}

.legend-name {
  font-size: 12px;
  color: #cbd5e1;
}

.hover-info {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: rgba(10, 15, 28, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  pointer-events: none;
  backdrop-filter: blur(4px);
}

.hover-biome {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
}

.hover-coords {
  font-family: monospace;
  font-size: 11px;
  color: #64748b;
}

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

.dark-slider {
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

.dark-slider::-webkit-slider-thumb:hover {
  background: #60a5fa;
}
</style>

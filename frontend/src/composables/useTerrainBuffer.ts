import { ref } from "vue";
import {
  PARSED_BIOME_COLORS,
  TILE_CITY,
  type BiomeId,
} from "../constants/biomes";

const proceduralTexture = (tileId: number, x: number, y: number) => {
  const hash = (Math.sin(x * 12.9898 + y * 78.233) * 43758.5453) % 1;
  const rand = Math.abs(hash);
  switch (tileId) {
    case 4:
    case 5:
    case 8:
    case 11:
    case 15:
      return 0.95 + rand * 0.1;
    case 7:
    case 10:
    case 13:
    case 14:
    case 16:
    case 17:
    case 18:
      return rand < 0.25 ? 0.82 : 1.0;
    case 6:
    case 19:
    case 20:
    case 21:
    case 22:
    case 24: {
      const layer = Math.sin(y * 1.5 + x * 0.2);
      return layer > 0.4 ? 0.92 : 1.08;
    }
    case 25: {
      const ripple = Math.sin(x * 0.4 + Math.sin(y * 0.3) * 2);
      return ripple > 0 ? 1.1 : 0.9;
    }
    case 9:
    case 12: {
      const patch = Math.sin(x * 0.15) * Math.cos(y * 0.15);
      return 1.0 + patch * 0.06;
    }
    default:
      return 1.0;
  }
};

export function useTerrainBuffer() {
  const size = ref(0);
  const mapBuffer = ref<Uint32Array | null>(null);
  const showTopology = ref(true);
  const highlightedBiome = ref<BiomeId | null>(null);

  const landCanvas = document.createElement("canvas");
  const landCtx = landCanvas.getContext("2d", { willReadFrequently: true })!;
  const highlightCanvas = document.createElement("canvas");
  const highlightCtx = highlightCanvas.getContext("2d", { willReadFrequently: true })!;
  let imgData: ImageData | null = null;
  let highlightImgData: ImageData | null = null;
  let citiesList: { x: number; y: number }[] = [];
  let landDirty = false;
  let highlightDirty = false;

  const init = (newSize: number) => {
    size.value = newSize;
    mapBuffer.value = new Uint32Array(newSize * newSize);
    citiesList = [];
    landCanvas.width = newSize;
    landCanvas.height = newSize;
    imgData = landCtx.createImageData(newSize, newSize);
    highlightCanvas.width = newSize;
    highlightCanvas.height = newSize;
    highlightImgData = highlightCtx.createImageData(newSize, newSize);
    landDirty = false;
    highlightDirty = false;
  };

  const updatePixel = (
    globalIndex: number,
    packedData: number,
    worldX: number,
    worldY: number,
  ) => {
    if (!imgData) return;
    const tileId = packedData & 0xff;
    const shadowByte = (packedData >> 8) & 0xff;

    const baseColor = PARSED_BIOME_COLORS[tileId] || { r: 0, g: 0, b: 0 };
    let { r, g, b } = baseColor;
    let alpha = 255;

    if (tileId === TILE_CITY) {
      citiesList.push({ x: worldX, y: worldY });
      alpha = 0;
    } else if (tileId === 0) {
      alpha = 160;
    } else if (tileId === 1) {
      alpha = 100;
    } else if (tileId === 2) {
      alpha = 40;
    } else if (tileId === 3) {
      alpha = 200;
    }

    if (tileId > 3 && tileId !== TILE_CITY) {
      const tex = proceduralTexture(tileId, worldX, worldY);
      r = Math.max(0, Math.min(255, r * tex));
      g = Math.max(0, Math.min(255, g * tex));
      b = Math.max(0, Math.min(255, b * tex));
    }

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

    const px = globalIndex * 4;
    imgData.data[px] = r;
    imgData.data[px + 1] = g;
    imgData.data[px + 2] = b;
    imgData.data[px + 3] = alpha;

    if (highlightImgData) {
      const matches =
        highlightedBiome.value !== null && tileId === highlightedBiome.value;
      highlightImgData.data[px] = matches ? r : 0;
      highlightImgData.data[px + 1] = matches ? g : 0;
      highlightImgData.data[px + 2] = matches ? b : 0;
      highlightImgData.data[px + 3] = matches ? alpha : 0;
      if (highlightedBiome.value !== null) highlightDirty = true;
    }
    landDirty = true;
  };

  const ingestChunk = (
    chunkX: number,
    chunkY: number,
    chunk: number[],
    chunkSize: number,
  ) => {
    if (!mapBuffer.value) return;
    for (let i = 0; i < chunk.length; i++) {
      const lx = i % chunkSize;
      const ly = Math.floor(i / chunkSize);
      const wx = chunkX + lx;
      const wy = chunkY + ly;
      if (wx < size.value && wy < size.value) {
        const gi = wy * size.value + wx;
        mapBuffer.value[gi] = chunk[i];
        updatePixel(gi, chunk[i], wx, wy);
      }
    }
  };

  const rebuildStaticMap = () => {
    if (!imgData || !mapBuffer.value) return;
    citiesList = [];
    const buf = mapBuffer.value;
    for (let i = 0; i < buf.length; i++) {
      const packed = buf[i];
      if (packed === 0 && i !== 0) continue;
      updatePixel(i, packed, i % size.value, Math.floor(i / size.value));
    }
  };

  const rebuildHighlight = () => {
    if (!highlightImgData || !mapBuffer.value || !imgData) return;
    const buf = mapBuffer.value;
    const land = imgData.data;
    const hl = highlightImgData.data;
    const target = highlightedBiome.value;
    for (let i = 0; i < buf.length; i++) {
      const matches = target !== null && (buf[i] & 0xff) === target;
      const px = i * 4;
      if (matches) {
        hl[px] = land[px];
        hl[px + 1] = land[px + 1];
        hl[px + 2] = land[px + 2];
        hl[px + 3] = land[px + 3];
      } else {
        hl[px + 3] = 0;
      }
    }
    highlightDirty = true;
  };

  const flushLand = () => {
    if (landDirty && imgData) {
      landCtx.putImageData(imgData, 0, 0);
      landDirty = false;
    }
  };

  const flushHighlight = () => {
    if (highlightDirty && highlightImgData) {
      highlightCtx.putImageData(highlightImgData, 0, 0);
      highlightDirty = false;
    }
  };

  const biomeAt = (x: number, y: number): BiomeId | null => {
    if (!mapBuffer.value) return null;
    if (x < 0 || y < 0 || x >= size.value || y >= size.value) return null;
    return mapBuffer.value[y * size.value + x] & 0xff;
  };

  const getBiomeCounts = () => {
    if (!mapBuffer.value) return [];
    const counts: Record<number, number> = {};
    const buf = mapBuffer.value;
    const total = buf.length;
    for (let i = 0; i < total; i++) {
      const id = buf[i] & 0xff;
      counts[id] = (counts[id] ?? 0) + 1;
    }
    return Object.entries(counts)
      .map(([id, count]) => ({ id: Number(id), count, pct: (count / total) * 100 }))
      .sort((a, b) => b.count - a.count);
  };

  return {
    size,
    showTopology,
    highlightedBiome,
    landCanvas,
    highlightCanvas,
    init,
    updatePixel,
    ingestChunk,
    rebuildStaticMap,
    rebuildHighlight,
    flushLand,
    flushHighlight,
    biomeAt,
    getCities: () => citiesList,
    getBiomeCounts,
  };
}

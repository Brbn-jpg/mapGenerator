import { ref, computed } from "vue";
import {
  fetchHistory as apiFetchHistory,
  fetchChunks as apiFetchChunks,
  generateMap as apiGenerateMap,
  streamUrl,
  type GenerateParams,
  type MapRecord,
} from "../api/maps";

const CHUNK_SIZE = 32;

export interface StreamHandlers {
  onInit: (size: number) => void;
  onChunk: (chunkX: number, chunkY: number, chunk: number[], chunkSize: number) => void;
  onDone?: () => void;
}

export function useMapStream(handlers: StreamHandlers) {
  const mapId = ref("");
  const loading = ref(false);
  const currentRenderSize = ref(1000);
  const receivedChunks = ref(0);
  const totalChunks = ref(0);
  const previousMaps = ref<MapRecord[]>([]);

  const progress = computed(() =>
    totalChunks.value > 0
      ? Math.round((receivedChunks.value / totalChunks.value) * 100)
      : 0,
  );

  let activeStream: EventSource | null = null;

  const closeStream = () => {
    if (activeStream) {
      activeStream.close();
      activeStream = null;
    }
  };

  const refreshHistory = async () => {
    try {
      previousMaps.value = await apiFetchHistory();
    } catch (e) {
      console.error("Failed to fetch history", e);
    }
  };

  const startStream = (id: string) => {
    closeStream();
    const source = new EventSource(streamUrl(id));
    activeStream = source;

    source.addEventListener("chunk", (e: MessageEvent) => {
      if (mapId.value !== id || activeStream !== source) {
        source.close();
        return;
      }
      const chunk = JSON.parse(e.data);
      handlers.onChunk(chunk.chunkX, chunk.chunkY, chunk.chunk, CHUNK_SIZE);
      receivedChunks.value++;
    });

    source.addEventListener("done", () => {
      if (activeStream === source) activeStream = null;
      loading.value = false;
      source.close();
      handlers.onDone?.();
      refreshHistory();
    });

    source.onerror = () => {
      if (activeStream !== source) return;
      console.error("SSE error");
      activeStream = null;
      loading.value = false;
      source.close();
    };
  };

  const generate = async (params: GenerateParams) => {
    closeStream();
    loading.value = true;
    mapId.value = "";
    receivedChunks.value = 0;
    totalChunks.value = Math.ceil(params.size / CHUNK_SIZE) ** 2;
    currentRenderSize.value = params.size;
    handlers.onInit(params.size);
    try {
      mapId.value = await apiGenerateMap(params);
      startStream(mapId.value);
    } catch (e) {
      console.error("Error generating map", e);
      loading.value = false;
    }
  };

  const loadExisting = async (record: MapRecord) => {
    closeStream();
    loading.value = true;
    mapId.value = String(record.id);
    currentRenderSize.value = record.size;
    receivedChunks.value = 0;
    totalChunks.value = Math.ceil(record.size / CHUNK_SIZE) ** 2;
    handlers.onInit(record.size);
    try {
      const chunks = await apiFetchChunks(record.id);
      chunks.forEach((c) => {
        handlers.onChunk(c.chunkX, c.chunkY, c.chunk, CHUNK_SIZE);
        receivedChunks.value++;
      });
    } catch (e) {
      console.error("Failed to load map", e);
    } finally {
      loading.value = false;
    }
  };

  return {
    mapId,
    loading,
    currentRenderSize,
    receivedChunks,
    totalChunks,
    progress,
    previousMaps,
    refreshHistory,
    generate,
    loadExisting,
    closeStream,
    CHUNK_SIZE,
  };
}

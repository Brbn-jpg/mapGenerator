import { api, API_URL } from "./client";

export interface MapRecord {
  id: number;
  seed: number;
  size: number;
  status: string;
}

export interface GenerateParams {
  size: number;
  seed: number;
  temp: number;
  moisture: number;
  continent: number;
  city: number;
}

export interface ChunkPayload {
  chunkX: number;
  chunkY: number;
  chunk: number[];
}

export const fetchHistory = async (): Promise<MapRecord[]> => {
  const res = await api.get<MapRecord[]>("/generate/last");
  return res.data;
};

export const fetchChunks = async (mapId: number | string): Promise<ChunkPayload[]> => {
  const res = await api.get<ChunkPayload[]>(`/generate/${mapId}/chunks`);
  return res.data ?? [];
};

export const generateMap = async (params: GenerateParams): Promise<string> => {
  // Backend currently uses misspelled `moisutre` field — translate at boundary.
  const res = await api.post<string>("/generate", {
    size: params.size,
    seed: params.seed,
    temp: params.temp,
    moisutre: params.moisture,
    continent: params.continent,
    city: params.city,
  });
  return String(res.data);
};

export const streamUrl = (id: string | number) =>
  `${API_URL}/generate/${id}/stream`;

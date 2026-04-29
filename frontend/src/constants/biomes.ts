export type BiomeId = number;

export interface RGB {
  r: number;
  g: number;
  b: number;
}

export const TILE_CITY: BiomeId = 23;

export const BIOME_COLORS: Record<BiomeId, string> = {
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

export const BIOME_NAMES: Record<BiomeId, string> = {
  0: "Abyss", 1: "Ocean", 2: "Shallow Water", 3: "Frozen Ocean",
  4: "Light Sand", 5: "Beach", 6: "Rocky Beach", 7: "Mangroves",
  8: "Snow Desert", 9: "Tundra", 10: "Taiga", 11: "Steppe",
  12: "Plains", 13: "Mixed Forest", 14: "Swamps", 15: "Desert",
  16: "Savanna", 17: "Dry Shrubs", 18: "Jungle", 19: "Canyons",
  20: "Bare Rocks", 21: "Alpine Tundra", 22: "Eternal Snow",
  23: "City", 24: "Badlands", 25: "Dunes",
};

export const PARSED_BIOME_COLORS: Record<BiomeId, RGB> = Object.entries(
  BIOME_COLORS,
).reduce(
  (acc, [id, hex]) => {
    acc[Number(id)] = {
      r: parseInt(hex.slice(1, 3), 16),
      g: parseInt(hex.slice(3, 5), 16),
      b: parseInt(hex.slice(5, 7), 16),
    };
    return acc;
  },
  {} as Record<BiomeId, RGB>,
);

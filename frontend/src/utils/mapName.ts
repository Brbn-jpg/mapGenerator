const ADJECTIVES = [
  "Ancient", "Frozen", "Crimson", "Sunken", "Forgotten", "Hollow", "Stormy",
  "Verdant", "Ashen", "Gilded", "Silent", "Forsaken", "Misty", "Iron",
  "Emerald", "Shadowed", "Blighted", "Cursed", "Sacred", "Shattered",
  "Endless", "Burning", "Pale", "Drifting", "Howling", "Sundered",
];

const NOUNS = [
  "Shore", "Vale", "Peaks", "Expanse", "Reaches", "Wastes", "Highlands",
  "Depths", "Isles", "Plains", "Realm", "Wilds", "Hollow", "Marches",
  "Basin", "Ridge", "Tidelands", "Crags", "Moors", "Straits",
  "Barrens", "Veil", "Dominion", "Frontier", "Passage",
];

const CONNECTORS = [
  "of the", "of", "beyond the", "at the Edge of", "beneath the",
];

const SECOND_NOUNS = [
  "Void", "Storm", "Abyss", "Dawn", "Dusk", "Tide", "Frost", "Flame",
  "Shadow", "Ash", "Iron", "Mist", "Silence", "Ruin", "Deep",
];

function rand(seed: number, n: number): [number, number] {
  // mulberry32
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return [((t ^ (t >>> 14)) >>> 0) % n, seed];
}

export function generateMapName(seed: number): string {
  let s = seed;
  let i: number;

  [i, s] = rand(s, ADJECTIVES.length);
  const adj = ADJECTIVES[i];

  [i, s] = rand(s, NOUNS.length);
  const noun = NOUNS[i];

  let [useConnector] = rand(s, 3);
  [, s] = rand(s, 3);

  if (useConnector === 0) {
    [i, s] = rand(s, CONNECTORS.length);
    const conn = CONNECTORS[i];
    [i, s] = rand(s, SECOND_NOUNS.length);
    const noun2 = SECOND_NOUNS[i];
    return `${adj} ${noun} ${conn} ${noun2}`;
  }

  return `The ${adj} ${noun}`;
}
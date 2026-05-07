import { ref, computed } from "vue";

type Stop = { t: number; r: number; g: number; b: number };

// Multiply tint stops keyed on time-of-day (0=midnight, 0.5=noon).
// White = identity, darker = night, warm = golden hour.
const STOPS: Stop[] = [
  { t: 0.00, r: 30,  g: 45,  b: 95  },  // midnight — deep blue
  { t: 0.20, r: 70,  g: 65,  b: 110 },  // pre-dawn — violet
  { t: 0.25, r: 255, g: 160, b: 110 },  // sunrise — peach
  { t: 0.32, r: 255, g: 220, b: 180 },  // morning — warm light
  { t: 0.50, r: 255, g: 255, b: 255 },  // noon — neutral
  { t: 0.68, r: 255, g: 225, b: 180 },  // afternoon
  { t: 0.75, r: 255, g: 130, b: 80  },  // sunset — deep orange
  { t: 0.83, r: 90,  g: 60,  b: 115 },  // dusk — purple
  { t: 1.00, r: 30,  g: 45,  b: 95  },  // wrap
];

const lerp = (a: number, b: number, k: number) => a + (b - a) * k;

const sample = (t: number): { r: number; g: number; b: number } => {
  t = ((t % 1) + 1) % 1;
  for (let i = 0; i < STOPS.length - 1; i++) {
    const a = STOPS[i];
    const b = STOPS[i + 1];
    if (t >= a.t && t <= b.t) {
      const k = (t - a.t) / (b.t - a.t);
      return { r: lerp(a.r, b.r, k), g: lerp(a.g, b.g, k), b: lerp(a.b, b.b, k) };
    }
  }
  return { r: 255, g: 255, b: 255 };
};

export const useDayNightCycle = () => {
  const enabled = ref(false);
  const auto = ref(true);
  // time of day in [0, 1)
  const time = ref(0.5);
  // full cycle duration in seconds
  const cycleSeconds = ref(60);

  const tick = (dtSeconds: number) => {
    if (!enabled.value || !auto.value) return;
    time.value = (time.value + dtSeconds / cycleSeconds.value) % 1;
  };

  const tintColor = computed(() => {
    if (!enabled.value) return null;
    const c = sample(time.value);
    return `rgb(${c.r | 0}, ${c.g | 0}, ${c.b | 0})`;
  });

  const phaseLabel = computed(() => {
    const t = time.value;
    if (t < 0.22 || t >= 0.85) return "Night";
    if (t < 0.30) return "Sunrise";
    if (t < 0.45) return "Morning";
    if (t < 0.55) return "Noon";
    if (t < 0.70) return "Afternoon";
    if (t < 0.80) return "Sunset";
    return "Dusk";
  });

  return { enabled, auto, time, cycleSeconds, tick, tintColor, phaseLabel };
};

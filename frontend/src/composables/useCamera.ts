import { ref } from "vue";

export interface CameraOpts {
  viewportW: number;
  viewportH: number;
  initialZoom?: number;
  initialOffsetX?: number;
  initialOffsetY?: number;
  minZoom?: number;
  maxZoom?: number;
}

export function useCamera(canvasRef: { value: HTMLCanvasElement | null }, opts: CameraOpts) {
  const zoom = ref(opts.initialZoom ?? 1);
  const offsetX = ref(opts.initialOffsetX ?? 0);
  const offsetY = ref(opts.initialOffsetY ?? 0);
  const isDragging = ref(false);
  const minZoom = opts.minZoom ?? 0.1;
  const maxZoom = opts.maxZoom ?? 5;

  let lastMouseX = 0;
  let lastMouseY = 0;

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

  const beginPan = (clientX: number, clientY: number) => {
    isDragging.value = true;
    lastMouseX = clientX;
    lastMouseY = clientY;
  };

  const updatePan = (clientX: number, clientY: number) => {
    if (!isDragging.value) return;
    offsetX.value += clientX - lastMouseX;
    offsetY.value += clientY - lastMouseY;
    lastMouseX = clientX;
    lastMouseY = clientY;
  };

  const endPan = () => {
    isDragging.value = false;
  };

  const reset = () => {
    zoom.value = opts.initialZoom ?? 1;
    offsetX.value = opts.initialOffsetX ?? 0;
    offsetY.value = opts.initialOffsetY ?? 0;
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
    zoom.value = Math.max(minZoom, Math.min(maxZoom, zoom.value * scaleFactor));
    offsetX.value = mouseX - worldX * zoom.value;
    offsetY.value = mouseY - worldY * zoom.value;
  };

  return {
    zoom,
    offsetX,
    offsetY,
    isDragging,
    getWorldCoords,
    beginPan,
    updatePan,
    endPan,
    onWheel,
    reset,
  };
}

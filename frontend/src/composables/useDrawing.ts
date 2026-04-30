import { ref } from "vue";

export type BrushType = "pen" | "marker" | "dashed" | "eraser";

export interface DrawnPath {
  points: { x: number; y: number }[];
  color: string;
  type: BrushType;
  width: number;
}

export const BRUSH_LABELS: Record<BrushType, string> = {
  pen: "Pen",
  marker: "Marker",
  dashed: "Dashed",
  eraser: "Eraser",
};

export function useDrawing() {
  const isDrawMode = ref(false);
  const drawColor = ref("#ff3366");
  const brushType = ref<BrushType>("pen");
  const brushWidth = ref(4);
  const drawnPaths = ref<DrawnPath[]>([]);
  const currentPath = ref<{ x: number; y: number }[]>([]);
  // World-space width of the in-progress stroke, captured at draw start.
  let currentWorldWidth = 4;

  const beginPath = (point: { x: number; y: number }, zoomAtDraw: number) => {
    currentPath.value = [point];
    currentWorldWidth = brushWidth.value / Math.max(zoomAtDraw, 0.0001);
  };

  const extendPath = (point: { x: number; y: number }) => {
    currentPath.value.push(point);
  };

  const commitPath = () => {
    if (currentPath.value.length > 0) {
      drawnPaths.value.push({
        points: [...currentPath.value],
        color: drawColor.value,
        type: brushType.value,
        width: currentWorldWidth,
      });
      currentPath.value = [];
    }
  };

  const undo = () => {
    drawnPaths.value.pop();
  };

  const clear = () => {
    drawnPaths.value = [];
    currentPath.value = [];
  };

  // `width` here is in world units — the path-coordinate space before the camera transform.
  const applyBrushStyle = (
    ctx: CanvasRenderingContext2D,
    type: BrushType,
    color: string,
    width: number,
  ) => {
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    switch (type) {
      case "marker":
        ctx.globalAlpha = 0.45;
        ctx.lineWidth = width * 2;
        break;
      case "dashed":
        ctx.setLineDash([width * 2, width * 1.5]);
        break;
      case "eraser":
        ctx.globalCompositeOperation = "destination-out";
        ctx.lineWidth = width * 1.5;
        break;
    }
  };

  const drawSegment = (
    ctx: CanvasRenderingContext2D,
    points: { x: number; y: number }[],
  ) => {
    if (points.length < 2) return;
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y);
    ctx.stroke();
  };

  const renderPaths = (ctx: CanvasRenderingContext2D) => {
    drawnPaths.value.forEach((item) => {
      applyBrushStyle(ctx, item.type, item.color, item.width);
      drawSegment(ctx, item.points);
    });
    if (currentPath.value.length > 0) {
      applyBrushStyle(ctx, brushType.value, drawColor.value, currentWorldWidth);
      drawSegment(ctx, currentPath.value);
    }
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
  };

  return {
    isDrawMode,
    drawColor,
    brushType,
    brushWidth,
    drawnPaths,
    currentPath,
    beginPath,
    extendPath,
    commitPath,
    undo,
    clear,
    renderPaths,
  };
}

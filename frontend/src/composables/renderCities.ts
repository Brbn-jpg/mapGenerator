export const renderCities = (
  ctx: CanvasRenderingContext2D,
  cities: { x: number; y: number }[],
  zoom: number,
) => {
  if (cities.length === 0) return;
  ctx.lineWidth = Math.max(0.5, 1 / zoom);
  ctx.strokeStyle = "#000000";

  cities.forEach(({ x, y }) => {
    const hash = (Math.abs(Math.sin(x * 12.9898 + y * 78.233)) * 43758.5453) % 1;
    if (hash < 0.3) return;

    if (hash > 0.75) {
      const h = 8 + Math.floor(hash * 8);
      const w = 5;
      ctx.fillStyle = "#a9a9a9";
      ctx.fillRect(x - w / 2, y - h + 2, w, h);
      ctx.strokeRect(x - w / 2, y - h + 2, w, h);
      ctx.fillStyle = "#ffff00";
      for (let row = 0; row < Math.floor(h / 4); row++) {
        ctx.fillRect(x - 1.5, y - h + 4 + row * 4, 1, 1);
        ctx.fillRect(x + 0.5, y - h + 4 + row * 4, 1, 1);
      }
    } else {
      const w = 5;
      const h = 4;
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(x - w / 2, y - h / 2 + 1, w, h);
      ctx.strokeRect(x - w / 2, y - h / 2 + 1, w, h);
      ctx.fillStyle = "#8b0000";
      ctx.beginPath();
      ctx.moveTo(x - w / 2 - 1, y - h / 2 + 1);
      ctx.lineTo(x, y - h / 2 - 2);
      ctx.lineTo(x + w / 2 + 1, y - h / 2 + 1);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = "#5d4037";
      ctx.fillRect(x, y, 1.5, 2.5);
      ctx.fillStyle = "#add8e6";
      ctx.fillRect(x - 1.5, y - 0.5, 1, 1);
    }
  });
};

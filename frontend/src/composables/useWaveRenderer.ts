export function useWaveRenderer(refreshEvery = 3) {
  const waveCanvas = document.createElement("canvas");
  const waveCtx = waveCanvas.getContext("2d");
  let frame = 0;

  const renderTo = (w: number, time: number) => {
    if (!waveCtx) return;
    if (waveCanvas.width !== w || waveCanvas.height !== w) {
      waveCanvas.width = w;
      waveCanvas.height = w;
    }
    const ctx = waveCtx;
    const scale = w / 1000;
    const step = Math.max(2, 32 * scale);
    const margin = 40 * scale;

    ctx.fillStyle = "#143d70";
    ctx.fillRect(0, 0, w, w);
    ctx.lineWidth = Math.max(0.5, 2 * scale);
    ctx.strokeStyle = "#0d2c54";

    for (let y = -margin; y <= w + margin; y += step) {
      ctx.beginPath();
      ctx.moveTo(0, y + margin);
      for (let x = 0; x <= w; x += step) {
        const smoothBase = Math.sin(x * (0.015 / scale) + time + y * (0.015 / scale));
        const sharpPeaks =
          1 - Math.abs(Math.sin(x * (0.08 / scale) - time * 1.3 + y * (0.2 / scale)));
        const wave = (smoothBase * 3 + sharpPeaks * 8) * scale;
        const jitterX = Math.sin(x * (1.5 / scale) + y * (2.1 / scale)) * (1.2 * scale);
        const jitterY = Math.cos(x * (2.3 / scale) - y * (1.7 / scale)) * (1.2 * scale);
        ctx.lineTo(x + jitterX, y - wave + jitterY + margin);
      }
      ctx.lineTo(w, y + step + margin);
      ctx.lineTo(0, y + step + margin);
      ctx.closePath();
      const isAlternate = Math.round(y / step) % 2 === 0;
      ctx.fillStyle = isAlternate ? "#1a5094" : "#1d5ba8";
      ctx.fill();
      ctx.stroke();
    }
  };

  const tick = (w: number, time: number) => {
    if (
      waveCanvas.width !== w ||
      waveCanvas.height !== w ||
      frame % refreshEvery === 0
    ) {
      renderTo(w, time);
    }
    frame++;
  };

  return { waveCanvas, tick };
}

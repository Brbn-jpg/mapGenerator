import { onMounted, onUnmounted } from "vue";

export interface ShortcutHandlers {
  randomizeSeed: () => void;
  toggleDrawMode: () => void;
  toggleTopology: () => void;
  undoDrawing: () => void;
  clearDrawing: () => void;
  generate: () => void;
  resetCamera: () => void;
  selectBrush: (index: number) => void;
}

const isTypingTarget = (el: EventTarget | null) => {
  if (!(el instanceof HTMLElement)) return false;
  const tag = el.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || el.isContentEditable;
};

export function useKeyboardShortcuts(h: ShortcutHandlers) {
  const onKeyDown = (e: KeyboardEvent) => {
    if (isTypingTarget(e.target)) return;

    const ctrl = e.ctrlKey || e.metaKey;

    if (ctrl && e.key.toLowerCase() === "z") {
      e.preventDefault();
      h.undoDrawing();
      return;
    }

    if (ctrl) return; // ignore other modified keys

    switch (e.key) {
      case "r":
      case "R":
        h.randomizeSeed();
        break;
      case " ":
        e.preventDefault();
        h.toggleDrawMode();
        break;
      case "h":
      case "H":
        h.toggleTopology();
        break;
      case "g":
      case "G":
        h.generate();
        break;
      case "Escape":
        h.resetCamera();
        break;
      case "Delete":
      case "Backspace":
        h.clearDrawing();
        break;
      case "1":
      case "2":
      case "3":
      case "4":
        h.selectBrush(Number(e.key) - 1);
        break;
    }
  };

  onMounted(() => window.addEventListener("keydown", onKeyDown));
  onUnmounted(() => window.removeEventListener("keydown", onKeyDown));
}

<script setup lang="ts">
import type { MapRecord } from "../api/maps";

const props = defineProps<{ items: MapRecord[]; activeId: string }>();
const emit = defineEmits<{ (e: "select", record: MapRecord): void }>();
</script>

<template>
  <div class="history-section" v-if="props.items.length > 0">
    <div class="section-label">Recent Maps</div>
    <div class="history-list">
      <div
        v-for="m in props.items"
        :key="m.id"
        class="history-item"
        :class="{ 'is-active': props.activeId === String(m.id) }"
        @click="emit('select', m)"
      >
        <div class="history-item-row">
          <span class="history-id">#{{ m.id }}</span>
          <span class="history-status" :class="m.status.toLowerCase()">{{ m.status }}</span>
        </div>
        <div class="history-item-row history-meta">
          <span>{{ m.size }}×{{ m.size }}</span>
          <span>seed {{ m.seed }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.history-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.section-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 1px;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.history-item {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.history-item:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.12);
}
.history-item.is-active {
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(59, 130, 246, 0.08);
}
.history-item-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.history-id {
  font-family: monospace;
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
}
.history-status {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 2px 6px;
  border-radius: 4px;
}
.history-status.completed {
  background: rgba(34, 197, 94, 0.15);
  color: #86efac;
}
.history-status.in_progress {
  background: rgba(234, 179, 8, 0.15);
  color: #fde047;
}
.history-meta {
  font-size: 11px;
  color: #64748b;
}
</style>

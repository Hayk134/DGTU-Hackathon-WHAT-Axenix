<template>
  <div
    class="chrome-node"
    :class="[
      variantClass, 
      heatLevel ? `heat-${heatLevel}` : '', 
      { 
        'node-defect-high': data.severity === 'HIGH' && !isHeatmap, 
        'node-defect-loop': data.complexity?.includes('n²') && !isHeatmap 
      }
    ]"
    @click="handleClick"
    @dblclick.stop="$emit('node-dblclick', { node: props })"
    @contextmenu.prevent.stop="onContextMenu"
    :title="data.isOverview ? 'ЛКМ: открыть граф метода во вкладке и перейти к коду' : 'ЛКМ: открыть код'"
  >
    <div class="node-header">
      <div class="flex items-center gap-1.5 min-w-0">
        <span class="node-type">{{ data.type || 'STATEMENT' }}</span>
        <button
          v-if="data.childCount && data.isOverview"
          type="button"
          class="node-overview-badge"
          @click.stop="$emit('open-tab', id)"
          title="Открыть граф метода во вкладке"
        >
          <span>↗ {{ data.childCount }} шагов</span>
        </button>
        <button
          v-else-if="data.childCount"
          type="button"
          class="node-ladder-btn"
          :class="{ 'is-open': data.isExpanded }"
          @click.stop="$emit('toggle-expand', id)"
          title="Свернуть / развернуть шаги"
        >
          <span>{{ data.isExpanded ? '▼ Свернуть' : `▶ ${data.childCount} шагов` }}</span>
        </button>
      </div>
      <div class="flex items-center gap-1.5 shrink-0">
        <span v-if="heatBadgeText" class="node-heat-badge" :class="`badge-${heatLevel}`">{{ heatBadgeText }}</span>
        <span v-if="data.line" class="node-line">:{{ data.line }}</span>
      </div>
    </div>

    <div class="node-body">
      <div class="flex items-center gap-1.5 min-w-0">
        <div class="status-dot" :class="statusDotClass"></div>
        <span class="node-label">{{ data.label }}</span>
      </div>
      <div v-if="data.sublabel" class="node-sublabel">{{ data.sublabel }}</div>
      <code v-if="data.code" class="node-code">{{ data.code }}</code>
    </div>

    <Handle type="target" :position="Position.Top" class="handle-target" />
    <Handle type="source" :position="Position.Bottom" class="handle-source" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { getNodeHeatLevel, getHeatBadgeText } from '../../utils/heatmap'

interface NodeData {
  label: string
  sublabel?: string
  code?: string
  type?: string
  line?: number
  severity?: 'HIGH' | 'MEDIUM' | 'LOW'
  complexity?: string
  variant?: 'default' | 'primary' | 'success' | 'warning'
  isMethod?: boolean
  childCount?: number
  isExpanded?: boolean
  isOverview?: boolean
  heatmapMode?: boolean
}

const props = withDefaults(defineProps<{
  id: string
  data: NodeData
  heatmapMode?: boolean
}>(), {
  heatmapMode: false
})

const emit = defineEmits(['node-click', 'node-dblclick', 'toggle-expand', 'open-tab'])

function handleClick() {
  emit('node-click', { node: props })
  if (props.data.isOverview) {
    emit('open-tab', props.id)
  }
}

function onContextMenu() {
  if (props.data.isOverview) {
    emit('open-tab', props.id)
  } else if (props.data.isMethod || props.data.childCount) {
    emit('toggle-expand', props.id)
  }
}

const isHeatmap = computed(() => props.heatmapMode || props.data?.heatmapMode || false)

const heatLevel = computed(() => {
  if (!isHeatmap.value) return null
  return getNodeHeatLevel(props.data)
})

const heatBadgeText = computed(() => {
  if (!heatLevel.value) return null
  return getHeatBadgeText(heatLevel.value, props.data)
})

const variantClass = computed(() => `variant-${props.data.variant || 'default'}`)
const statusDotClass = computed(() => {
  if (isHeatmap.value) {
    if (heatLevel.value === 'critical') return 'bg-[#ef4444]'
    if (heatLevel.value === 'high') return 'bg-[#f97316]'
    if (heatLevel.value === 'medium') return 'bg-[#eab308]'
    return 'bg-[#10b981]'
  }
  return {
    'bg-[#b8d9c5]': props.data.variant === 'success',
    'bg-[#f0c9a8]': props.data.variant === 'warning',
    'bg-[#b8d8f0]': props.data.variant === 'primary',
    'bg-[#c5c9df]': !props.data.variant || props.data.variant === 'default',
  }
})
</script>

<style scoped>
.chrome-node {
  min-width: 180px;
  max-width: 260px;
  border-radius: 6px;
  overflow: visible;
  background: #10131a;
  border: 1px solid rgba(255, 255, 255, 0.09);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.15s ease, background 0.2s ease;
  cursor: pointer;
}

.chrome-node:hover {
  border-color: rgba(223, 230, 247, 0.72);
  box-shadow: 0 4px 16px rgba(176, 196, 222, 0.14);
  transform: translateY(-1px);
}

.chrome-node.node-defect-high {
  border-color: #b58585;
  box-shadow: 0 0 0 1px rgba(181, 133, 133, 0.32), 0 0 14px rgba(181, 133, 133, 0.18);
}

.chrome-node.node-defect-loop {
  border-color: #cfc4b4;
  box-shadow: 0 0 0 1px rgba(207, 196, 180, 0.28), 0 0 14px rgba(207, 196, 180, 0.14);
}

/* Complexity Heatmap Styles */
.chrome-node.heat-critical {
  border-color: #ef4444 !important;
  background: linear-gradient(180deg, rgba(239, 68, 68, 0.20), #15090b) !important;
  box-shadow: 0 0 18px rgba(239, 68, 68, 0.45), inset 0 0 12px rgba(239, 68, 68, 0.15) !important;
}

.chrome-node.heat-high {
  border-color: #f97316 !important;
  background: linear-gradient(180deg, rgba(249, 115, 22, 0.18), #160d09) !important;
  box-shadow: 0 0 15px rgba(249, 115, 22, 0.40), inset 0 0 10px rgba(249, 115, 22, 0.12) !important;
}

.chrome-node.heat-medium {
  border-color: #eab308 !important;
  background: linear-gradient(180deg, rgba(234, 179, 8, 0.16), #171409) !important;
  box-shadow: 0 0 12px rgba(234, 179, 8, 0.30) !important;
}

.chrome-node.heat-cool {
  border-color: #10b981 !important;
  background: linear-gradient(180deg, rgba(16, 185, 129, 0.14), #09140e) !important;
  box-shadow: 0 0 10px rgba(16, 185, 129, 0.22) !important;
}

.node-heat-badge {
  font-family: 'JetBrains Mono', monospace;
  font-size: 8.5px;
  font-weight: 800;
  padding: 1px 5px;
  border-radius: 4px;
  line-height: 1.2;
  letter-spacing: 0.02em;
}
.badge-critical { background: rgba(239, 68, 68, 0.3); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.6); }
.badge-high { background: rgba(249, 115, 22, 0.3); color: #fdba74; border: 1px solid rgba(249, 115, 22, 0.6); }
.badge-medium { background: rgba(234, 179, 8, 0.3); color: #fde047; border: 1px solid rgba(234, 179, 8, 0.6); }
.badge-cool { background: rgba(16, 185, 129, 0.3); color: #6ee7b7; border: 1px solid rgba(16, 185, 129, 0.6); }

.node-header {
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  gap: 8px;
  background: #171b24;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px 6px 0 0;
}

.node-type,
.node-line {
  font-family: 'JetBrains Mono', monospace;
  font-size: 9px;
  letter-spacing: 0.06em;
  color: #bdc8d8;
}

.node-type { font-weight: 700; }
.node-line { color: #929eae; }

.node-body { padding: 8px 10px 9px; }
.status-dot { width: 6px; height: 6px; border-radius: 999px; flex-shrink: 0; }
.node-label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 650;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-sublabel {
  margin-top: 4px;
  font-size: 9px;
  color: #aeb8c7;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.node-code {
  display: block;
  margin-top: 5px;
  color: #c9d1dd;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  line-height: 1.35;
  max-height: 2.7em;
  overflow: hidden;
  white-space: pre-wrap;
}

.handle-target,
.handle-source {
  width: 8px;
  height: 8px;
  border-radius: 1px;
  background: #c7cbd0;
  border: 1px solid #717983;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55), 0 0 5px rgba(220, 232, 244, 0.35);
}
.handle-target:hover,
.handle-source:hover { background: #e0e4e8; border-color: #f4f6f8; }
</style>

<template>
  <div class="relative">
    <button
      class="jb-btn-silver !w-7 !h-7 !p-0 relative overflow-hidden transition-all flex items-center justify-center cursor-pointer select-none"
      @click="handleClick"
      :disabled="isUploading"
      :title="isUploading ? 'Загрузка...' : 'Импортировать Java-файлы'"
    >
      <FileInput class="w-3.5 h-3.5" />
      
      <div 
        v-if="isUploading"
        class="absolute inset-0 bg-black/60 flex items-center justify-center"
      >
        <Loader2 class="w-3.5 h-3.5 animate-spin text-white" />
      </div>
    </button>

    <input
      ref="fileInput"
      type="file"
      accept=".java,.txt"
      multiple
      class="hidden"
      @change="handleFileChange"
    />

    <Transition
      enter-active-class="transition-all duration-200"
      enter-from-class="opacity-0 translate-y-2"
      leave-active-class="transition-all duration-150"
      leave-to-class="opacity-0 translate-y-1"
    >
      <div
        v-if="statusMessage"
        class="absolute top-full mt-2 right-0 min-w-[240px] px-3 py-2 rounded border text-xs font-mono whitespace-nowrap z-50 shadow-2xl"
        :class="statusClass"
      >
        <div class="flex items-center gap-2">
          <Check v-if="statusType === 'success'" class="w-3.5 h-3.5 shrink-0" />
          <AlertTriangle v-else-if="statusType === 'error'" class="w-3.5 h-3.5 shrink-0" />
          <Info v-else class="w-3.5 h-3.5 shrink-0" />
          <span>{{ statusMessage }}</span>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { FileInput, Loader2, Check, AlertTriangle, Info } from 'lucide-vue-next'

export interface LoadProjectResult {
  project: {
    files: Record<string, string>
    mainFile: string
  }
  graph: {
    nodes: any[]
    edges: any[]
  }
  analysis: {
    defects: any[]
    metrics: any
    recommendations: any[]
  }
}

const emit = defineEmits<{
  filesLoaded: [result: LoadProjectResult]
  error: [message: string]
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const isUploading = ref(false)
const statusMessage = ref('')
const statusType = ref<'success' | 'error' | 'info'>('info')

const statusClass = computed(() => {
  switch (statusType.value) {
    case 'success':
      return 'bg-[#0e1410] border-sage-border text-sage-text'
    case 'error':
      return 'bg-[#14100e] border-[rgba(181,133,133,0.35)] text-[#b58585]'
    default:
      return 'bg-graphite-800 border-white/20 text-silver-brushed'
  }
})

function handleClick() {
  if (!isUploading.value) {
    fileInput.value?.click()
  }
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const files = target.files
  
  if (!files || files.length === 0) {
    return
  }

  isUploading.value = true
  statusMessage.value = ''

  try {
    const filePromises = Array.from(files).map(file => readFile(file))
    const loadedFiles = await Promise.all(filePromises)

    const fileMap: Record<string, string> = {}
    loadedFiles.forEach(f => {
      fileMap[f.name] = f.content
    })

    showStatus('Отправка в backend...', 'info')
    
    const response = await fetch('http://localhost:8080/api/project/load', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        files: fileMap
      })
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ message: 'Ошибка сети' }))
      throw new Error(errorData.details || errorData.error || errorData.message || `HTTP ${response.status}`)
    }

    const result: LoadProjectResult = await response.json()

    showStatus(
      `Загружено ${Object.keys(fileMap).length} файл(ов)`,
      'success',
      2500
    )
    
    emit('filesLoaded', result)

  } catch (error) {
    const message = error instanceof Error ? error.message : 'Неизвестная ошибка'
    showStatus(`Ошибка: ${message}`, 'error', 4000)
    emit('error', message)
    console.error('File import error:', error)
  } finally {
    isUploading.value = false
    if (target) {
      target.value = ''
    }
  }
}

function readFile(file: File): Promise<{ name: string; content: string }> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    
    reader.onload = (e) => {
      if (e.target?.result) {
        resolve({
          name: file.name,
          content: e.target.result as string
        })
      } else {
        reject(new Error(`Не удалось прочитать ${file.name}`))
      }
    }
    
    reader.onerror = () => {
      reject(new Error(`Ошибка чтения ${file.name}`))
    }
    
    reader.readAsText(file, 'UTF-8')
  })
}

function showStatus(
  message: string,
  type: 'success' | 'error' | 'info',
  duration: number = 3000
) {
  statusMessage.value = message
  statusType.value = type

  if (duration > 0) {
    setTimeout(() => {
      statusMessage.value = ''
    }, duration)
  }
}

defineExpose({
  openFilePicker: () => fileInput.value?.click()
})
</script>

<style scoped>
.font-mono {
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-variant-ligatures: none;
}
</style>

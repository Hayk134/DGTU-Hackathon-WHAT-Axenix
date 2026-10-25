import { ref, computed } from 'vue'
import * as api from '../api/client'
import type {
  ProjectWorkspaceResponse,
  OptimizationResult,
  VerificationResult,
} from '../api/client'

export interface ConsoleLog {
  text: string
  color: string
}

export function useBackendIntegration() {
  const isConnected = ref(false)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  
  const currentProject = ref<ProjectWorkspaceResponse | null>(null)
  const consoleLogs = ref<ConsoleLog[]>([])
  
  const verificationResult = ref<VerificationResult | null>(null)
  const isRunningTests = ref(false)

  const activeCode = computed(() => {
    if (!currentProject.value) return ''
    const mainFile = currentProject.value.project.mainFile
    return currentProject.value.project.files[mainFile] || ''
  })

  const graphNodes = computed(() => currentProject.value?.graph.nodes || [])
  const graphEdges = computed(() => currentProject.value?.graph.edges || [])
  const defects = computed(() => currentProject.value?.analysis.defects || [])
  const metrics = computed(() => currentProject.value?.analysis.metrics)

  const overallBigO = computed(() => {
    const nestedDefects = defects.value.filter(d => d.type === 'NESTED_COMPLEXITY')
    if (nestedDefects.length > 0) return 'O(n²)'
    return null
  })

  const hasErrors = computed(() => {
    return defects.value.some(d => d.severity === 'HIGH')
  })

  async function checkConnection() {
    try {
      const health = await api.checkHealth()
      isConnected.value = true
      addLog(`[Backend] Connected to server: ${health.service}`, 'text-[#a4c0ac]')
      return true
    } catch (err) {
      isConnected.value = false
      addLog('[Backend] Connection failed. Backend is unavailable.', 'text-[#cfc4b4]')
      return false
    }
  }

  async function loadProjectFromFiles(files: Record<string, string>) {
    isLoading.value = true
    addLog('[Loader] Loading uploaded files...', 'text-silver-300')
    
    try {
      const workspace = await api.loadProject({ files })
      currentProject.value = workspace
      addLog('[Parser] Files processed successfully', 'text-[#a4c0ac]')
      error.value = null
    } catch (err: any) {
      error.value = err.message
      addLog(`[Error] Failed to load files: ${err.message}`, 'text-[#b58585]')
    } finally {
      isLoading.value = false
    }
  }

  async function reParseCode(filePath: string, content: string) {
    isLoading.value = true
    addLog(`[Parser] Re-parsing ${filePath}...`, 'text-silver-300')
    
    try {
      const workspace = await api.parseCode({ filePath, content })
      currentProject.value = workspace
      addLog('[Parser] Code updated, graph rebuilt', 'text-[#a4c0ac]')
      error.value = null
    } catch (err: any) {
      error.value = err.message
      addLog(`[Error] Parse failed: ${err.message}`, 'text-[#b58585]')
    } finally {
      isLoading.value = false
    }
  }

  async function updateNodeCode(nodeId: string, newCode: string) {
    isLoading.value = true
    addLog(`[Mutator] Updating node ${nodeId}...`, 'text-silver-300')
    
    try {
      const workspace = await api.mutateGraph({ nodeId, operation: 'UPDATE', newCode })
      currentProject.value = workspace
      addLog('[Mutator] Node updated, changes synced', 'text-[#a4c0ac]')
      error.value = null
    } catch (err: any) {
      error.value = err.message
      addLog(`[Error] Mutation failed: ${err.message}`, 'text-[#b58585]')
    } finally {
      isLoading.value = false
    }
  }

  async function optimizeAndDecompose(applyOptimizations: boolean, decomposeMonolith: boolean) {
    isLoading.value = true
    addLog('[Optimizer] Running optimization pipeline...', 'text-silver-300')
    
    try {
      const result: OptimizationResult = await api.optimizeProject({
        applyOptimizations,
        decomposeMonolith,
      })
      
      currentProject.value = result.after
      
      addLog(`[Optimizer] Applied ${result.changes.length} optimizations`, 'text-[#a4c0ac]')
      
      if (decomposeMonolith) {
        const fileCount = Object.keys(result.after.project.files).length
        addLog(`[Decomposer] Monolith split into ${fileCount} modules`, 'text-[#a4c0ac]')
      }
      
      if (result.verification.compilationSuccess) {
        const speedup = result.verification.speedupFactor.toFixed(2)
        addLog(`[Verification] Tests passed, speedup: ${speedup}x`, 'text-[#a4c0ac]')
        verificationResult.value = result.verification
      } else {
        addLog('[Verification] Compilation failed', 'text-[#b58585]')
      }
      
      error.value = null
    } catch (err: any) {
      error.value = err.message
      addLog(`[Error] Optimization failed: ${err.message}`, 'text-[#b58585]')
    } finally {
      isLoading.value = false
    }
  }

  async function runVerification() {
    isRunningTests.value = true
    addLog('[TestRunner] Starting verification...', 'text-silver-300')
    
    try {
      const result = await api.verifyProject()
      verificationResult.value = result
      
      if (result.compilationSuccess) {
        const passedCount = result.testResults.filter(t => t.passed).length
        const totalCount = result.testResults.length
        addLog(
          `[TestRunner] ${passedCount}/${totalCount} tests passed`,
          'text-[#a4c0ac]'
        )
        
        const speedup = result.speedupFactor.toFixed(2)
        addLog(`[Performance] Speedup factor: ${speedup}x`, 'text-[#a4c0ac]')
      } else {
        addLog('[TestRunner] Compilation failed', 'text-[#b58585]')
        if (result.originalErrors) {
          addLog(`  • Original: ${result.originalErrors}`, 'text-[#b58585]')
        }
        if (result.optimizedErrors) {
          addLog(`  • Optimized: ${result.optimizedErrors}`, 'text-[#b58585]')
        }
      }
    } catch (err: any) {
      addLog(`[Error] Verification failed: ${err.message}`, 'text-[#b58585]')
    } finally {
      isRunningTests.value = false
    }
  }

  function addLog(text: string, color: string = 'text-silver-300') {
    const timestamp = new Date().toLocaleTimeString('ru-RU', { 
      hour: '2-digit', 
      minute: '2-digit', 
      second: '2-digit' 
    })
    consoleLogs.value.push({ 
      text: `[${timestamp}] ${text}`, 
      color 
    })
  }

  function clearLogs() {
    consoleLogs.value = []
  }

  return {
    isConnected,
    isLoading,
    error,
    currentProject,
    consoleLogs,
    verificationResult,
    isRunningTests,
    
    activeCode,
    graphNodes,
    graphEdges,
    defects,
    metrics,
    overallBigO,
    hasErrors,
    
    checkConnection,
    loadProjectFromFiles,
    reParseCode,
    updateNodeCode,
    optimizeAndDecompose,
    runVerification,
    addLog,
    clearLogs,
  }
}

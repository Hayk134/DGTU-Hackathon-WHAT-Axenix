// API client for Java Blueprints Backend
const API_BASE_URL = 'http://localhost:8080/api'

export interface GraphNode {
  id: string
  type: string
  label: string
  code: string
  location: SourceLocation
  complexity?: number
  metadata?: Record<string, string>
}

export interface GraphEdge {
  from: string
  to: string
  label?: string
  condition?: string
}

export interface SourceLocation {
  filePath: string
  startLine: number
  endLine: number
  startColumn: number
  endColumn: number
}

export interface Defect {
  id: string
  type: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  location: SourceLocation
  title: string
  description: string
  complexity?: string
  codeFragment: string
}

export interface CodeMetrics {
  totalLines: number
  totalMethods: number
  averageComplexity: number
  maxComplexity: number
  duplicatedBlocks: number
}

export interface Recommendation {
  defectId: string
  title: string
  description: string
  expectedBenefit: string
}

export interface AnalysisReport {
  defects: Defect[]
  metrics: CodeMetrics
  recommendations: Recommendation[]
}

export interface BlueprintGraph {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface ProjectState {
  files: Record<string, string>
  mainFile: string
}

export interface ProjectWorkspaceResponse {
  project: ProjectState
  graph: BlueprintGraph
  analysis: AnalysisReport
}

export interface OptimizationChange {
  type: string
  description: string
  location: SourceLocation
  complexityBefore: string
  complexityAfter: string
}

export interface TestResult {
  testName: string
  passed: boolean
  originalTime: number
  optimizedTime: number
  message: string
}

export interface VerificationResult {
  compilationSuccess: boolean
  testResults: TestResult[]
  originalExecutionTime: number
  optimizedExecutionTime: number
  speedupFactor: number
  behaviorPreserved: boolean
  originalOutput: string
  optimizedOutput: string
  originalErrors: string
  optimizedErrors: string
}

export interface OptimizationResult {
  before: ProjectWorkspaceResponse
  after: ProjectWorkspaceResponse
  changes: OptimizationChange[]
  verification: VerificationResult
}

export async function checkHealth(): Promise<{ status: string; service: string }> {
  const response = await fetch(`${API_BASE_URL}/health`)
  if (!response.ok) throw new Error('Backend not responding')
  return response.json()
}

export async function loadProject(params: {
  files: Record<string, string>
}): Promise<ProjectWorkspaceResponse> {
  const response = await fetch(`${API_BASE_URL}/project/load`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
  if (!response.ok) throw new Error('Failed to load project')
  return response.json()
}

export async function loadDemoProject(): Promise<ProjectWorkspaceResponse> {
  const response = await fetch(`${API_BASE_URL}/project/load-demo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  })
  if (!response.ok) throw new Error('Failed to load demo project')
  return response.json()
}

export async function parseCode(params: {
  filePath: string
  content: string
}): Promise<ProjectWorkspaceResponse> {
  const response = await fetch(`${API_BASE_URL}/project/parse`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
  if (!response.ok) throw new Error('Failed to parse code')
  return response.json()
}

export async function mutateGraph(params: {
  filePath?: string
  nodeId: string
  operation: 'UPDATE' | 'DELETE' | 'ADD'
  newCode?: string
}): Promise<ProjectWorkspaceResponse> {
  const response = await fetch(`${API_BASE_URL}/graph/mutate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
  if (!response.ok) {
    const err = await response.json().catch(() => ({}))
    throw new Error(err.details || err.error || `HTTP ${response.status}`)
  }
  return response.json()
}

export async function optimizeProject(params: {
  applyOptimizations: boolean
  decomposeMonolith: boolean
}): Promise<OptimizationResult> {
  const response = await fetch(`${API_BASE_URL}/project/optimize`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
  if (!response.ok) throw new Error('Failed to optimize project')
  return response.json()
}

export async function optimizeProjectWithAi(): Promise<OptimizationResult> {
  const response = await fetch(`${API_BASE_URL}/project/optimize-ai`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  })
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.details || errorData.error || `HTTP ${response.status}`)
  }
  return response.json()
}

export async function applyOptimization(params: {
  files: Record<string, string>
  mainFile?: string
}): Promise<ProjectWorkspaceResponse> {
  const response = await fetch(`${API_BASE_URL}/project/apply-optimization`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.details || errorData.error || `HTTP ${response.status}`)
  }
  return response.json()
}

export async function verifyProject(params?: { inputs?: string[] }): Promise<VerificationResult> {
  const response = await fetch(`${API_BASE_URL}/project/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params || {}),
  })
  if (!response.ok) throw new Error('Failed to verify project')
  return response.json()
}

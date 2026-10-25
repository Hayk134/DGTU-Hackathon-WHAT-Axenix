<template>
  <div class="reference-shell h-screen w-screen flex flex-col bg-[#050608] text-silver-brushed overflow-hidden font-sans select-none">
    
    <header class="ide-toolbar" role="banner">
      <div class="toolbar-brand">
        <div class="brand-mark" aria-hidden="true"><span>W</span></div>
        <div class="toolbar-title">
          <strong>WHAT</strong>
          <span class="toolbar-breadcrumb">{{ currentFile || currentMainFile || 'workspace / unassigned.java' }}</span>
        </div>
      </div>

      <div class="toolbar-run-widget" role="group" aria-label="Выполнение">
        <button class="run-action !text-[#a3be8c] hover:!text-[#b8d4c0]" :disabled="isProcessing || !projectLoaded" @click="runProjectCode" title="Скомпилировать и запустить код">
          <Play class="w-3.5 h-3.5 fill-current" />
          <span>Run</span>
        </button>
        <button class="run-action !text-[#88c0d0] font-semibold" :disabled="isProcessing || !projectLoaded" @click="runDecomposition" title="Автоматически декомпозировать монолит на модули">
          <Boxes class="w-3.5 h-3.5" />
          <span>Декомпозировать</span>
        </button>
        <button class="run-action !text-[#a4c0ac] font-semibold" :disabled="isProcessing || !projectLoaded" @click="optimizeCodeWithAi" title="Оптимизировать проект с AI (Gemini 3.8 Flash)">
          <Sparkles class="w-3.5 h-3.5" />
          <span>Оптимизировать с AI</span>
        </button>
        <button 
          v-if="pendingOptimizationResult"
          class="run-action !text-[#090b10] !bg-[#a4c0ac] hover:!bg-[#b8d4c0] font-semibold shadow-sm"
          @click="applyPendingOptimization"
          title="Записать предложенный код в проект и обновить файлы"
        >
          <Check class="w-3.5 h-3.5" />
          <span>Обновить код</span>
        </button>
        <button class="run-action !text-[#ebcb8b]" :disabled="isProcessing || !projectLoaded" @click="optimizeCode" title="Оптимизировать проект (правила AST)">
          <Zap class="w-3.5 h-3.5" />
          <span>Optimize</span>
        </button>
      </div>

      <div class="toolbar-actions">
        <span class="toolbar-inspection" :class="highSeverityDefectCount ? 'has-defects' : 'is-clean'">{{ highSeverityDefectCount }} дефектов</span>
        <FileImportButton @files-loaded="handleProjectLoaded" @error="handleImportError" />
        <button class="jb-btn-silver" :disabled="!projectLoaded" @click="exportProject" title="Скачать Java-проект">
          <Download class="w-3.5 h-3.5" />
          <span>Export Java</span>
        </button>

        <!-- Audit Report Button (Opens Modal) -->
        <button 
          class="jb-btn-silver cursor-pointer" 
          @click="openAuditModal" 
          title="Сформировать аудиторский отчет (PDF / Markdown)"
        >
          <FileText class="w-3.5 h-3.5" />
          <span>Отчет</span>
        </button>
      </div>
    </header>

    <!-- ===== 2. MAIN WORKSPACE ===== -->
    <div class="flex-1 min-h-0 flex overflow-hidden">
      
      <!-- ===== 2.1 LEFT TOOL STRIPE (38px) ===== -->
      <aside class="w-10 bg-[#090b10] border-r border-white/[0.08] flex flex-col py-2 gap-1 shrink-0">
        <button 
          @click="toggleLeftTab('project')"
          class="jb-stripe-btn"
          :class="{ active: leftTab === 'project' }"
          title="Проект"
        >
          <FolderTree class="w-4 h-4" />
        </button>

        <button 
          @click="toggleLeftTab('analysis')"
          class="jb-stripe-btn"
          :class="{ active: leftTab === 'analysis' }"
          title="Анализ"
        >
          <Gauge class="w-4 h-4" />
        </button>

        <button
          @click="toggleLeftTab('palette')"
          class="jb-stripe-btn"
          :class="{ active: leftTab === 'palette' }"
          title="Палитра блоков"
        >
          <Boxes class="w-4 h-4" />
        </button>

        <div class="flex-1"></div>

        
      </aside>

      <!-- ===== 2.2 LEFT TOOL WINDOW (Collapsible) ===== -->
      <Transition
        enter-active-class="transition-all duration-200"
        leave-active-class="transition-all duration-200"
        enter-from-class="-translate-x-full"
        leave-to-class="-translate-x-full"
      >
        <aside 
          v-if="leftTab"
          class="w-60 bg-[#0c0e15] border-r border-white/[0.08] flex flex-col shrink-0"
        >
          <!-- Tool Window Header -->
          <div class="h-8 px-3 border-b border-white/[0.08] flex items-center justify-between bg-[#0a0c13]">
            <div class="flex min-w-0 items-center gap-2">
              <span class="text-xs font-semibold text-silver-platinum uppercase tracking-wide">
                {{ leftTab === 'project' ? 'Проект' : leftTab === 'analysis' ? 'Анализ' : 'Палитра блоков' }}
              </span>
              <span v-if="leftTab === 'project'" class="text-[10px] font-mono text-silver-dark">{{ Object.keys(projectFiles).length }} файлов</span>
            </div>
            <div class="flex items-center gap-1">
              <template v-if="leftTab === 'project'">
                <button class="jb-icon-btn !h-5 !w-5" title="Развернуть все" aria-label="Развернуть все" @click="expandAllDirectories">
                  <ChevronsDown class="h-3 w-3" />
                </button>
                <button class="jb-icon-btn !h-5 !w-5" title="Свернуть все" aria-label="Свернуть все" @click="collapseAllDirectories">
                  <ChevronsUp class="h-3 w-3" />
                </button>
              </template>
              <button @click="leftTab = null" class="jb-icon-btn !w-5 !h-5">
                <X class="w-3 h-3" />
              </button>
            </div>
          </div>

          <!-- Project Tree -->
          <div v-if="leftTab === 'project'" class="flex-1 overflow-y-auto p-2">
            <div v-if="projectLoaded" class="space-y-0.5">
              <div
                v-for="entry in projectTree"
                :key="entry.path"
              >
                <button
                  v-if="entry.kind === 'directory'"
                  class="w-full px-2 py-1 rounded text-xs font-mono flex items-center gap-1.5 text-silver-steel hover:bg-graphite-card hover:text-silver-brushed"
                  @click="toggleDirectory(entry.path)"
                >
                  <component :is="expandedDirectories.has(entry.path) ? ChevronDown : ChevronRight" class="w-3 h-3 shrink-0" />
                  <FolderTree class="w-3.5 h-3.5 shrink-0 text-silver-dark" />
                  <span class="truncate">{{ entry.name }}</span>
                </button>

                <button
                  v-else
                  class="w-full px-2 py-1 rounded text-xs font-mono flex items-center gap-1.5 cursor-pointer transition-colors"
                  :class="entry.filePath === currentFile ? 'bg-graphite-hover text-white' : 'text-silver-steel hover:bg-graphite-card hover:text-silver-brushed'"
                  :style="{ paddingLeft: `${entry.depth * 14 + 22}px` }"
                  @click="selectFile(entry.filePath || entry.path)"
                >
                  <FileCode2 class="w-3.5 h-3.5 shrink-0" />
                  <span class="truncate">{{ entry.name }}</span>
                  <span class="ml-auto shrink-0 text-[9px] text-silver-dark">{{ entry.kind === 'file' ? (entry.filePath === currentMainFile ? 'main' : `${(projectFiles[entry.filePath || entry.path] || '').split(/\r?\n/).length} ln`) : '' }}</span>
                </button>
              </div>
            </div>
            <div v-else class="p-4 text-center text-xs text-silver-dark">
              <Info class="w-8 h-8 mx-auto mb-2 opacity-40" />
              <p>Импортируйте Java-файлы</p>
            </div>
          </div>

          <!-- Analysis Panel -->
          <div v-if="leftTab === 'analysis'" class="flex-1 overflow-y-auto p-2">
            <div v-if="analysisReport" class="space-y-3">
              <!-- Header with quick PDF export -->
              <div class="flex items-center justify-between px-1 text-xs">
                <span class="text-silver-steel text-[11px] font-medium">Аудит архитектуры</span>
                <button 
                  class="text-[10px] text-[#88c0d0] hover:text-[#b8e0ee] hover:underline flex items-center gap-1 cursor-pointer transition-colors"
                  @click="exportAuditReport('pdf')"
                  title="Экспорт полного аудиторского отчета в PDF"
                >
                  <FileText class="w-3 h-3" />
                  <span>Экспорт в PDF</span>
                </button>
              </div>
              <!-- Subtabs: Проблемы / Рекомендации / Метрики -->
              <div class="flex border-b border-white/[0.08] bg-[#090b10] rounded p-1 gap-1">
                <button 
                  class="flex-1 py-1 text-[10px] font-semibold rounded transition-colors text-center"
                  :class="activeAnalysisTab === 'defects' ? 'bg-[#88c0d0]/20 text-[#88c0d0]' : 'text-silver-steel hover:text-white'"
                  @click="activeAnalysisTab = 'defects'"
                >
                  Проблемы ({{ analysisReport.defects.length }})
                </button>
                <button 
                  class="flex-1 py-1 text-[10px] font-semibold rounded transition-colors text-center"
                  :class="activeAnalysisTab === 'recommendations' ? 'bg-[#a3be8c]/20 text-[#a3be8c]' : 'text-silver-steel hover:text-white'"
                  @click="activeAnalysisTab = 'recommendations'"
                >
                  Рекомендации ({{ analysisReport.recommendations.length }})
                </button>
                <button 
                  class="flex-1 py-1 text-[10px] font-semibold rounded transition-colors text-center"
                  :class="activeAnalysisTab === 'metrics' ? 'bg-[#ebcb8b]/20 text-[#ebcb8b]' : 'text-silver-steel hover:text-white'"
                  @click="activeAnalysisTab = 'metrics'"
                >
                  Метрики
                </button>
              </div>

              <!-- Complexity & Speedup Widget -->
              <div class="p-3 bg-[#131722] rounded border border-white/[0.10]">
                <div class="text-[10px] uppercase font-bold text-silver-dark tracking-wider mb-2 flex items-center justify-between">
                  <span class="flex items-center gap-1.5"><Gauge class="w-3.5 h-3.5 text-[#ebcb8b]" /> Сложность алгоритмов</span>
                  <span class="text-[#ebcb8b] font-mono text-[9px]">Big-O CFG</span>
                </div>
                <div class="flex items-center justify-between text-xs font-mono py-1.5 px-2.5 rounded bg-black/40 border border-white/[0.06]">
                  <span :class="currentBigO === 'O(1)' ? 'text-[#a3be8c]' : currentBigO === 'O(n)' ? 'text-[#ebcb8b]' : 'text-[#bf616a]'" class="font-semibold">Текущая: {{ currentBigO }}</span>
                  <span class="text-silver-dark">──►</span>
                  <span class="text-[#a3be8c] font-semibold">После: {{ targetBigO }}</span>
                </div>
                <div class="mt-2.5 flex items-center justify-between text-xs">
                  <span class="text-silver-steel">Ожидаемое ускорение:</span>
                  <span class="font-bold text-[#a3be8c] bg-[#a3be8c]/10 px-2 py-0.5 rounded border border-[#a3be8c]/30">{{ estimatedSpeedup }}</span>
                </div>
              </div>

              <!-- Subtab 1: Defects -->
              <div v-if="activeAnalysisTab === 'defects'" class="space-y-2">
                <!-- Staged optimization banner -->
                <div v-if="pendingOptimizationResult" class="p-2.5 rounded bg-emerald-950/40 border border-emerald-500/30 flex items-center justify-between gap-2">
                  <div class="text-[11px] text-emerald-300 font-medium flex items-center gap-1.5">
                    <Sparkles class="w-3.5 h-3.5 shrink-0 text-emerald-400" />
                    <span>AI исправил {{ pendingFixedDefectsCount }} дефект(ов)</span>
                  </div>
                  <button class="px-2.5 py-1 text-[11px] font-bold rounded bg-emerald-400 text-[#090b10] hover:bg-emerald-300 transition-colors cursor-pointer" @click="applyPendingOptimization">
                    Применить в проект
                  </button>
                </div>

                <!-- Empty state when all defects resolved -->
                <div v-if="analysisReport.defects.length === 0" class="p-5 text-center bg-[#0d1612] rounded border border-sage-border/40 space-y-2">
                  <CheckCircle2 class="w-7 h-7 text-sage-text mx-auto" />
                  <p class="font-bold text-xs text-silver-white">Все дефекты устранены!</p>
                  <p class="text-[10px] text-silver-steel">AST и AI анализ подтверждают отсутствие неэффективных циклов O(n²) и критических проблем.</p>
                </div>

                <div 
                  v-for="defect in analysisReport.defects"
                  :key="defect.id"
                  class="defect-card bg-graphite-card rounded border p-2.5 text-xs cursor-pointer hover:border-white/20 transition-colors"
                  :class="[
                    defect.severity === 'HIGH' ? 'border-titanium-border' : 'border-white/[0.10]',
                    selectedDefect?.id === defect.id ? 'ring-1 ring-sky-400/50' : '',
                    isDefectResolvedInPending(defect) ? 'opacity-70 bg-sage-text/5 border-sage-border/40' : ''
                  ]"
                  @click="jumpToDefect(defect)"
                >
                  <div class="flex items-start justify-between gap-2">
                    <div class="flex items-start gap-1.5 min-w-0">
                      <CheckCircle2 v-if="isDefectResolvedInPending(defect)" class="w-3.5 h-3.5 text-sage-text shrink-0 mt-0.5" />
                      <AlertTriangle v-else-if="defect.severity === 'HIGH'" class="w-3.5 h-3.5 text-titanium-text shrink-0 mt-0.5" />
                      <Info v-else class="w-3.5 h-3.5 text-silver-steel shrink-0 mt-0.5" />
                      <p :class="isDefectResolvedInPending(defect) ? 'line-through text-silver-steel' : 'text-silver-platinum font-semibold'" class="break-words [overflow-wrap:anywhere]">{{ defect.title }}</p>
                    </div>
                    <div class="flex items-center gap-1 shrink-0">
                      <span v-if="isDefectResolvedInPending(defect)" class="text-[9px] font-mono px-1.5 py-0.5 rounded bg-sage-text/20 text-sage-text border border-sage-text/30">
                        Устраняется в Diff
                      </span>
                      <span v-else-if="defect.complexity" class="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#bf616a]/20 text-[#bf616a] border border-[#bf616a]/30">
                        {{ defect.complexity }}
                      </span>
                    </div>
                  </div>

                  <p class="text-silver-dark text-[10px] font-mono mt-1">
                    {{ defect.location.filePath }}:{{ defect.location.startLine }}
                  </p>

                  <div class="mt-2 pt-2 border-t border-white/[0.06] space-y-1 text-[11px]">
                    <p class="text-silver-steel"><strong class="text-silver-dark font-medium">Риск:</strong> Рост времени при увеличении объема входа</p>
                    <p class="text-silver-steel"><strong class="text-silver-dark font-medium">Рекомендация:</strong> {{ defect.description }}</p>
                  </div>

                  <div class="mt-2 flex items-center justify-between gap-2">
                    <button 
                      class="text-[10px] text-[#88c0d0] hover:underline flex items-center gap-1"
                      @click.stop="jumpToDefect(defect)"
                    >
                      <Search class="w-3 h-3" /> Найти на графе
                    </button>
                    <button 
                      class="px-2 py-0.5 text-[10px] rounded bg-[#a4c0ac]/20 hover:bg-[#a4c0ac]/30 text-[#a4c0ac] border border-[#a4c0ac]/30"
                      @click.stop="optimizeCode"
                    >
                      Исправить
                    </button>
                  </div>
                </div>

                <div
                  v-if="selectedDefect"
                  class="bg-[#0a0d12] rounded border border-sky-400/30 p-3 text-xs space-y-3 mt-3"
                >
                  <div class="flex items-start justify-between gap-2">
                    <div>
                      <p class="text-silver-platinum font-semibold">{{ selectedDefect.title }}</p>
                      <p class="text-silver-dark mt-1 font-mono text-[10px]">
                        {{ selectedDefect.location.filePath }}:
                        {{ selectedDefect.location.startLine }}–{{ selectedDefect.location.endLine }}
                      </p>
                    </div>
                    <button
                      class="text-silver-steel hover:text-white"
                      title="Закрыть подробности"
                      @click.stop="selectedDefect = null"
                    >
                      <X class="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <div>
                    <p class="text-silver-dark uppercase tracking-wider text-[10px] mb-1">Описание дефекта</p>
                    <p class="text-silver-brushed whitespace-pre-wrap text-[11px]">{{ selectedDefect.description }}</p>
                  </div>

                  <div v-if="selectedDefect.complexity">
                    <p class="text-silver-dark uppercase tracking-wider text-[10px] mb-1">Сложность</p>
                    <p class="font-mono text-titanium-text">{{ selectedDefect.complexity }}</p>
                  </div>

                  <div v-if="selectedDefect.codeFragment">
                    <p class="text-silver-dark uppercase tracking-wider text-[10px] mb-1">Фрагмент кода</p>
                    <pre class="max-h-40 overflow-auto rounded bg-black/30 p-2 text-[10px] leading-relaxed text-silver-brushed whitespace-pre-wrap">{{ selectedDefect.codeFragment }}</pre>
                  </div>

                  <button
                    class="w-full jb-btn-secondary justify-center text-xs"
                    @click.stop="jumpToDefect(selectedDefect)"
                  >
                    Перейти к строке {{ selectedDefect.location.startLine }}
                  </button>
                </div>
              </div>

              <!-- Subtab 2: Recommendations -->
              <div v-else-if="activeAnalysisTab === 'recommendations'" class="space-y-2">
                <div 
                  v-for="(rec, idx) in analysisReport.recommendations"
                  :key="idx"
                  class="bg-graphite-card rounded border border-white/[0.10] p-2.5 text-xs space-y-1.5"
                >
                  <p class="text-silver-platinum font-semibold">{{ rec.title }}</p>
                  <p class="text-silver-steel text-[11px] leading-relaxed">{{ rec.description }}</p>
                  <div class="pt-1.5 flex items-center justify-between border-t border-white/[0.06] text-[10px]">
                    <span class="text-[#a3be8c] font-medium">Эффект: {{ rec.expectedBenefit }}</span>
                    <button class="px-2 py-0.5 rounded bg-white/10 hover:bg-white/20 text-white" @click="optimizeCode">Применить</button>
                  </div>
                </div>
                <div v-if="analysisReport.recommendations.length === 0" class="text-center py-4 text-xs text-silver-dark">
                  Нет активных рекомендаций
                </div>
              </div>

              <!-- Subtab 3: Metrics -->
              <div v-else-if="activeAnalysisTab === 'metrics'" class="space-y-3">
                <div class="bg-graphite-card rounded border border-white/[0.10] p-3">
                  <h4 class="text-[10px] uppercase tracking-wider text-silver-dark font-bold mb-2">Метрики кода</h4>
                  <div class="space-y-1.5 text-xs font-mono">
                    <div class="flex justify-between">
                      <span class="text-silver-steel">Всего строк:</span>
                      <span class="text-silver-platinum font-semibold">{{ analysisReport.metrics.totalLines }}</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-silver-steel">Всего методов:</span>
                      <span class="text-silver-platinum font-semibold">{{ analysisReport.metrics.totalMethods }}</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-silver-steel">Средняя сложность:</span>
                      <span class="text-silver-platinum font-semibold">{{ analysisReport.metrics.averageComplexity.toFixed(1) }}</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-silver-steel">Макс. сложность:</span>
                      <span class="text-[#bf616a] font-semibold">{{ analysisReport.metrics.maxComplexity }}</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-silver-steel">Дубликаты блоков:</span>
                      <span class="text-silver-platinum font-semibold">{{ analysisReport.metrics.duplicatedBlocks }}</span>
                    </div>
                  </div>
                </div>

                <div class="bg-graphite-card rounded border border-white/[0.10] p-3 text-[11px] space-y-1.5 text-silver-steel">
                  <div class="text-[10px] uppercase font-bold text-silver-dark mb-1">Java Scope</div>
                  <div class="flex items-center gap-1.5 text-emerald-400">✓ java.lang & Управление потоком (Java 21)</div>
                  <div class="flex items-center gap-1.5 text-emerald-400">✓ java.util (Collections, Map, Stream)</div>
                  <div class="flex items-center gap-1.5 text-emerald-400">✓ Консольный ввод/вывод & Исключения</div>
                  <div class="flex items-center gap-1.5 text-emerald-400">✓ Детерминированный Quality Gate</div>
                </div>
              </div>
            </div>
            <div v-else class="p-4 text-center text-xs text-silver-dark">
              <Gauge class="w-8 h-8 mx-auto mb-2 opacity-40" />
              <p>Анализ недоступен</p>
            </div>
          </div>
          <!-- Block Palette -->
          <div v-if="leftTab === 'palette'" class="flex-1 overflow-y-auto p-2 space-y-2">
            <p class="px-1 text-[10px] text-silver-dark">Добавить конструкцию в выбранный метод</p>
            <button
              v-for="block in paletteBlocks"
              :key="block.label"
              class="w-full rounded border border-white/[0.10] bg-graphite-card p-2 text-left hover:border-white/30 transition-colors"
              :disabled="isProcessing || !projectLoaded"
              @click="addPaletteBlock(block.code)"
            >
              <span class="block text-xs font-semibold text-silver-platinum">{{ block.label }}</span>
              <code class="mt-1 block truncate text-[10px] text-silver-steel">{{ block.code }}</code>
            </button>
          </div>
        </aside>
      </Transition>

      <!-- ===== 2.3 CENTRAL SPLIT WORKSPACE ===== -->
      <main ref="workspaceElement" class="workspace-main flex-1 min-w-0 min-h-0 flex flex-col overflow-hidden">
        
        <!-- Top: Graph Canvas -->
        <section class="graph-panel flex-1 min-h-0 flex flex-col border-b border-white/[0.08] overflow-hidden bg-[#040507]">
          <!-- Graph Toolbar -->
          <div class="graph-toolbar">
            <div class="flex items-center gap-2 text-xs shrink-0">
              <Workflow class="w-4 h-4 text-[#a4c0ac]" />
              <span class="font-mono">Control Flow Graph</span>
              <span class="text-silver-steel">· {{ graphNodes.length }} / {{ rawGraphData.nodes.length || graphNodes.length }} узлов</span>
            </div>
            <div class="graph-actions flex items-center gap-1.5">
              <button class="jb-btn-secondary" :disabled="!projectLoaded" @click="openMainFile" :title="currentMainFile || 'Активный файл'">{{ currentMainFile ? currentMainFile.split('/').pop() : 'Файл' }}</button>
              <button class="jb-icon-btn" :disabled="isProcessing || !projectLoaded" @click="runOptimization" title="Запуск" aria-label="Запуск"><Play class="w-4 h-4" /></button>
              
              <!-- Zoom & Fit Controls -->
              <div class="flex items-center gap-0.5 bg-black/40 p-0.5 rounded border border-white/10">
                <button class="jb-icon-btn !w-6 !h-6 text-silver-steel hover:text-white" :disabled="!graphNodes.length" @click="zoomIn({ duration: 200 })" title="Приблизить граф (+)">
                  <span class="font-bold text-xs leading-none">+</span>
                </button>
                <button class="jb-icon-btn !w-6 !h-6 text-silver-steel hover:text-white" :disabled="!graphNodes.length" @click="zoomOut({ duration: 200 })" title="Отдалить граф (−)">
                  <span class="font-bold text-xs leading-none">−</span>
                </button>
                <button class="jb-icon-btn !w-6 !h-6 text-silver-steel hover:text-white" :disabled="!projectLoaded || !graphNodes.length" @click="focusGraph" title="По размеру графа (Fit View)">
                  <Maximize2 class="w-3.5 h-3.5" />
                </button>
                <button 
                  class="jb-icon-btn !w-6 !h-6 transition-colors" 
                  :class="showMiniMap ? 'text-[#a4c0ac] bg-white/10' : 'text-silver-steel hover:text-white'" 
                  :disabled="!graphNodes.length" 
                  @click="showMiniMap = !showMiniMap" 
                  title="Показать / скрыть миникарту навигатора"
                >
                  <MapIcon class="w-3.5 h-3.5" />
                </button>
              </div>

              <!-- Heatmap Toggle Button -->
              <button 
                class="px-2 py-1 rounded text-[10px] font-mono transition-all flex items-center gap-1 cursor-pointer select-none"
                :class="heatmapMode ? 'bg-gradient-to-r from-red-600/30 via-orange-600/30 to-amber-600/30 border border-orange-500/60 text-white shadow-[0_0_12px_rgba(249,115,22,0.3)]' : 'bg-black/40 border border-white/10 text-silver-steel hover:text-white hover:bg-white/10'"
                :disabled="!graphNodes.length"
                @click="heatmapMode = !heatmapMode" 
                title="Тепловая карта сложности узлов (Heatmap)"
              >
                <Flame class="w-3 h-3" :class="heatmapMode ? 'text-orange-400 animate-pulse' : 'text-silver-steel'" />
                <span class="font-semibold">Heatmap</span>
              </button>

              <!-- Graph Size Presets & Maximize -->
              <div class="flex items-center gap-0.5 bg-black/40 p-0.5 rounded border border-white/10 text-[10px] font-mono">
                <button 
                  class="px-1.5 py-0.5 rounded transition-colors"
                  :class="activeGraphPreset === 50 && !isGraphMaximized ? 'bg-[#a4c0ac] text-[#090b10] font-semibold' : 'hover:bg-white/10 text-silver-steel hover:text-white'"
                  @click="setGraphHeightPreset(50)" 
                  title="Граф 50% экрана"
                >
                  50%
                </button>
                <button 
                  class="px-1.5 py-0.5 rounded transition-colors"
                  :class="activeGraphPreset === 75 && !isGraphMaximized ? 'bg-[#a4c0ac] text-[#090b10] font-semibold' : 'hover:bg-white/10 text-silver-steel hover:text-white'"
                  @click="setGraphHeightPreset(75)" 
                  title="Граф 75% экрана"
                >
                  75%
                </button>
                <button 
                  class="px-1.5 py-0.5 rounded transition-colors font-semibold"
                  :class="isGraphMaximized ? 'bg-[#a4c0ac] text-[#090b10]' : 'hover:bg-white/10 text-silver-steel hover:text-white'"
                  @click="toggleMaximizeGraph" 
                  :title="isGraphMaximized ? 'Восстановить редактор' : 'Развернуть поле графа на весь экран (100%)'"
                >
                  {{ isGraphMaximized ? 'Восстановить' : '100%' }}
                </button>
              </div>

              <button class="jb-icon-btn" @click="searchVisible = !searchVisible" title="Поиск" aria-label="Поиск" :aria-expanded="searchVisible"><Search class="w-4 h-4" /></button>
            </div>
          </div>
          <div v-if="searchVisible" class="search-panel p-3 border-b border-white/10">
            <input v-model="searchQuery" aria-label="Поиск по узлам" placeholder="Поиск по коду и названиям узлов…" class="w-full rounded bg-white/5 px-3 py-2 text-xs outline-none focus:ring-1 focus:ring-white/30" />
            <div v-if="searchQuery.trim()" class="max-h-32 overflow-auto mt-2">
              <button v-for="node in searchResults" :key="node.id" class="block w-full text-left rounded px-2 py-1 text-xs hover:bg-white/10" @click="handleNodeClick({ node })">{{ node.data.label }} · {{ node.data.filePath }}:{{ node.data.line }}</button>
              <p v-if="!searchResults.length" class="text-xs text-silver-steel">Ничего не найдено</p>
            </div>
          </div>

          <!-- Unreal Engine Blueprint Style Method Tabs Bar -->
          <div v-if="projectLoaded && methodList.length" class="method-tabs-bar">
            <button
              class="method-tab"
              :class="{ 'active': activeMethodTab === '__overview__' }"
              @click="switchMethodTab('__overview__')"
              title="Обзор всех методов класса"
            >
              <Boxes class="w-3.5 h-3.5" />
              <span>Обзор класса ({{ methodList.length }})</span>
            </button>
            <div
              v-for="mId in openMethodTabs.filter(id => id !== '__overview__')"
              :key="mId"
              class="method-tab"
              :class="{ 'active': activeMethodTab === mId }"
              @click="switchMethodTab(mId)"
            >
              <Workflow class="w-3.5 h-3.5 text-[#a4c0ac]" />
              <span class="truncate max-w-[150px]">{{ getMethodDisplayName(mId) }}</span>
              <button
                class="method-tab-close"
                @click.stop="closeMethodTab(mId)"
                title="Закрыть вкладку метода"
              >
                <X class="w-3 h-3" />
              </button>
            </div>
          </div>

          <!-- Vue Flow Canvas -->
          <div class="flex-1 min-h-0 relative">
            <VueFlow
              v-if="graphNodes.length > 0"
              v-model:nodes="graphNodes"
              v-model:edges="graphEdges"
              :default-zoom="0.8"
              :min-zoom="0.1"
              :max-zoom="4"
              :fit-view-on-init="true"
              :zoom-on-scroll="true"
              :pan-on-scroll="false"
              :zoom-on-double-click="false"
              @node-click="handleNodeClick"
              @connect="handleConnect"
              class="bg-graphite-dots"
            >
              <template #node-custom="props">
                <ChromeNode
                  v-bind="props"
                  :heatmap-mode="heatmapMode"
                  @node-click="handleNodeClick"
                  @node-dblclick="handleNodeDblClick"
                  @toggle-expand="toggleMethodExpand"
                  @open-tab="openMethodInTab"
                />
              </template>
              <Background pattern-color="rgba(255, 255, 255, 0.05)" :gap="16" />
              <MiniMap 
                v-if="showMiniMap"
                :key="`minimap-${activeMethodTab}`"
                :node-color="currentMiniMapColorFunc"
                :node-stroke-color="currentMiniMapStrokeFunc"
                :node-stroke-width="1.5"
                :node-border-radius="3"
                :mask-color="'rgba(5, 6, 8, 0.65)'"
                :mask-stroke-color="'rgba(56, 189, 248, 0.7)'"
                :mask-stroke-width="1.5"
                :pannable="true"
                :zoomable="true"
                @node-click="handleMiniMapNodeClick"
                @node-dblclick="handleMiniMapNodeDblClick"
                @click="handleMiniMapClick"
                class="jb-minimap"
              />
            </VueFlow>

            <!-- Heatmap Legend Overlay -->
            <div 
              v-if="heatmapMode && graphNodes.length" 
              class="absolute bottom-3 left-3 z-10 flex items-center gap-2.5 bg-[#090c12]/92 backdrop-blur border border-white/15 px-3 py-1.5 rounded-lg text-[10px] font-mono shadow-2xl pointer-events-none select-none"
            >
              <span class="text-silver-dark uppercase font-bold text-[9px]">Тепловая карта:</span>
              <div class="flex items-center gap-1">
                <span class="w-2 h-2 rounded-full bg-red-500 shadow-[0_0_6px_#ef4444]"></span>
                <span class="text-red-400 font-semibold">O(n³)</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="w-2 h-2 rounded-full bg-orange-500 shadow-[0_0_6px_#f97316]"></span>
                <span class="text-orange-400 font-semibold">O(n²)</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="w-2 h-2 rounded-full bg-yellow-500 shadow-[0_0_6px_#eab308]"></span>
                <span class="text-yellow-400 font-semibold">O(n)</span>
              </div>
              <div class="flex items-center gap-1">
                <span class="w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_6px_#10b981]"></span>
                <span class="text-emerald-400 font-semibold">O(1)</span>
              </div>
            </div>
            <div v-else-if="!graphNodes.length" class="flex items-center justify-center h-full text-silver-dark">
              <div class="text-center">
                <Workflow class="w-12 h-12 mx-auto mb-3 opacity-20" />
                <p class="text-sm">Граф будет построен после импорта</p>
              </div>
            </div>
          </div>
        </section>

        <!-- Bottom: Code Editor & Tabs -->
          <section
            v-if="bottomPanelVisible"
            class="bottom-panel flex flex-col bg-[#050608] overflow-hidden shrink-0" :style="{ height: `${bottomPanelHeight}px` }"
          >
          <div 
            class="panel-resizer hover:bg-white/15 transition-all flex items-center justify-center cursor-row-resize select-none group" 
            role="separator" 
            tabindex="0" 
            aria-label="Высота нижней панели" 
            aria-orientation="horizontal" 
            :aria-valuenow="bottomPanelHeight" 
            :aria-valuemin="40" 
            :aria-valuemax="maxPanelHeight" 
            @pointerdown="startPanelResize" 
            @dblclick="toggleMaximizeGraph"
            @keydown.up.prevent="setPanelHeight(bottomPanelHeight + 20)" 
            @keydown.down.prevent="setPanelHeight(bottomPanelHeight - 20)" 
            @keydown.home.prevent="setPanelHeight(40)" 
            @keydown.end.prevent="setPanelHeight(maxPanelHeight)"
            title="Перетащите для изменения высоты или дважды кликните для максимизации поля графа"
          >
            <div class="w-12 h-1 bg-white/25 group-hover:bg-[#a4c0ac] rounded-full transition-colors"></div>
          </div>
          <!-- Editor Tabs -->
          <div class="h-8 border-b border-white/[0.08] bg-[#0c0e14] flex items-center overflow-x-auto shrink-0">
            <button 
              v-for="tab in bottomTabs"
              :key="tab.id"
              @click="activeBottomTab = tab.id"
              class="jb-tab"
              :class="{ active: activeBottomTab === tab.id }"
            >
              <component :is="tab.icon" class="w-3.5 h-3.5" />
              <span>{{ tab.label }}</span>
            </button>
              <button
                class="ml-auto jb-tab"
                title="Скрыть нижнюю панель"
                @click="bottomPanelVisible = false"
              >
                <Minimize2 class="w-3.5 h-3.5" />
                <span>Скрыть</span>
              </button>
          </div>

          <!-- Editor Content -->
          <div class="flex-1 overflow-hidden">
            <!-- Code Editor -->
              <div v-show="activeBottomTab === 'code'" class="h-full overflow-hidden bg-[#050608] flex flex-col">
                  <div v-if="selectedGraphNode" class="node-inspection-banner shrink-0 mx-4 mt-3 border-b border-white/[0.08] pb-3">
                    <div class="flex items-start justify-between gap-3">
                      <div class="min-w-0">
                        <p class="break-words text-xs font-semibold text-silver-platinum">{{ selectedGraphNode.label }}</p>
                        <p class="mt-1 text-[10px] font-mono text-silver-dark">
                          {{ selectedGraphNode.type || 'STATEMENT' }} › {{ selectedGraphNode.filePath || currentFile }}<span v-if="selectedGraphNode.line">:{{ selectedGraphNode.line }}</span>
                        </p>
                      </div>
                      <div class="flex items-center gap-1.5 shrink-0">
                        <button 
                          class="px-2 py-0.5 text-[10px] font-semibold rounded bg-[#bf616a]/20 hover:bg-[#bf616a]/40 text-[#bf616a] border border-[#bf616a]/30 transition-colors flex items-center gap-1"
                          @click="deleteSelectedNode"
                          title="Удалить блок из кода (Del)"
                          :disabled="isProcessing"
                        >
                          <X class="w-3 h-3" />
                          <span>Удалить блок</span>
                        </button>
                        <button class="shrink-0 text-[10px] text-silver-steel hover:text-white px-1.5 py-0.5" @click="showFullFile">Показать файл</button>
                      </div>
                    </div>
                    
                    <div class="mt-2 space-y-1.5">
                      <div class="flex items-center justify-between text-[10px] text-silver-dark font-mono">
                        <span>Java AST блок (редактируемый):</span>
                        <span class="text-silver-steel text-[9px]">Ctrl+Enter — применить</span>
                      </div>
                      <textarea 
                        v-model="editedNodeCode"
                        @keydown.ctrl.enter.prevent="updateSelectedNode"
                        rows="2"
                        class="w-full text-[11px] font-mono leading-relaxed bg-[#0a0c13] text-[#e5e9f0] border border-white/[0.12] rounded p-2 focus:outline-none focus:border-[#88c0d0] resize-y"
                        placeholder="Код Java узла..."
                      ></textarea>
                      <div class="flex justify-end gap-2">
                        <button 
                          class="px-2.5 py-1 text-[10px] font-semibold rounded bg-[#88c0d0]/20 hover:bg-[#88c0d0]/30 text-[#88c0d0] border border-[#88c0d0]/30 transition-colors flex items-center gap-1"
                          @click="updateSelectedNode"
                          :disabled="isProcessing"
                        >
                          <Check class="w-3 h-3" />
                          <span>Применить к Java-коду</span>
                        </button>
                      </div>
                    </div>
                  </div>
                <div ref="editorElement" class="min-h-0 flex-1" aria-label="Редактор Java-кода"></div>
              </div>

            <!-- Optimization & Decomposition Panel -->
            <div v-show="activeBottomTab === 'optimization'" class="h-full overflow-auto bg-[#050608] p-4 font-mono text-xs">
              <div class="flex flex-wrap items-center justify-between gap-3 mb-4 pb-3 border-b border-white/[0.08]">
                <div class="flex items-center gap-2">
                  <Zap class="w-4 h-4 text-titanium-text" />
                  <span class="text-sm font-semibold text-silver-white">Оптимизация O(n²) и Декомпозиция монолита</span>
                </div>
                <div class="flex items-center gap-2">
                  <button 
                    class="jb-btn-primary flex items-center gap-1.5 px-3 py-1 rounded-[4px] bg-gradient-to-b from-white via-silver-brushed to-silver-steel text-[#090b10] font-semibold text-xs disabled:opacity-50 transition-opacity"
                    :disabled="isProcessing || !projectLoaded"
                    @click="optimizeCodeWithAi"
                  >
                    <Sparkles class="w-3.5 h-3.5 fill-current" />
                    <span>{{ isProcessing ? 'Оптимизация с AI...' : 'Оптимизировать с AI' }}</span>
                  </button>
                  <button 
                    class="jb-btn-secondary flex items-center gap-1.5 px-2 py-1 text-xs"
                    :disabled="isProcessing || !projectLoaded"
                    @click="optimizeCode"
                    title="Применить шаблонные правила AST"
                  >
                    <Zap class="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              <div class="mb-3 p-2.5 rounded border border-white/10 bg-[#0e1118] text-xs text-silver-steel flex items-start gap-2.5">
                <Sparkles class="w-4 h-4 text-[#a4c0ac] shrink-0 mt-0.5" />
                <div class="space-y-0.5">
                  <p class="text-silver-white font-medium text-[11px]">AI-оптимизация работает в 1 клик</p>
                  <p class="text-[10px] leading-relaxed text-silver-steel">
                    Gemini автоматически улучшает весь код (O(n²) → O(n), StringBuilder, Java 21). Выбирать пункты вручную не требуется.
                  </p>
                </div>
              </div>

              <div class="optimization-cards mb-4" v-if="optimizationCandidates.length">
                <div class="mb-2 flex items-center justify-between gap-2">
                  <span class="text-[10px] uppercase tracking-wider text-silver-dark">Локальные AST-правила (без AI)</span>
                  <div class="flex items-center gap-2">
                    <button 
                      type="button"
                      class="text-[10px] font-mono text-[#a4c0ac] hover:underline cursor-pointer transition-colors"
                      @click="toggleSelectAllOptimizations"
                    >
                      {{ isAllOptimizationsSelected ? 'Снять все' : 'Выбрать все' }}
                    </button>
                    <span class="text-[10px] text-silver-steel">({{ selectedOptimizationCount }}/{{ optimizationCandidates.length }})</span>
                  </div>
                </div>
                <label
                  v-for="candidate in optimizationCandidates"
                  :key="candidate.id"
                  class="optimization-card"
                  :class="{ 'optimization-card-active': selectedOptimizationIds.has(candidate.id) }"
                >
                  <input
                    type="checkbox"
                    :checked="selectedOptimizationIds.has(candidate.id)"
                    class="mt-0.5 accent-white"
                    @change="toggleOptimization(candidate.id)"
                  />
                  <div class="min-w-0 flex-1">
                    <div class="flex flex-wrap items-center gap-2">
                      <span class="font-semibold text-silver-platinum break-words">{{ candidate.title }}</span>
                      <span class="optimization-complexity">{{ candidate.complexity }}</span>
                    </div>
                    <p class="mt-1 text-[11px] text-silver-steel break-words [overflow-wrap:anywhere]">{{ candidate.description }}</p>
                    <p class="mt-1 text-[10px] text-silver-dark break-all">{{ candidate.location }}</p>
                  </div>
                </label>
              </div>

              <div v-if="allCriticalResolved" class="mb-3 rounded border border-sage-border bg-sage-tint p-3 text-sage-text">
                Все критические дефекты O(n²) устранены
              </div>

              <div v-if="verificationResult" class="mb-4 flex flex-wrap items-center gap-3 rounded border p-3" :class="verificationResult.behaviorPreserved ? 'border-sage-border bg-sage-tint' : 'border-ash-border bg-ash-tint'">
                <span class="font-semibold" :class="verificationResult.behaviorPreserved ? 'text-sage-text' : 'text-ash-text'">{{ verificationResult.behaviorPreserved ? '100% Сохранение поведения подтверждено ✓' : 'Сохранение поведения не подтверждено' }}</span>
                <span class="text-silver-steel">До: {{ verificationResult.originalExecutionTime }} ms › После: {{ verificationResult.optimizedExecutionTime }} ms</span>
                <span class="font-semibold text-silver-platinum">Прирост: {{ verificationResult.speedupFactor.toFixed(2) }}x</span>
              </div>

              <div v-if="optimizationError" class="p-4 rounded border border-[rgba(181,133,133,0.35)] bg-[#14100e] text-[#b58585] space-y-2">
                <div class="flex items-center gap-2 font-semibold text-xs">
                  <AlertTriangle class="w-4 h-4 shrink-0 text-[#b58585]" />
                  <span>Ошибка оптимизации</span>
                </div>
                <pre class="text-[11px] font-mono whitespace-pre-wrap text-silver-brushed bg-black/30 p-2.5 rounded border border-white/5 max-h-40 overflow-auto">{{ optimizationError }}</pre>
              </div>

              <div v-else-if="pendingOptimizationResult" class="p-4 rounded border border-emerald-500/30 bg-emerald-950/20 flex items-center justify-between gap-3">
                <div class="flex items-center gap-2 min-w-0">
                  <Sparkles class="w-4 h-4 text-[#a4c0ac] shrink-0" />
                  <div class="min-w-0">
                    <p class="text-xs font-semibold text-silver-white truncate">AI подготовил код (изменений: {{ pendingOptimizationResult.changes.length }})</p>
                    <p class="text-[10px] text-silver-steel">Перейдите во вкладку Diff для сравнения до и после.</p>
                  </div>
                </div>
                <button 
                  class="px-3 py-1.5 rounded text-xs font-semibold bg-[#a4c0ac] text-[#090b10] hover:bg-[#b8d4c0] flex items-center gap-1.5 cursor-pointer shrink-0 shadow"
                  @click="applyPendingOptimization"
                >
                  <Check class="w-3.5 h-3.5" />
                  <span>Обновить код</span>
                </button>
              </div>

              <div v-else-if="!optimizationChanges" class="p-6 text-center text-silver-dark space-y-2">
                <Zap class="w-8 h-8 mx-auto opacity-30 text-silver-steel" />
                <p class="text-silver-steel">Оптимизация ещё не запускалась</p>
                <p class="text-xs text-silver-dark max-w-md mx-auto">
                  Нажмите кнопку «Оптимизировать с AI» в верхней панели. После генерации откроется Diff для безопасного предпросмотра.
                </p>
              </div>

              <div v-else-if="optimizationChanges.length === 0" class="p-4 rounded border border-white/10 bg-[#0a0c12] text-silver-steel">
                Безопасных семантических мест для оптимизации в текущем файле не найдено.
              </div>

              <div v-else class="space-y-3">
                <div class="text-xs font-semibold text-silver-platinum uppercase tracking-wider">
                  Применено изменений: {{ optimizationChanges.length }}
                </div>

                <div 
                  v-for="(change, idx) in optimizationChanges" 
                  :key="idx"
                  class="rounded border border-white/[0.08] bg-[#0c0e15] p-3 space-y-1.5"
                >
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-silver-white font-semibold text-xs flex items-center gap-1.5">
                      <CheckCircle2 class="w-3.5 h-3.5 text-sage-text" />
                      {{ change.type }}
                    </span>
                    <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-[#141d17] border border-sage-border text-sage-text">
                      {{ change.complexityBefore }} &rarr; {{ change.complexityAfter }}
                    </span>
                  </div>
                  <p class="text-silver-brushed text-xs">{{ change.description }}</p>
                  <div class="text-[10px] text-silver-dark">
                    {{ change.location.filePath }}:{{ change.location.startLine }}–{{ change.location.endLine }}
                  </div>
                </div>
              </div>
            </div>
            <!-- Diff viewer -->
            <div v-show="activeBottomTab === 'diff'" class="h-full overflow-auto bg-[#050608] p-4 font-mono text-xs">
              <div class="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-white/[0.08] pb-3">
                <div>
                  <p class="text-sm font-semibold text-silver-white">
                    {{ diffViewMode === 'unified' ? 'Unified Diff (в одном файле)' : 'Side-by-side diff (2 параллельные колонки)' }}
                  </p>
                  <p class="text-[10px] text-silver-dark">
                    {{ pendingOptimizationResult ? 'Сравнение текущего кода с предложенным AI' : 'Оригинал и текущая версия' }}
                  </p>
                </div>
                <div class="flex items-center gap-2">
                  <div class="flex items-center gap-1 bg-[#090b10] p-0.5 rounded border border-white/10">
                    <button
                      type="button"
                      class="px-2.5 py-1 rounded text-xs transition-colors cursor-pointer"
                      :class="diffViewMode === 'unified' ? 'bg-white/15 text-silver-white font-semibold' : 'text-silver-dark hover:text-silver-steel'"
                      @click="diffViewMode = 'unified'"
                      title="Один файл со всеми изменениями (Unified Diff)"
                    >
                      В одном файле
                    </button>
                    <button
                      type="button"
                      class="px-2.5 py-1 rounded text-xs transition-colors cursor-pointer"
                      :class="diffViewMode === 'split' ? 'bg-white/15 text-silver-white font-semibold' : 'text-silver-dark hover:text-silver-steel'"
                      @click="diffViewMode = 'split'"
                      title="Две параллельные колонки (Side-by-side)"
                    >
                      2 колонки
                    </button>
                  </div>
                  <select v-model="diffFile" class="rounded border border-white/10 bg-[#0c0e15] px-2 py-1 text-[11px] text-silver-brushed">
                    <option v-for="file in diffFiles" :key="file" :value="file">{{ file }}</option>
                  </select>
                </div>
              </div>

              <!-- Pending Review Action Bar -->
              <div v-if="pendingOptimizationResult" class="mb-4 flex flex-wrap items-center justify-between gap-3 p-3 rounded border border-emerald-500/30 bg-emerald-950/20">
                <div class="flex items-center gap-2.5">
                  <Sparkles class="w-4 h-4 text-[#a4c0ac] shrink-0" />
                  <div>
                    <p class="text-xs font-semibold text-silver-white">Gemini подготовил оптимизацию (изменений: {{ pendingOptimizationResult.changes.length }})</p>
                    <p class="text-[10px] text-silver-steel">
                      {{ pendingOptimizationResult.verification?.compilationSuccess ? 'JDK 21 скомпилирован ✓' : 'Ошибка компиляции' }}
                      <span v-if="pendingOptimizationResult.verification">
                        · Поведение: {{ pendingOptimizationResult.verification.behaviorPreserved ? 'сохранено ✓' : 'не сохранено' }}
                        · Прирост: {{ pendingOptimizationResult.verification.speedupFactor.toFixed(2) }}x
                      </span>
                    </p>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <button 
                    class="px-3 py-1.5 rounded text-xs font-semibold bg-[#a4c0ac] text-[#090b10] hover:bg-[#b8d4c0] transition-colors flex items-center gap-1.5 cursor-pointer shadow"
                    @click="applyPendingOptimization"
                    title="Записать новый код в проект и обновить AST-граф"
                  >
                    <Check class="w-3.5 h-3.5" />
                    <span>Обновить код в проекте</span>
                  </button>
                  <button 
                    class="px-3 py-1.5 rounded text-xs font-semibold border border-white/15 bg-white/5 text-silver-brushed hover:bg-white/10 transition-colors flex items-center gap-1.5 cursor-pointer"
                    @click="discardPendingOptimization"
                    title="Отклонить изменения"
                  >
                    <X class="w-3.5 h-3.5" />
                    <span>Отклонить</span>
                  </button>
                </div>
              </div>

              <!-- Unified View (в одном файле, как в Antigravity) -->
              <div v-if="diffFile && diffViewMode === 'unified'" class="diff-unified-pane">
                <div class="diff-unified-heading">
                  <div class="flex items-center gap-2">
                    <span class="text-silver-white font-semibold flex items-center gap-1.5">
                      <FileCode2 class="w-3.5 h-3.5 text-[#a4c0ac]" />
                      <span>{{ diffFile }}</span>
                    </span>
                    <span class="text-[10px] text-silver-dark font-normal">Единый файл изменений</span>
                  </div>
                  <div class="flex items-center gap-3 text-xs font-mono">
                    <span class="text-emerald-400 font-semibold" title="Добавлено строк">+{{ diffStats.additions }}</span>
                    <span class="text-red-400 font-semibold" title="Удалено строк">-{{ diffStats.deletions }}</span>
                    <span class="text-silver-steel font-normal">Всего: {{ unifiedDiffLines.length }} строк</span>
                  </div>
                </div>
                <pre class="m-0 p-0 overflow-x-auto"><div 
                  v-for="(line, idx) in unifiedDiffLines" 
                  :key="'unified-' + idx"
                  class="diff-unified-line"
                  :class="{
                    'diff-unified-removed': line.type === 'remove',
                    'diff-unified-added': line.type === 'add'
                  }"
                ><span class="diff-unified-gutter-old">{{ line.oldNum ?? '' }}</span><span class="diff-unified-gutter-new">{{ line.newNum ?? '' }}</span><span class="diff-unified-marker">{{ line.type === 'remove' ? '-' : (line.type === 'add' ? '+' : ' ') }}</span><span class="diff-unified-content">{{ line.text }}</span></div></pre>
              </div>

              <!-- Split View (2 параллельные колонки) -->
              <div v-else-if="diffFile && diffViewMode === 'split'" class="grid min-w-[680px] grid-cols-2 gap-2">
                <div class="diff-pane">
                  <div class="diff-heading">
                    <span class="text-red-400 font-semibold flex items-center gap-1.5">
                      <span class="w-2 h-2 rounded-full bg-red-500 inline-block"></span>
                      {{ pendingOptimizationResult ? 'Текущий код (До оптимизации)' : 'Оригинал (Original)' }}
                    </span>
                    <span class="text-[10px] text-silver-dark font-normal">Строк: {{ diffLeftLinesCount }}</span>
                  </div>
                  <pre><div v-for="(row, idx) in alignedDiffRows" :key="'left-' + idx" class="diff-line" :class="{ 'diff-removed': row.left.type === 'removed', 'diff-empty': row.left.type === 'empty' }"><span class="diff-num">{{ row.left.lineNum ?? '' }}</span><span class="diff-marker">{{ row.left.type === 'removed' ? '-' : ' ' }}</span><span class="diff-text">{{ row.left.text }}</span></div></pre>
                </div>
                <div class="diff-pane">
                  <div class="diff-heading">
                    <span class="text-green-400 font-semibold flex items-center gap-1.5">
                      <span class="w-2 h-2 rounded-full bg-green-500 inline-block"></span>
                      {{ pendingOptimizationResult ? 'Новый код AI (После оптимизации)' : 'Оптимизированный (Refactored)' }}
                    </span>
                    <span class="text-[10px] text-silver-dark font-normal">Строк: {{ diffRightLinesCount }}</span>
                  </div>
                  <pre><div v-for="(row, idx) in alignedDiffRows" :key="'right-' + idx" class="diff-line" :class="{ 'diff-added': row.right.type === 'added', 'diff-empty': row.right.type === 'empty' }"><span class="diff-num">{{ row.right.lineNum ?? '' }}</span><span class="diff-marker">{{ row.right.type === 'added' ? '+' : ' ' }}</span><span class="diff-text">{{ row.right.text }}</span></div></pre>
                </div>
              </div>
              <div v-else class="p-8 text-center text-silver-dark">Запустите оптимизацию, чтобы сравнить версии.</div>
            </div>

            <!-- Console -->
            <div v-show="activeBottomTab === 'console'" class="h-full overflow-auto bg-[#08090d] p-3">
              <div 
                v-for="(log, idx) in consoleLogs"
                :key="idx"
                class="font-mono text-xs leading-relaxed mb-1"
                :class="log.color"
              >
                {{ log.text }}
              </div>
            </div>

            <!-- Tests / Quality Gate -->
            <div v-show="activeBottomTab === 'tests'" class="h-full overflow-auto bg-[#050608] p-4 font-mono text-xs">
              <!-- Top bar with action button & status -->
              <div class="flex items-center justify-between gap-3 mb-4 pb-3 border-b border-white/[0.08]">
                <div class="flex items-center gap-2">
                  <ShieldCheck class="w-4 h-4 text-sage-text" />
                  <span class="text-sm font-semibold text-silver-white">Quality Gate &amp; Поведенческая верификация</span>
                </div>
                <div class="flex items-center gap-2">
                  <button 
                    class="jb-btn-primary flex items-center gap-1.5 px-3 py-1 rounded-[4px] bg-gradient-to-b from-white via-silver-brushed to-silver-steel text-[#090b10] font-semibold text-xs disabled:opacity-50 transition-opacity"
                    :disabled="isProcessing || !projectLoaded"
                    @click="runQualityGateVerification"
                  >
                    <Play class="w-3.5 h-3.5 fill-current" />
                    <span>{{ isProcessing ? 'Тестирование...' : 'Запустить Quality Gate' }}</span>
                  </button>
                </div>
              </div>

              <div class="mb-4 rounded border border-white/[0.08] bg-[#0c0e15] p-3">
                <label for="custom-stdin" class="mb-2 block text-[10px] uppercase tracking-wider text-silver-dark">Пользовательские входные данные (stdin)</label>
                <div class="flex gap-2">
                  <textarea id="custom-stdin" v-model="customInput" rows="2" class="min-w-0 flex-1 resize-y rounded bg-black/30 p-2 text-xs text-silver-brushed outline-none focus:ring-1 focus:ring-white/30" placeholder="Например: 42"></textarea>
                  <button class="jb-btn-secondary self-start" :disabled="isProcessing || !projectLoaded" @click="runCustomQualityTest">Запустить тест</button>
                </div>
              </div>

              <!-- Content states -->
              <div v-if="!verificationResult && !isProcessing" class="p-8 text-center text-silver-dark space-y-2">
                <CheckCircle2 class="w-10 h-10 mx-auto opacity-30 text-silver-steel" />
                <p class="text-silver-steel text-sm">Проверка поведения ещё не выполнялась</p>
                <p class="text-xs text-silver-dark max-w-md mx-auto">
                  Нажмите «Запустить Quality Gate», чтобы скомпилировать код в изолированной среде, подать тестовые сценарии и доказать сохранение поведения (дифференциальное тестирование stdout).
                </p>
              </div>

              <div v-else-if="isProcessing && !verificationResult" class="p-8 text-center text-silver-steel space-y-3">
                <Loader2 class="w-8 h-8 mx-auto animate-spin text-silver-platinum" />
                <p class="text-sm">Компиляция и прогон изолированных тестов...</p>
              </div>

              <div v-else-if="verificationResult" class="space-y-4">
                <!-- Summary Banner -->
                <div 
                  class="rounded-md border p-3 flex flex-wrap items-center justify-between gap-3"
                  :class="verificationResult.behaviorPreserved ? 'bg-[#0e1713] border-sage-border text-sage-text' : 'bg-[#181113] border-ash-border text-ash-text'"
                >
                  <div class="flex items-center gap-2.5">
                    <CheckCircle2 v-if="verificationResult.behaviorPreserved" class="w-5 h-5 text-sage-text shrink-0" />
                    <AlertTriangle v-else class="w-5 h-5 text-ash-text shrink-0" />
                    <div>
                      <div class="font-bold text-sm text-silver-white">
                        {{ verificationResult.behaviorPreserved ? 'Все тесты пройдены · Поведение сохранено ✓' : 'Quality Gate не пройден · Обнаружены расхождения' }}
                      </div>
                      <div class="text-[11px] opacity-80 mt-0.5">
                        Пройдено {{ passedTestsCount }} из {{ totalTestsCount }} тестов ({{ testsSuccessPercent }}%)
                        <span v-if="verificationResult.speedupFactor > 1.0" class="ml-2 text-silver-platinum font-semibold">
                          · Ускорение: {{ verificationResult.speedupFactor.toFixed(2) }}x
                        </span>
                      </div>
                    </div>
                  </div>

                  <!-- Progress Bar -->
                  <div class="w-48 bg-black/40 rounded-full h-2 overflow-hidden border border-white/10 shrink-0">
                    <div 
                      class="h-full transition-all duration-500"
                      :class="verificationResult.behaviorPreserved ? 'bg-[#9db3a4]' : 'bg-[#b58585]'"
                      :style="{ width: `${testsSuccessPercent}%` }"
                    ></div>
                  </div>
                </div>

                <!-- Errors banner if any -->
                <div v-if="verificationResult.originalErrors || verificationResult.optimizedErrors" class="bg-[#140e10] border border-ash-border rounded p-3 text-ash-text">
                  <div class="font-bold text-xs mb-1">Ошибки компиляции или выполнения:</div>
                  <pre class="text-[11px] whitespace-pre-wrap font-mono">{{ verificationResult.originalErrors || verificationResult.optimizedErrors }}</pre>
                </div>

                <!-- Output comparison -->
                <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
                  <div class="rounded border border-white/[0.08] bg-[#08090d] p-3">
                    <p class="mb-2 text-[10px] uppercase tracking-wider text-silver-dark">Исходный вывод</p>
                    <pre class="max-h-48 overflow-auto whitespace-pre-wrap text-[11px] text-silver-brushed">{{ verificationResult.originalOutput || '—' }}</pre>
                  </div>
                  <div class="rounded border border-white/[0.08] bg-[#08090d] p-3">
                    <p class="mb-2 text-[10px] uppercase tracking-wider text-silver-dark">Оптимизированный вывод</p>
                    <pre class="max-h-48 overflow-auto whitespace-pre-wrap text-[11px] text-silver-brushed">{{ verificationResult.optimizedOutput || '—' }}</pre>
                  </div>
                </div>

                <!-- Tests Table -->
                <div class="rounded border border-white/[0.08] overflow-hidden bg-[#08090d]">
                  <table class="w-full text-left border-collapse">
                    <thead>
                      <tr class="bg-[#0c0e15] border-b border-white/[0.08] text-[11px] text-silver-steel uppercase">
                        <th class="py-2 px-3">Тестовый сценарий</th>
                        <th class="py-2 px-3">Статус</th>
                        <th class="py-2 px-3 text-right">Время выполнения</th>
                        <th class="py-2 px-3">Результат верификации</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-white/[0.05]">
                      <tr 
                        v-for="test in verificationResult.testResults" 
                        :key="test.testName"
                        class="hover:bg-white/[0.02] transition-colors"
                      >
                        <td class="py-2 px-3 font-semibold text-silver-platinum flex items-center gap-2">
                          <span 
                            class="w-2 h-2 rounded-full shrink-0" 
                            :class="test.passed ? 'bg-sage-text' : 'bg-ash-text'"
                          ></span>
                          <span>{{ test.testName }}</span>
                        </td>
                        <td class="py-2 px-3">
                          <span 
                            class="px-2 py-0.5 rounded text-[10px] font-semibold"
                            :class="test.passed ? 'bg-sage-tint text-sage-text border border-sage-border' : 'bg-ash-tint text-ash-text border border-ash-border'"
                          >
                            {{ test.passed ? 'Успешно' : 'Ошибка' }}
                          </span>
                        </td>
                        <td class="py-2 px-3 text-right font-mono text-silver-steel text-[11px]">
                          {{ test.optimizedTime }}&nbsp;мс
                          <span v-if="test.originalTime > 0" class="text-silver-dark text-[10px]">
                            (база: {{ test.originalTime }}&nbsp;мс)
                          </span>
                        </td>
                        <td class="py-2 px-3 text-silver-brushed text-[11px]">
                          {{ test.message }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </section>

          <button
            v-else
            class="h-8 shrink-0 border-t border-white/[0.08] bg-[#0c0e14] px-3 flex items-center gap-2 text-xs text-silver-steel hover:text-silver-platinum hover:bg-white/[0.05] transition-colors"
            title="Показать нижнюю панель"
            @click="bottomPanelVisible = true"
          >
            <Maximize2 class="w-3.5 h-3.5" />
            <span>Показать нижнюю панель</span>
          </button>
      </main>
    </div>

    <!-- ===== Audit Report Modal Dialog ===== -->
    <div 
      v-if="auditModalOpen" 
      class="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4"
      @click.self="auditModalOpen = false"
    >
      <div class="w-full max-w-lg rounded-xl border border-white/20 bg-[#0e131d] shadow-2xl p-6 text-silver-brushed space-y-5">
        
        <!-- Modal Header -->
        <div class="flex items-center justify-between border-b border-white/10 pb-3">
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-lg bg-[#a4c0ac]/15 border border-[#a4c0ac]/30 flex items-center justify-center text-[#a4c0ac]">
              <FileText class="w-4 h-4" />
            </div>
            <div>
              <h3 class="text-sm font-bold text-white">Аудиторский отчет проекта</h3>
              <p class="text-[11px] text-silver-steel">WHAT Enterprise Blueprint Suite • Java 21</p>
            </div>
          </div>
          <button 
            class="jb-icon-btn text-silver-steel hover:text-white" 
            @click="auditModalOpen = false" 
            title="Закрыть"
          >
            <X class="w-4 h-4" />
          </button>
        </div>

        <!-- Export Action Options -->
        <div class="space-y-3 pt-1">
          <!-- Section 1: Documents -->
          <div class="space-y-1.5">
            <div class="text-[10px] font-semibold text-silver-dark uppercase tracking-wider px-1">Аудиторские отчеты</div>
            
            <button 
              class="w-full p-2.5 rounded-lg border border-white/10 bg-[#121620] hover:bg-[#18202d] hover:border-white/20 transition-all flex items-center justify-between group cursor-pointer text-left"
              @click="exportAuditReport('pdf')"
            >
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-lg bg-emerald-500/[0.08] border border-emerald-500/20 text-emerald-400/80 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
                  <Printer class="w-4 h-4" />
                </div>
                <div>
                  <div class="text-xs font-semibold text-silver-white group-hover:text-white transition-colors">Печать в PDF / Фирменный HTML</div>
                  <div class="text-[11px] text-silver-dark group-hover:text-silver-steel transition-colors">Официальный отчет для презентации и заказчика</div>
                </div>
              </div>
              <ChevronRight class="w-4 h-4 text-silver-dark group-hover:text-white transition-colors" />
            </button>

            <button 
              class="w-full p-2.5 rounded-lg border border-white/10 bg-[#121620] hover:bg-[#18202d] hover:border-white/20 transition-all flex items-center justify-between group cursor-pointer text-left"
              @click="exportAuditReport('markdown')"
            >
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-lg bg-sky-500/[0.08] border border-sky-500/20 text-sky-400/80 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
                  <FileCode class="w-4 h-4" />
                </div>
                <div>
                  <div class="text-xs font-semibold text-silver-white group-hover:text-white transition-colors">Скачать Markdown (.md)</div>
                  <div class="text-[11px] text-silver-dark group-hover:text-silver-steel transition-colors">Таблицы дефектов и метрик для Git / PR</div>
                </div>
              </div>
              <ChevronRight class="w-4 h-4 text-silver-dark group-hover:text-white transition-colors" />
            </button>
          </div>

          <!-- Section 2: Graph Architecture Images -->
          <div class="space-y-1.5 pt-1">
            <div class="text-[10px] font-semibold text-silver-dark uppercase tracking-wider px-1">Снимки графа архитектуры</div>

            <button 
              class="w-full p-2.5 rounded-lg border border-white/10 bg-[#121620] hover:bg-[#18202d] hover:border-white/20 transition-all flex items-center justify-between group cursor-pointer text-left"
              :disabled="isExportingImage || graphNodes.length === 0"
              @click="handleExportGraphImage('png')"
            >
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-lg bg-indigo-500/[0.08] border border-indigo-500/20 text-indigo-400/80 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
                  <ImageIcon class="w-4 h-4" />
                </div>
                <div>
                  <div class="text-xs font-semibold text-silver-white group-hover:text-white transition-colors">Экспорт графа в PNG (.png)</div>
                  <div class="text-[11px] text-silver-dark group-hover:text-silver-steel transition-colors">Четкий растровый снимок 2x Ultra HD без потерь качества</div>
                </div>
              </div>
              <ChevronRight class="w-4 h-4 text-silver-dark group-hover:text-white transition-colors" />
            </button>

            <button 
              class="w-full p-2.5 rounded-lg border border-white/10 bg-[#121620] hover:bg-[#18202d] hover:border-white/20 transition-all flex items-center justify-between group cursor-pointer text-left"
              :disabled="isExportingImage || graphNodes.length === 0"
              @click="handleExportGraphImage('jpeg')"
            >
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded-lg bg-amber-500/[0.08] border border-amber-500/20 text-amber-400/80 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
                  <FileImage class="w-4 h-4" />
                </div>
                <div>
                  <div class="text-xs font-semibold text-silver-white group-hover:text-white transition-colors">Экспорт графа в JPEG (.jpg)</div>
                  <div class="text-[11px] text-silver-dark group-hover:text-silver-steel transition-colors">Оптимизированное сжатое изображение для слайдов и презентаций</div>
                </div>
              </div>
              <ChevronRight class="w-4 h-4 text-silver-dark group-hover:text-white transition-colors" />
            </button>
          </div>
        </div>

        <div class="flex items-center justify-between pt-2 border-t border-white/10 text-[11px] text-silver-dark">
          <span>Сформировано платформой WHAT</span>
          <button class="hover:text-white transition-colors cursor-pointer" @click="auditModalOpen = false">Отмена</button>
        </div>

      </div>
    </div>

    <!-- ===== 3. STATUS BAR ===== -->
    <footer class="h-6 border-t border-white/[0.08] bg-[#0a0c13] px-3 flex items-center justify-between text-[10px] font-mono text-silver-dark shrink-0">
      <div class="flex items-center gap-3">

        <div v-if="projectLoaded" class="flex items-center gap-1.5">
          <CheckCircle2 class="w-3 h-3 text-sage-text" />
          <span class="text-sage-text">Ready</span>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <span v-if="currentFile">{{ currentFile }} · строка {{ cursorLine }}</span>
        <span>UTF-8</span>

      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import dagre from 'dagre'

// Dagre layout function
function applyDagreLayout(nodes: any[], edges: any[]) {
  const g = new dagre.graphlib.Graph()
  g.setGraph({
    rankdir: 'TB',
    align: 'UL',
    nodesep: 40,
    ranksep: 50,
    marginx: 80,
    marginy: 60,
  })
  g.setDefaultEdgeLabel(() => ({}))

  nodes.forEach((node) => {
    g.setNode(node.id, { width: 220, height: 85 })
  })

  edges.forEach((edge) => {
    const source = edge.source ?? edge.from
    const target = edge.target ?? edge.to
    if (source && target && source !== target) {
      if (nodes.some(n => n.id === source) && nodes.some(n => n.id === target)) {
        g.setEdge(source, target)
      }
    }
  })

  dagre.layout(g)

  return nodes.map((node) => {
    const position = g.node(node.id)
    return {
      ...node,
      dimensions: { width: 220, height: 85 },
      position: {
        x: position ? position.x - 110 : 0,
        y: position ? position.y - 42 : 0,
      },
    }
  })
}
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as monaco from 'monaco-editor'
import { 
  optimizeProjectWithAi, 
  applyOptimization, 
  optimizeProject, 
  loadDemoProject, 
  mutateGraph, 
  type VerificationResult, 
  type OptimizationChange 
} from './api/client'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import { 
  FolderTree, FileCode2, Play, Zap, Search, Download, Boxes,
  Settings, X, Info, AlertTriangle, CheckCircle2, Gauge,
  Workflow, Maximize2, Minimize2, ChevronDown, ChevronRight, ChevronsDown, ChevronsUp,
  ShieldCheck, Loader2, GitCompareArrows, Sparkles, Check,
  FileText, Printer, FileCode, Map as MapIcon, Flame, Image as ImageIcon, FileImage
} from 'lucide-vue-next'
import { generateAuditMarkdown, generateAuditHtml, downloadFile, openPrintableReport, type AuditReportData } from './utils/auditReport'
import { exportGraphImage as triggerGraphExport } from './utils/exportGraph'
import { getNodeHeatLevel, getHeatColor } from './utils/heatmap'
import ChromeNode from './components/nodes/ChromeNode.vue'
import FileImportButton from './components/FileImportButton.vue'

// State
const { setCenter, fitView, zoomIn, zoomOut, removeSelectedElements } = useVueFlow()
const leftTab = ref<'project' | 'analysis' | 'palette' | null>(null)
const activeBottomTab = ref<'code' | 'console' | 'tests' | 'optimization' | 'diff'>('code')
const activeAnalysisTab = ref<'defects' | 'recommendations' | 'metrics'>('defects')
const editedNodeCode = ref('')
const customInput = ref('')
const editorElement = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let editorSubscription: monaco.IDisposable | null = null
let editorDecorationIds: string[] = []
let editorSyncTimer: ReturnType<typeof setTimeout> | undefined
let suppressEditorSync = false
const selectedGraphNode = ref<any>(null)
const bottomPanelVisible = ref(true)
const projectLoaded = ref(false)
const isProcessing = ref(false)
const showMiniMap = ref(true)
const heatmapMode = ref(false)
const miniMapTick = ref(0)

const currentMiniMapColorFunc = computed(() => {
  const isHeat = heatmapMode.value
  const selectedId = selectedGraphNode.value?.id
  void miniMapTick.value
  return (node: any) => {
    const isSelected = node.selected || (selectedId && node.id === selectedId)
    if (isSelected) return '#38bdf8'
    const d = node.data || node
    if (isHeat) {
      const level = getNodeHeatLevel(d)
      return getHeatColor(level)
    }
    // Standard mode (Heatmap OFF): calm dark tones, matching main graph
    if (d.isOverview || d.isMethod || d.type === 'METHOD') {
      return '#263042'
    }
    return '#181e29'
  }
})

const currentMiniMapStrokeFunc = computed(() => {
  const isHeat = heatmapMode.value
  const selectedId = selectedGraphNode.value?.id
  void miniMapTick.value
  return (node: any) => {
    const isSelected = node.selected || (selectedId && node.id === selectedId)
    if (isSelected) return '#ffffff'
    const d = node.data || node
    if (isHeat) {
      const level = getNodeHeatLevel(d)
      return getHeatColor(level)
    }
    // Standard mode (Heatmap OFF): subtle defect borders matching main graph
    if (d.severity === 'HIGH') return '#b58585'
    if (d.severity === 'MEDIUM' || d.complexity?.includes('n²')) return '#cfc4b4'
    return 'rgba(255, 255, 255, 0.12)'
  }
})

function handleMiniMapNodeClick(param: any) {
  const node = param?.node
  if (!node) return
  handleNodeClick({ node })
  const pos = node.computedPosition || node.position
  const dim = node.dimensions || { width: 220, height: 85 }
  if (pos) {
    void setCenter(pos.x + (dim.width / 2), pos.y + (dim.height / 2), { zoom: 0.95, duration: 300 })
  }
}

function handleMiniMapNodeDblClick(param: any) {
  const node = param?.node
  if (!node) return
  handleNodeDblClick({ node })
}

function handleMiniMapClick(eventData: any) {
  if (eventData?.event?.target?.tagName === 'rect') return
  const el = eventData?.event?.currentTarget
  if (!el) return
  const rect = el.getBoundingClientRect()
  const clickX = eventData.event.clientX - rect.left
  const clickY = eventData.event.clientY - rect.top
  const pctX = Math.max(0, Math.min(1, clickX / rect.width))
  const pctY = Math.max(0, Math.min(1, clickY / rect.height))
  
  const nodes = graphNodes.value
  if (!nodes.length) return
  const minX = Math.min(...nodes.map(n => n.position?.x || 0))
  const maxX = Math.max(...nodes.map(n => (n.position?.x || 0) + (n.dimensions?.width || 220)))
  const minY = Math.min(...nodes.map(n => n.position?.y || 0))
  const maxY = Math.max(...nodes.map(n => (n.position?.y || 0) + (n.dimensions?.height || 85)))
  
  const targetX = minX + pctX * (maxX - minX)
  const targetY = minY + pctY * (maxY - minY)
  
  void setCenter(targetX, targetY, { duration: 300 })
}

watch(heatmapMode, (newVal) => {
  miniMapTick.value++
  graphNodes.value = graphNodes.value.map(n => ({
    ...n,
    data: {
      ...n.data,
      heatmapMode: newVal
    }
  }))
})

const projectFiles = ref<Record<string, string>>({})
const originalFiles = ref<Record<string, string>>({})
const diffFile = ref('')
const diffViewMode = ref<'unified' | 'split'>('unified')
const currentFile = ref<string>('')
const currentMainFile = ref<string>('')
const cursorLine = ref(1)

const graphNodes = ref<any[]>([])
const graphEdges = ref<any[]>([])
const rawGraphData = ref<{ nodes: any[]; edges: any[] }>({ nodes: [], edges: [] })
const expandedMethodIds = ref<Set<string>>(new Set())

const openMethodTabs = ref<string[]>(['__overview__'])
const activeMethodTab = ref<string>('__overview__')

const methodList = computed(() => {
  const nodes = rawGraphData.value.nodes || []
  return nodes
    .filter(n => n.type === 'METHOD' || n.id.startsWith('method_') || n.id.startsWith('constructor_'))
    .sort((a, b) => (a.location?.startLine || a.data?.line || 0) - (b.location?.startLine || b.data?.line || 0))
})

function getMethodDisplayName(mId: string): string {
  const node = (rawGraphData.value.nodes || []).find(n => n.id === mId)
  if (!node) return mId
  return (node.label || node.id).replace(/^(Method|Constructor):\s*/, '').replace(/\(.*$/, '') + '()'
}

function openMethodInTab(methodId: string) {
  if (!openMethodTabs.value.includes(methodId)) {
    openMethodTabs.value.push(methodId)
  }
  activeMethodTab.value = methodId
  rebuildVisibleGraph()
}

function closeMethodTab(methodId: string) {
  openMethodTabs.value = openMethodTabs.value.filter(id => id !== methodId)
  if (activeMethodTab.value === methodId) {
    activeMethodTab.value = openMethodTabs.value[openMethodTabs.value.length - 1] || '__overview__'
  }
  rebuildVisibleGraph()
}

function switchMethodTab(methodId: string) {
  activeMethodTab.value = methodId
  rebuildVisibleGraph()
}

function getMethodName(nodeId: string, nodeLabel: string): string {
  if (nodeId.startsWith('method_') && nodeId.endsWith('_start')) {
    return nodeId.slice(7, -6)
  }
  if (nodeId.startsWith('constructor_') && nodeId.endsWith('_start')) {
    return nodeId.slice(12, -6)
  }
  return nodeLabel.replace(/^(Method|Constructor):\s*/, '').replace(/\(.*$/, '').trim()
}

function getMethodChildNodeIds(rootId: string, methodName: string, allNodes: any[], allEdges: any[]): Set<string> {
  const childIds = new Set<string>()

  allNodes.forEach(n => {
    if (n.id === rootId) return
    if (
      n.id.startsWith(`${methodName}_`) ||
      n.id === `method_${methodName}_end` ||
      n.id === `constructor_${methodName}_end`
    ) {
      childIds.add(n.id)
    }
  })

  const queue = [rootId]
  const visited = new Set<string>([rootId])
  const otherMethodIds = new Set(
    allNodes
      .filter(n => n.id !== rootId && (n.type === 'METHOD' || n.id.startsWith('method_') || n.id.startsWith('constructor_')))
      .map(n => n.id)
  )

  while (queue.length > 0) {
    const cur = queue.shift()!
    const outEdges = allEdges.filter(e => (e.from || e.source) === cur)
    for (const e of outEdges) {
      const targetId = e.to || e.target
      if (!visited.has(targetId) && !otherMethodIds.has(targetId) && targetId !== 'start') {
        visited.add(targetId)
        childIds.add(targetId)
        queue.push(targetId)
      }
    }
  }

  return childIds
}

function rebuildVisibleGraph() {
  const rawNodes = rawGraphData.value.nodes
  const rawEdges = rawGraphData.value.edges
  if (!rawNodes || !rawNodes.length) {
    graphNodes.value = []
    graphEdges.value = []
    return
  }

  const methodRoots = rawNodes
    .filter(n => n.type === 'METHOD' || n.id.startsWith('method_') || n.id.startsWith('constructor_'))
    .sort((a, b) => (a.location?.startLine || a.data?.line || 0) - (b.location?.startLine || b.data?.line || 0))

  const methodChildMap = new Map<string, Set<string>>()
  methodRoots.forEach(m => {
    const mName = getMethodName(m.id, m.label || m.data?.label || '')
    methodChildMap.set(m.id, getMethodChildNodeIds(m.id, mName, rawNodes, rawEdges))
  })

  // CASE 1: OVERVIEW TAB (Show grid of method cards)
  if (activeMethodTab.value === '__overview__') {
    const startNode = rawNodes.find(n => n.id === 'start')
    const visibleNodes: any[] = []
    const visibleEdges: any[] = []

    const COLS = Math.min(4, Math.max(2, Math.ceil(Math.sqrt(Math.max(1, methodRoots.length * 1.4)))))
    const CARD_W = 240
    const CARD_H = 88
    const GAP_X = 40
    const GAP_Y = 32

    if (startNode) {
      const centerX = ((COLS - 1) * (CARD_W + GAP_X)) / 2 + 50
      visibleNodes.push({
        id: startNode.id,
        type: 'custom',
        position: { x: centerX, y: 30 },
        dimensions: { width: CARD_W, height: CARD_H },
        data: {
          label: startNode.label,
          sublabel: 'CLASS OVERVIEW',
          type: 'START',
          line: startNode.location?.startLine,
          code: `${methodRoots.length} методов в классе (кликните метод для открытия)`,
          filePath: startNode.location?.filePath,
          variant: 'primary',
          isOverview: true
        }
      })
    }

    methodRoots.forEach((m, idx) => {
      const col = idx % COLS
      const row = Math.floor(idx / COLS)
      const x = col * (CARD_W + GAP_X) + 50
      const y = (startNode ? 160 : 40) + row * (CARD_H + GAP_Y)
      const childCount = methodChildMap.get(m.id)?.size || 0

      const mName = getMethodName(m.id, m.label || m.data?.label || '')
      const mStartLine = m.location?.startLine || m.data?.line || 0
      const mEndLine = m.location?.endLine || (mStartLine + 120)
      
      const methodDefects = (analysisReport.value?.defects || []).filter((defect: any) => {
        if (mName && defect.id && defect.id.toLowerCase().includes(mName.toLowerCase())) return true
        const dStart = defect.location?.startLine
        if (!dStart || !mStartLine) return false
        return (dStart >= mStartLine && dStart <= mEndLine) || dStart === mStartLine
      })

      const mSeverity = methodDefects.some((d: any) => d.severity === 'HIGH')
        ? 'HIGH'
        : (methodDefects.some((d: any) => d.severity === 'MEDIUM') ? 'MEDIUM' : (methodDefects.length ? 'LOW' : undefined))

      const mComplexity = methodDefects.find((d: any) => d.complexity?.includes('n³') || d.complexity?.includes('n^3'))?.complexity
        || methodDefects.find((d: any) => d.complexity?.includes('n²') || d.complexity?.includes('n^2'))?.complexity
        || methodDefects.find((d: any) => d.complexity)?.complexity
        || (m.complexity ? `O(${m.complexity})` : (methodDefects.length ? 'O(n²)' : undefined))

      visibleNodes.push({
        id: m.id,
        type: 'custom',
        position: { x, y },
        dimensions: { width: CARD_W, height: CARD_H },
        data: {
          label: m.label,
          sublabel: m.type || 'METHOD',
          type: m.type || 'METHOD',
          line: m.location?.startLine,
          code: m.code || '',
          filePath: m.location?.filePath,
          variant: 'default',
          severity: mSeverity,
          complexity: mComplexity,
          isMethod: true,
          childCount,
          isOverview: true,
          heatmapMode: heatmapMode.value
        }
      })

      if (startNode) {
        visibleEdges.push({
          id: `start-${m.id}`,
          source: startNode.id,
          target: m.id,
          label: '',
          animated: false,
          style: { stroke: '#475569', strokeWidth: 1.2 }
        })
      }
    })

    graphNodes.value = visibleNodes
    graphEdges.value = visibleEdges
    miniMapTick.value++
    scheduleGraphFit()
    return
  }

  // CASE 2: SINGLE METHOD TAB (Dedicated isolated CFG of this method)
  const targetMethodId = activeMethodTab.value
  const targetMethod = methodRoots.find(m => m.id === targetMethodId)
  const childIds = methodChildMap.get(targetMethodId) || new Set<string>()

  if (!targetMethod && !childIds.size) {
    activeMethodTab.value = '__overview__'
    rebuildVisibleGraph()
    return
  }

  const memberIds = new Set<string>([targetMethodId, ...childIds])
  const targetMethodName = targetMethod ? getMethodName(targetMethod.id, targetMethod.label || '') : ''
  const methodNodes = rawNodes.filter(n => memberIds.has(n.id)).map(node => {
    const isMethodRoot = node.id === targetMethodId
    const nodeLine = node.location?.startLine || node.data?.line
    const nodeEndLine = node.location?.endLine || nodeLine

    const nodeDefects = (analysisReport.value?.defects || []).filter((defect: any) => {
      const dStart = defect.location?.startLine
      const dEnd = defect.location?.endLine || dStart
      if (!dStart || !nodeLine) return false
      if (isMethodRoot) {
        if (targetMethodName && defect.id && defect.id.toLowerCase().includes(targetMethodName.toLowerCase())) return true
        return (dStart >= nodeLine && dStart <= (nodeEndLine || nodeLine + 120))
      }
      return (dStart <= (nodeEndLine || nodeLine) && dEnd >= nodeLine) || dStart === nodeLine
    })

    const nSeverity = nodeDefects.some((d: any) => d.severity === 'HIGH')
      ? 'HIGH'
      : (nodeDefects.some((d: any) => d.severity === 'MEDIUM') ? 'MEDIUM' : (nodeDefects.length ? 'LOW' : undefined))

    let nComplexity = nodeDefects.find((d: any) => d.complexity?.includes('n³') || d.complexity?.includes('n^3'))?.complexity
      || nodeDefects.find((d: any) => d.complexity?.includes('n²') || d.complexity?.includes('n^2'))?.complexity
      || nodeDefects.find((d: any) => d.complexity)?.complexity
      || (node.complexity ? `O(${node.complexity})` : undefined)

    const nType = (node.type || node.data?.type || '').toUpperCase()
    const nLabel = (node.label || node.data?.label || '').toLowerCase()

    if (!nComplexity) {
      if (['FOR', 'FOREACH', 'WHILE', 'LOOP'].includes(nType) || nLabel.includes('for') || nLabel.includes('while')) {
        const isNested = node.code && (node.code.match(/\b(for|while)\b/g) || []).length >= 2
        nComplexity = isNested ? 'O(n²)' : 'O(n)'
      } else if (['IF', 'ELSE', 'SWITCH', 'CASE', 'BRANCH'].includes(nType) || nLabel.includes('if') || nLabel.includes('switch') || nLabel.includes('case')) {
        nComplexity = 'O(1)'
      }
    }

    return {
      id: node.id,
      type: 'custom',
      position: { x: 0, y: 0 },
      dimensions: { width: 220, height: 85 },
      data: {
        label: node.label || node.data?.label || node.id,
        sublabel: node.type || node.data?.sublabel || 'STATEMENT',
        type: node.type || node.data?.type || 'STATEMENT',
        line: node.location?.startLine || node.data?.line,
        code: node.code || node.data?.code || '',
        filePath: node.location?.filePath || node.data?.filePath,
        variant: isMethodRoot ? 'primary' : 'default',
        severity: nSeverity,
        complexity: nComplexity,
        isMethod: isMethodRoot,
        childCount: isMethodRoot ? childIds.size : 0,
        isOverview: false,
        heatmapMode: heatmapMode.value
      }
    }
  })

  const methodEdges = rawEdges.filter(e => {
    const src = e.from || e.source
    const tgt = e.to || e.target
    return memberIds.has(src) && memberIds.has(tgt)
  }).map(edge => ({
    id: `${edge.from || edge.source}-${edge.to || edge.target}`,
    source: edge.from || edge.source,
    target: edge.to || edge.target,
    label: edge.label || edge.condition || '',
    animated: !!(edge.label || edge.condition),
    style: { stroke: '#94a3b8', strokeWidth: 1.5 }
  }))

  graphNodes.value = applyDagreLayout(methodNodes, methodEdges)
  graphEdges.value = methodEdges
  miniMapTick.value++
  scheduleGraphFit()
}

function setRawGraph(rawGraph: { nodes: any[]; edges: any[] }, resetExpanded: boolean = true) {
  rawGraphData.value = {
    nodes: rawGraph?.nodes || [],
    edges: rawGraph?.edges || []
  }
  if (resetExpanded) {
    openMethodTabs.value = ['__overview__']
    activeMethodTab.value = '__overview__'
    expandedMethodIds.value = new Set()
  }
  rebuildVisibleGraph()
}

function toggleMethodExpand(nodeIdOrEvent: any) {
  const methodId = typeof nodeIdOrEvent === 'string' ? nodeIdOrEvent : nodeIdOrEvent?.node?.id
  if (!methodId) return

  if (activeMethodTab.value === '__overview__') {
    openMethodInTab(methodId)
    return
  }

  const methodRoots = rawGraphData.value.nodes.filter(n => n.type === 'METHOD' || n.id.startsWith('method_') || n.id.startsWith('constructor_'))
  if (!methodRoots.some(m => m.id === methodId)) return

  const next = new Set(expandedMethodIds.value)
  if (next.has(methodId)) {
    next.delete(methodId)
  } else {
    next.add(methodId)
  }
  expandedMethodIds.value = next
  rebuildVisibleGraph()
}

function handleNodeDblClick(event: any) {
  handleNodeClick(event)
  bottomPanelVisible.value = true
  activeBottomTab.value = 'code'
  nextTick(() => {
    if (editor) {
      editor.focus()
      const line = event?.node?.data?.line
      if (line) {
        editor.revealLineInCenter(line)
      }
    }
  })
}
const analysisReport = ref<any>(null)
watch(analysisReport, () => {
  rebuildVisibleGraph()
})
const selectedDefect = ref<any>(null)
const expandedDirectories = ref<Set<string>>(new Set())


const consoleLogs = ref<{ text: string; color: string }[]>([])

const bottomTabs = [
  { id: 'optimization', label: 'Оптимизация', icon: Zap },
  { id: 'code', label: 'Код', icon: FileCode2 },
  { id: 'console', label: 'Консоль', icon: Settings },
  { id: 'tests', label: 'Тесты', icon: CheckCircle2 },
  { id: 'diff', label: 'Diff', icon: GitCompareArrows },
] as const

// Computed
const currentFileContent = computed(() => {
  return projectFiles.value[currentFile.value] || '// Файл не выбран'
})

type ProjectTreeEntry = {
  path: string
  name: string
  kind: 'directory' | 'file'
  depth: number
  filePath?: string
}

const projectTree = computed<ProjectTreeEntry[]>(() => {
  const filePathByNormalizedPath = new Map(
    Object.keys(projectFiles.value).map((path) => [path.replaceAll('\\', '/'), path])
  )
  const files = [...filePathByNormalizedPath.keys()]
  const directories = new Set<string>()

  files.forEach((filePath) => {
    const parts = filePath.split('/').filter(Boolean)
    for (let index = 1; index < parts.length; index += 1) {
      directories.add(parts.slice(0, index).join('/'))
    }
  })

  const entries: ProjectTreeEntry[] = []
  const childrenByParent = new Map<string, string[]>()

  ;[...directories, ...files].forEach((path) => {
    const parent = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
    const children = childrenByParent.get(parent) || []
    children.push(path)
    childrenByParent.set(parent, children)
  })

  function visit(parent: string, depth: number) {
    const children = (childrenByParent.get(parent) || []).sort((a, b) => {
      const aDirectory = directories.has(a)
      const bDirectory = directories.has(b)
      if (aDirectory !== bDirectory) return aDirectory ? -1 : 1
      return a.localeCompare(b)
    })

    children.forEach((path) => {
      const name = path.substring(path.lastIndexOf('/') + 1)
      const directory = directories.has(path)
      entries.push({
        path,
        name,
        kind: directory ? 'directory' : 'file',
        depth,
        filePath: directory ? undefined : filePathByNormalizedPath.get(path),
      })
      if (directory && expandedDirectories.value.has(path)) {
        visit(path, depth + 1)
      }
    })
  }

  visit('', 0)
  return entries
})


const optimizationChanges = ref<OptimizationChange[] | null>(null)
const verificationResult = ref<VerificationResult | null>(null)

const pendingOptimizationResult = ref<any>(null)
const optimizationError = ref<string | null>(null)

const highSeverityDefectCount = computed(() => analysisReport.value?.defects?.filter((defect: any) => defect.severity === 'HIGH').length ?? 0)
const allCriticalResolved = computed(() => projectLoaded.value && analysisReport.value && highSeverityDefectCount.value === 0)

const currentBigO = computed(() => {
  if (!analysisReport.value) return 'O(1)'
  const defects = analysisReport.value.defects || []
  const hasCube = defects.some((d: any) => d.complexity?.includes('n³') || d.complexity?.includes('n^3'))
  if (hasCube) return 'O(n³)'
  const hasSquare = defects.some((d: any) => 
    d.complexity?.includes('n²') || 
    d.complexity?.includes('n^2') ||
    d.type === 'INEFFICIENT_LOOP' || 
    d.type === 'NESTED_COMPLEXITY'
  )
  if (hasSquare) return 'O(n²)'
  if ((analysisReport.value.metrics?.maxComplexity ?? 0) > 1) return 'O(n)'
  return 'O(1)'
})

const targetBigO = computed(() => {
  if (currentBigO.value === 'O(n³)' || currentBigO.value === 'O(n²)') {
    return 'O(n)'
  }
  return currentBigO.value
})

const estimatedSpeedup = computed(() => {
  const verifiedSpeedup = verificationResult.value?.speedupFactor || pendingOptimizationResult.value?.verification?.speedupFactor
  if (verifiedSpeedup && verifiedSpeedup > 1.0) {
    return `~${verifiedSpeedup.toFixed(1)}x`
  }
  if (currentBigO.value === 'O(n³)') return 'до ~12.5x'
  if (currentBigO.value === 'O(n²)') return 'до ~5.2x'
  if (currentBigO.value === 'O(n)') {
    const hasDefects = (analysisReport.value?.defects?.length ?? 0) > 0
    return hasDefects ? 'до ~1.8x' : '1.0x (Оптимально)'
  }
  return '1.0x (Оптимально)'
})

function isDefectResolvedInPending(defect: any): boolean {
  if (!pendingOptimizationResult.value?.after?.analysis?.defects) return false
  const afterDefects = pendingOptimizationResult.value.after.analysis.defects as any[]
  const stillExists = afterDefects.some((d: any) => 
    d.id === defect.id || 
    (d.type === defect.type && 
     d.location?.filePath === defect.location?.filePath && 
     Math.abs((d.location?.startLine ?? 0) - (defect.location?.startLine ?? 0)) < 4)
  )
  return !stillExists
}

const pendingFixedDefectsCount = computed(() => {
  if (!pendingOptimizationResult.value?.after?.analysis?.defects || !analysisReport.value?.defects) return 0
  return analysisReport.value.defects.filter((d: any) => isDefectResolvedInPending(d)).length
})

const diffFiles = computed(() => {
  const pendingFiles = pendingOptimizationResult.value?.after?.project?.files ? Object.keys(pendingOptimizationResult.value.after.project.files) : []
  return [...new Set([...Object.keys(originalFiles.value), ...Object.keys(projectFiles.value), ...pendingFiles])].sort()
})

const diffLeftContent = computed(() => {
  if (!diffFile.value) return ''
  if (pendingOptimizationResult.value) {
    return projectFiles.value[diffFile.value] || ''
  }
  return originalFiles.value[diffFile.value] || ''
})

const diffRightContent = computed(() => {
  if (!diffFile.value) return ''
  if (pendingOptimizationResult.value) {
    return pendingOptimizationResult.value.after?.project?.files?.[diffFile.value] || ''
  }
  return projectFiles.value[diffFile.value] || ''
})

const optimizationCandidates = computed(() => (analysisReport.value?.recommendations || []).map((recommendation: any, index: number) => ({
  id: recommendation.defectId || `recommendation-${index}`,
  title: recommendation.title,
  description: recommendation.description,
  complexity: recommendation.expectedBenefit,
  location: analysisReport.value?.defects?.find((defect: any) => defect.id === recommendation.defectId)?.location?.filePath || 'AST recommendation',
})))

const selectedOptimizationIds = ref<Set<string>>(new Set())
const selectedOptimizationCount = computed(() => selectedOptimizationIds.value.size)

const isAllOptimizationsSelected = computed(() => {
  return optimizationCandidates.value.length > 0 && selectedOptimizationIds.value.size === optimizationCandidates.value.length
})

function toggleSelectAllOptimizations() {
  if (isAllOptimizationsSelected.value) {
    selectedOptimizationIds.value = new Set()
  } else {
    selectedOptimizationIds.value = new Set(optimizationCandidates.value.map((c: any) => c.id))
  }
}

function toggleOptimization(id: string) {
  const next = new Set(selectedOptimizationIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedOptimizationIds.value = next
}
interface DiffSideItem {
  lineNum?: number
  text: string
  type: 'unchanged' | 'removed' | 'added' | 'empty'
}

interface AlignedDiffRow {
  left: DiffSideItem
  right: DiffSideItem
}

const diffLeftLinesCount = computed(() => (diffLeftContent.value ? diffLeftContent.value.split(/\r?\n/).length : 0))
const diffRightLinesCount = computed(() => (diffRightContent.value ? diffRightContent.value.split(/\r?\n/).length : 0))

const alignedDiffRows = computed<AlignedDiffRow[]>(() => {
  const oldText = diffLeftContent.value || ''
  const newText = diffRightContent.value || ''
  if (!oldText && !newText) return []

  const oldLines = oldText.split(/\r?\n/)
  const newLines = newText.split(/\r?\n/)

  const n = oldLines.length
  const m = newLines.length

  if (oldText === newText) {
    return oldLines.map((line, idx) => ({
      left: { lineNum: idx + 1, text: line, type: 'unchanged' as const },
      right: { lineNum: idx + 1, text: line, type: 'unchanged' as const }
    }))
  }

  if (n * m > 1_000_000) {
    const maxLen = Math.max(n, m)
    const rows: AlignedDiffRow[] = []
    for (let i = 0; i < maxLen; i++) {
      const o = oldLines[i]
      const nw = newLines[i]
      rows.push({
        left: i < n ? { lineNum: i + 1, text: o, type: o === nw ? 'unchanged' : 'removed' } : { text: '', type: 'empty' },
        right: i < m ? { lineNum: i + 1, text: nw, type: o === nw ? 'unchanged' : 'added' } : { text: '', type: 'empty' }
      })
    }
    return rows
  }

  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < m; j++) {
      if (oldLines[i] === newLines[j]) {
        dp[i + 1][j + 1] = dp[i][j] + 1
      } else {
        dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1])
      }
    }
  }

  interface DiffOp {
    type: 'same' | 'remove' | 'add'
    oldLine?: string
    newLine?: string
    oldNum?: number
    newNum?: number
  }

  const ops: DiffOp[] = []
  let i = n
  let j = m
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      ops.push({ type: 'same', oldLine: oldLines[i - 1], newLine: newLines[j - 1], oldNum: i, newNum: j })
      i--
      j--
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      ops.push({ type: 'add', newLine: newLines[j - 1], newNum: j })
      j--
    } else if (i > 0 && (j === 0 || dp[i][j - 1] < dp[i - 1][j])) {
      ops.push({ type: 'remove', oldLine: oldLines[i - 1], oldNum: i })
      i--
    }
  }
  ops.reverse()

  const rows: AlignedDiffRow[] = []
  let k = 0
  while (k < ops.length) {
    const op = ops[k]
    if (op.type === 'same') {
      rows.push({
        left: { lineNum: op.oldNum, text: op.oldLine ?? '', type: 'unchanged' },
        right: { lineNum: op.newNum, text: op.newLine ?? '', type: 'unchanged' }
      })
      k++
    } else {
      const removes: DiffOp[] = []
      const adds: DiffOp[] = []
      while (k < ops.length && ops[k].type !== 'same') {
        if (ops[k].type === 'remove') removes.push(ops[k])
        else if (ops[k].type === 'add') adds.push(ops[k])
        k++
      }

      const count = Math.max(removes.length, adds.length)
      for (let r = 0; r < count; r++) {
        const rem = removes[r]
        const add = adds[r]
        rows.push({
          left: rem
            ? { lineNum: rem.oldNum, text: rem.oldLine ?? '', type: 'removed' }
            : { text: '', type: 'empty' },
          right: add
            ? { lineNum: add.newNum, text: add.newLine ?? '', type: 'added' }
            : { text: '', type: 'empty' }
        })
      }
    }
  }

  return rows
})

interface UnifiedDiffLine {
  type: 'same' | 'remove' | 'add'
  oldNum?: number
  newNum?: number
  text: string
}

const unifiedDiffLines = computed<UnifiedDiffLine[]>(() => {
  const oldText = diffLeftContent.value || ''
  const newText = diffRightContent.value || ''
  if (!oldText && !newText) return []

  const oldLines = oldText.split(/\r?\n/)
  const newLines = newText.split(/\r?\n/)

  const n = oldLines.length
  const m = newLines.length

  if (oldText === newText) {
    return oldLines.map((line, idx) => ({
      type: 'same' as const,
      oldNum: idx + 1,
      newNum: idx + 1,
      text: line
    }))
  }

  if (n * m > 1_000_000) {
    const lines: UnifiedDiffLine[] = []
    const maxLen = Math.max(n, m)
    for (let i = 0; i < maxLen; i++) {
      const o = oldLines[i]
      const nw = newLines[i]
      if (o === nw) {
        lines.push({ type: 'same', oldNum: i + 1, newNum: i + 1, text: o })
      } else {
        if (i < n) lines.push({ type: 'remove', oldNum: i + 1, text: o })
        if (i < m) lines.push({ type: 'add', newNum: i + 1, text: nw })
      }
    }
    return lines
  }

  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < m; j++) {
      if (oldLines[i] === newLines[j]) {
        dp[i + 1][j + 1] = dp[i][j] + 1
      } else {
        dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1])
      }
    }
  }

  interface DiffOp {
    type: 'same' | 'remove' | 'add'
    oldLine?: string
    newLine?: string
    oldNum?: number
    newNum?: number
  }

  const ops: DiffOp[] = []
  let i = n
  let j = m
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      ops.push({ type: 'same', oldLine: oldLines[i - 1], newLine: newLines[j - 1], oldNum: i, newNum: j })
      i--
      j--
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      ops.push({ type: 'add', newLine: newLines[j - 1], newNum: j })
      j--
    } else if (i > 0 && (j === 0 || dp[i][j - 1] < dp[i - 1][j])) {
      ops.push({ type: 'remove', oldLine: oldLines[i - 1], oldNum: i })
      i--
    }
  }
  ops.reverse()

  const result: UnifiedDiffLine[] = []
  let k = 0
  while (k < ops.length) {
    const op = ops[k]
    if (op.type === 'same') {
      result.push({
        type: 'same',
        oldNum: op.oldNum,
        newNum: op.newNum,
        text: op.oldLine ?? ''
      })
      k++
    } else {
      const removes: DiffOp[] = []
      const adds: DiffOp[] = []
      while (k < ops.length && ops[k].type !== 'same') {
        if (ops[k].type === 'remove') removes.push(ops[k])
        else if (ops[k].type === 'add') adds.push(ops[k])
        k++
      }

      for (const r of removes) {
        result.push({
          type: 'remove',
          oldNum: r.oldNum,
          text: r.oldLine ?? ''
        })
      }
      for (const a of adds) {
        result.push({
          type: 'add',
          newNum: a.newNum,
          text: a.newLine ?? ''
        })
      }
    }
  }

  return result
})

const diffStats = computed(() => {
  let additions = 0
  let deletions = 0
  for (const line of unifiedDiffLines.value) {
    if (line.type === 'add') additions++
    else if (line.type === 'remove') deletions++
  }
  return { additions, deletions }
})
const paletteBlocks = [
  { label: 'Variable Declaration', code: 'int value = 0;' },
  { label: 'Branching / If', code: 'if (condition) { }' },
  { label: 'For Loop', code: 'for (int i = 0; i < n; i++) { }' },
  { label: 'While Loop', code: 'while (condition) { }' },
  { label: 'Method Declaration', code: 'public static void helper() { }' },
  { label: 'Return Statement', code: 'return result;' },
  { label: 'Try / Catch', code: 'try { } catch (Exception e) { }' },
]

const monacoTheme = {
  base: 'vs-dark' as const,
  inherit: true,
  rules: [
    { token: 'keyword', foreground: 'C8D1DE' },
    { token: 'type', foreground: 'B8D8F0' },
    { token: 'string', foreground: 'B8D9C5' },
    { token: 'comment', foreground: '657080' },
  ],
  colors: {
    'editor.background': '#050608',
    'editor.foreground': '#C9D1DD',
    'editorLineNumber.foreground': '#596372',
    'editorLineNumber.activeForeground': '#D5DCE6',
    'editor.lineHighlightBackground': '#10131A',
    'editorCursor.foreground': '#E2E8F0',
    'editor.selectionBackground': '#33415588',
  },
}

const passedTestsCount = computed(() => {
  return verificationResult.value?.testResults.filter(t => t.passed).length ?? 0
})

const totalTestsCount = computed(() => {
  return verificationResult.value?.testResults.length ?? 0
})

const testsSuccessPercent = computed(() => {
  if (!totalTestsCount.value) return 0
  return Math.round((passedTestsCount.value / totalTestsCount.value) * 100)
})

async function runQualityGateVerification() {
  if (!projectLoaded.value || isProcessing.value) return
  isProcessing.value = true
  activeBottomTab.value = 'tests'
  consoleLogs.value.push({
    text: '[QualityGate] Запуск изолированной дифференциальной верификации...',
    color: 'text-silver-300'
  })

  try {
    const response = await fetch('http://localhost:8080/api/project/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const result: VerificationResult = await response.json()
    verificationResult.value = result

    if (result.behaviorPreserved) {
      consoleLogs.value.push({
        text: `[QualityGate] 100% PASS (${result.testResults.length} тестов). Поведение сохранено ✓`,
        color: 'text-sage-text'
      })
    } else {
      consoleLogs.value.push({
        text: `[QualityGate] Сохранение поведения не подтверждено`,
        color: 'text-[#b58585]'
      })
    }
  } catch (err: any) {
    consoleLogs.value.push({
      text: `[QualityGate] Ошибка запроса: ${err.message}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}
const searchVisible = ref(false)
const searchQuery = ref('')
const searchResults = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  return query ? graphNodes.value.filter(node => [node.data.label, node.data.code, node.data.filePath].join(' ').toLocaleLowerCase().includes(query)) : []
})
const workspaceElement = ref<HTMLElement | null>(null)
const bottomPanelHeight = ref(300)
const maxPanelHeight = ref(600)
let resizeObserver: ResizeObserver | undefined
let resizeTarget: HTMLElement | null = null
let resizeOrigin = 0
let resizeHeight = 0
const isGraphMaximized = ref(false)
const auditModalOpen = ref(false)

function openAuditModal() {
  auditModalOpen.value = true
}

function buildAuditReportData(): AuditReportData {
  const now = new Date()
  const pad = (n: number) => n.toString().padStart(2, '0')
  const timestamp = `${pad(now.getDate())}.${pad(now.getMonth() + 1)}.${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  
  const metrics = analysisReport.value?.metrics || {
    totalLines: currentMainFile.value ? (projectFiles.value[currentMainFile.value]?.split('\n').length || 0) : 0,
    totalMethods: methodList.value.length || 0,
    averageComplexity: 1.0,
    maxComplexity: 1,
    duplicatedBlocks: 0
  }

  const defects = (analysisReport.value?.defects || []).map((d: any) => ({
    id: d.id || '',
    title: d.title || d.type || 'Дефект',
    type: d.type || 'DEFECT',
    severity: d.severity || 'LOW',
    location: d.location,
    description: d.description,
    codeFragment: d.codeFragment,
    isResolved: isDefectResolvedInPending(d)
  }))

  return {
    projectName: 'WHAT Enterprise Blueprints',
    mainFile: currentMainFile.value ? currentMainFile.value.split('/').pop() || currentMainFile.value : 'Main.java',
    timestamp,
    metrics,
    defects,
    recommendations: analysisReport.value?.recommendations || [],
    currentBigO: currentBigO.value,
    targetBigO: targetBigO.value,
    nodeCount: graphNodes.value.length || rawGraphData.value.nodes.length,
    verification: verificationResult.value || pendingOptimizationResult.value?.verification || null,
    diffStats: diffStats.value
  }
}

function exportAuditReport(format: 'markdown' | 'pdf') {
  auditModalOpen.value = false
  const data = buildAuditReportData()
  const fileBase = (data.mainFile || 'Project').replace(/\.[^/.]+$/, '')
  const dateStr = new Date().toISOString().slice(0, 10)

  if (format === 'markdown') {
    const md = generateAuditMarkdown(data)
    downloadFile(`WHAT_Audit_${fileBase}_${dateStr}.md`, md, 'text/markdown;charset=utf-8')
    consoleLogs.value.push({ text: `[Аудит] Отчет сохранен в формате Markdown (.md)`, color: 'text-sage-text' })
  } else {
    const html = generateAuditHtml(data)
    openPrintableReport(html)
    consoleLogs.value.push({ text: `[Аудит] Сформирован бланк для печати и сохранения в PDF`, color: 'text-sage-text' })
  }
}

const isExportingImage = ref(false)

async function handleExportGraphImage(format: 'png' | 'jpeg') {
  auditModalOpen.value = false
  isExportingImage.value = true
  try {
    await triggerGraphExport(format, currentMainFile.value || 'JavaBlueprint', (msg, color) => {
      consoleLogs.value.push({ text: msg, color: color || 'text-silver-300' })
    })
  } finally {
    isExportingImage.value = false
  }
}
const savedBottomPanelHeight = ref(300)
const activeGraphPreset = ref<number | null>(null)

function setPanelHeight(height: number) { bottomPanelHeight.value = Math.round(Math.max(40, Math.min(maxPanelHeight.value, height))) }

function toggleMaximizeGraph() {
  if (isGraphMaximized.value) {
    setPanelHeight(savedBottomPanelHeight.value || 300)
    isGraphMaximized.value = false
    activeGraphPreset.value = null
  } else {
    savedBottomPanelHeight.value = bottomPanelHeight.value > 80 ? bottomPanelHeight.value : 300
    setPanelHeight(40)
    isGraphMaximized.value = true
    activeGraphPreset.value = 100
  }
  nextTick(() => {
    setTimeout(() => {
      focusGraph()
    }, 60)
  })
}

function setGraphHeightPreset(percent: number) {
  isGraphMaximized.value = false
  activeGraphPreset.value = percent
  const totalH = workspaceElement.value?.clientHeight || 700
  const bottomH = Math.round(totalH * (1 - percent / 100))
  setPanelHeight(bottomH)
  nextTick(() => {
    setTimeout(() => {
      focusGraph()
    }, 60)
  })
}

function autoAdaptGraphSize() {
  if (!workspaceElement.value || isGraphMaximized.value || activeGraphPreset.value !== null) return
  const totalH = workspaceElement.value.clientHeight || 700
  const nodeCount = graphNodes.value.length

  let targetBottomH: number
  if (activeMethodTab.value === '__overview__' || nodeCount > 25) {
    targetBottomH = Math.round(totalH * 0.25)
  } else if (nodeCount > 10) {
    targetBottomH = Math.round(totalH * 0.35)
  } else {
    targetBottomH = Math.round(totalH * 0.50)
  }
  setPanelHeight(targetBottomH)
}
function resizePanel(event: PointerEvent) { setPanelHeight(resizeHeight + resizeOrigin - event.clientY) }
function stopPanelResize() {
  resizeTarget?.removeEventListener('pointermove', resizePanel)
  resizeTarget?.removeEventListener('pointerup', stopPanelResize)
  resizeTarget?.removeEventListener('pointercancel', stopPanelResize)
  resizeTarget?.removeEventListener('lostpointercapture', stopPanelResize)
  resizeTarget = null
}
function startPanelResize(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  activeGraphPreset.value = null
  isGraphMaximized.value = false
  stopPanelResize()
  resizeTarget = event.currentTarget as HTMLElement
  resizeOrigin = event.clientY
  resizeHeight = bottomPanelHeight.value
  resizeTarget.setPointerCapture(event.pointerId)
  resizeTarget.addEventListener('pointermove', resizePanel)
  resizeTarget.addEventListener('pointerup', stopPanelResize)
  resizeTarget.addEventListener('pointercancel', stopPanelResize)
  resizeTarget.addEventListener('lostpointercapture', stopPanelResize)
}
function initializeEditor() {
  if (!editorElement.value || editor) return
  monaco.editor.defineTheme('jetbrains-graphite', monacoTheme)
  editor = monaco.editor.create(editorElement.value, {
    language: 'java',
    theme: 'jetbrains-graphite',
    fontFamily: 'JetBrains Mono, Consolas, monospace',
    fontSize: 12,
    lineHeight: 20,
    minimap: { enabled: true },
    automaticLayout: true,
    padding: { top: 12, bottom: 12 },
    scrollBeyondLastLine: false,
  })
  editorSubscription = editor.onDidChangeModelContent(() => {
    if (suppressEditorSync || !currentFile.value) return
    projectFiles.value[currentFile.value] = editor?.getValue() || ''
    if (editorSyncTimer) clearTimeout(editorSyncTimer)
    editorSyncTimer = setTimeout(() => parseEditorContent(), 500)
  })
  syncEditorContent()
}

function syncEditorContent() {
  if (!editor) return
  const content = currentFileContent.value
  suppressEditorSync = true
  if (editor.getValue() !== content) editor.setValue(content)
  suppressEditorSync = false
}

async function parseEditorContent() {
  if (!currentFile.value || !projectLoaded.value || !editor) return
  try {
    const response = await fetch('http://localhost:8080/api/project/parse', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filePath: currentFile.value, content: editor.getValue() }),
    })
    if (!response.ok) return
    const result = await response.json()
    updateWorkspace(result)
  } catch (error) {
    consoleLogs.value.push({ text: `[Editor] Parse failed: ${error instanceof Error ? error.message : String(error)}`, color: 'text-[#b58585]' })
  }
}

function highlightEditorRange(startLine: number, endLine = startLine) {
  if (!editor || !startLine) return
  editorDecorationIds = editor.deltaDecorations(editorDecorationIds, [{
    range: new monaco.Range(startLine, 1, Math.max(startLine, endLine), 1),
    options: { isWholeLine: true, className: 'editor-line-highlight', overviewRuler: { color: '#cfc4b4', position: monaco.editor.OverviewRulerLane.Full } },
  }])
  editor.revealLinesInCenter(startLine, Math.max(startLine, endLine))
  editor.setPosition({ lineNumber: startLine, column: 1 })
  editor.focus()
}

function focusGraph() {
  void fitView({ padding: 0.16, minZoom: 0.15, maxZoom: 1.2, duration: 350, includeHiddenNodes: false })
}

function expandAllDirectories() {
  const directories = new Set<string>()
  Object.keys(projectFiles.value).forEach((filePath) => {
    const parts = filePath.replaceAll('\\', '/').split('/').filter(Boolean)
    for (let index = 1; index < parts.length; index += 1) directories.add(parts.slice(0, index).join('/'))
  })
  expandedDirectories.value = directories
}

function collapseAllDirectories() {
  expandedDirectories.value = new Set()
}

function showFullFile() {
  selectedGraphNode.value = null
  editorDecorationIds = editor?.deltaDecorations(editorDecorationIds, []) || []
  if (editor) {
    editor.setScrollPosition({ scrollTop: 0 })
    editor.setPosition({ lineNumber: Math.max(1, cursorLine.value), column: 1 })
    editor.focus()
  }
  removeSelectedElements()
  graphNodes.value = graphNodes.value.map((node) => ({ ...node, selected: false }))
}

function scheduleGraphFit() {
  if (activeGraphPreset.value === null && !isGraphMaximized.value) {
    autoAdaptGraphSize()
  }
  nextTick(() => {
    setTimeout(() => {
      focusGraph()
    }, 60)
  })
}

function updateWorkspace(result: any) {
  projectFiles.value = result.project.files
  currentMainFile.value = result.project.mainFile
  if (!diffFile.value) diffFile.value = result.project.mainFile
  analysisReport.value = result.analysis
  setRawGraph(result.graph, true)
  nextTick(() => {
    syncEditorContent()
  })
}

async function addPaletteBlock(code: string) {
    if (editor && editor.hasTextFocus()) {
    const selection = editor.getSelection()
    if (selection) {
      editor.executeEdits('palette', [{ range: selection, text: code, forceMoveMarkers: true }])
      editor.focus()
      return
    }
  }
  const target = selectedGraphNode.value?.id || graphNodes.value.find(node => node.data?.type === 'METHOD')?.id
  if (!target) {
    consoleLogs.value.push({ text: '[Palette] Select a method node or place the cursor in the editor', color: 'text-[#b58585]' })
    return
  }
  try {
    const response = await fetch('http://localhost:8080/api/graph/mutate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filePath: currentFile.value, nodeId: target, operation: 'ADD', newCode: code }),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    updateWorkspace(await response.json())
  } catch (error) {
    consoleLogs.value.push({ text: `[Palette] Failed: ${error instanceof Error ? error.message : String(error)}`, color: 'text-[#b58585]' })
  }
}

async function exportProject() {
  try {
    const response = await fetch('http://localhost:8080/api/project/export')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'java-blueprints-refactored.zip'
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    consoleLogs.value.push({ text: `[Export] Failed: ${error instanceof Error ? error.message : String(error)}`, color: 'text-[#b58585]' })
  }
}

async function runCustomQualityTest() {
  if (!projectLoaded.value || isProcessing.value) return
  isProcessing.value = true
  try {
    const response = await fetch('http://localhost:8080/api/project/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ inputs: [customInput.value] }),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    verificationResult.value = await response.json()
  } catch (error) {
    consoleLogs.value.push({ text: `[QualityGate] Custom test failed: ${error instanceof Error ? error.message : String(error)}`, color: 'text-[#b58585]' })
  } finally {
    isProcessing.value = false
  }
}

function handleConnect(params: any) {
  const newEdge = {
    id: `edge_${params.source}_${params.target}_${Date.now()}`,
    source: params.source,
    target: params.target,
    animated: true,
    style: { stroke: '#88c0d0', strokeWidth: 2 }
  }
  graphEdges.value.push(newEdge)
  consoleLogs.value.push({
    text: `[Graph Flow] Создана визуальная связь: ${params.source} ➔ ${params.target}`,
    color: 'text-silver-brushed'
  })
}

async function updateSelectedNode() {
  if (!selectedGraphNode.value || isProcessing.value) return
  const nodeId = selectedGraphNode.value.id
  isProcessing.value = true
  try {
    const res = await mutateGraph({
      filePath: currentFile.value,
      nodeId,
      operation: 'UPDATE',
      newCode: editedNodeCode.value
    })
    updateWorkspace(res)
    consoleLogs.value.push({
      text: `[Graph Sync] Блок ${nodeId} успешно обновлен в коде Java`,
      color: 'text-sage-text'
    })
  } catch (err: any) {
    consoleLogs.value.push({
      text: `[Graph Sync Error] Не удалось обновить блок: ${err.message || err}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}

async function deleteSelectedNode() {
  if (!selectedGraphNode.value || isProcessing.value) return
  const nodeId = selectedGraphNode.value.id
  isProcessing.value = true
  try {
    const res = await mutateGraph({
      filePath: currentFile.value,
      nodeId,
      operation: 'DELETE'
    })
    selectedGraphNode.value = null
    updateWorkspace(res)
    consoleLogs.value.push({
      text: `[Graph Sync] Блок ${nodeId} удален из AST и кода`,
      color: 'text-sage-text'
    })
  } catch (err: any) {
    consoleLogs.value.push({
      text: `[Graph Sync Error] Не удалось удалить блок: ${err.message || err}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}

function handleGlobalKeydown(e: KeyboardEvent) {
  if ((e.key === 'Delete' || e.key === 'Backspace') && selectedGraphNode.value) {
    const active = document.activeElement
    if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.closest('.monaco-editor'))) {
      return
    }
    e.preventDefault()
    void deleteSelectedNode()
  }
}

onMounted(() => {
  initializeEditor()
  window.addEventListener('keydown', handleGlobalKeydown)
  resizeObserver = new ResizeObserver(() => {
    maxPanelHeight.value = Math.max(120, (workspaceElement.value?.clientHeight || 600) - 180)
    setPanelHeight(bottomPanelHeight.value)
  })
  if (workspaceElement.value) resizeObserver.observe(workspaceElement.value)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  stopPanelResize()
  resizeObserver?.disconnect()
  if (editorSyncTimer) clearTimeout(editorSyncTimer)
  editorSubscription?.dispose()
  editor?.dispose()
})

watch(selectedGraphNode, (node) => {
  if (node) {
    editedNodeCode.value = node.code || ''
  }
})

watch(currentFile, () => nextTick(syncEditorContent))
watch(activeBottomTab, (tab) => {
  if (tab === 'code') nextTick(() => {
    initializeEditor()
    editor?.layout()
    syncEditorContent()
  })
})
function openMainFile() {
  if (!projectLoaded.value) return
  selectFile(currentMainFile.value)
  selectedGraphNode.value = null
  bottomPanelVisible.value = true
  activeBottomTab.value = 'code'
}
// Methods
function toggleLeftTab(tab: 'project' | 'analysis' | 'palette') {
  leftTab.value = leftTab.value === tab ? null : tab
}

function handleProjectLoaded(result: any) {
  optimizationChanges.value = null
  verificationResult.value = null
  optimizationError.value = null
  pendingOptimizationResult.value = null
  selectedGraphNode.value = null
  consoleLogs.value = []
  projectFiles.value = result.project.files
  originalFiles.value = { ...result.project.files }
  diffFile.value = result.project.mainFile
  currentMainFile.value = result.project.mainFile
  currentFile.value = result.project.mainFile
  selectedOptimizationIds.value = new Set(
    (result.analysis?.recommendations || []).map((r: any, idx: number) => r.defectId || `recommendation-${idx}`)
  )

  analysisReport.value = result.analysis

  setRawGraph(result.graph, true)
  selectedDefect.value = null
  expandedDirectories.value = new Set(
    Object.keys(projectFiles.value)
      .map((path) => path.replaceAll('\\', '/'))
      .flatMap((path) => {
        const parts = path.split('/').filter(Boolean)
        return parts.slice(1, -1).map((_, index) => parts.slice(0, index + 1).join('/'))
      })
  )

  projectLoaded.value = true
  consoleLogs.value.push({
    text: `[Import] Loaded ${Object.keys(projectFiles.value).length} file(s)`,
    color: 'text-sage-text'
  })
}

function toggleDirectory(path: string) {
  const next = new Set(expandedDirectories.value)
  if (next.has(path)) {
    next.delete(path)
  } else {
    next.add(path)
  }
  expandedDirectories.value = next
}

function handleImportError(message: string) {
  consoleLogs.value.push({
    text: `[Error] ${message}`,
    color: 'text-[#b58585]'
  })
}

function selectFile(filename: string) {
  currentFile.value = filename
  selectedGraphNode.value = null
  editorDecorationIds = editor?.deltaDecorations(editorDecorationIds, []) || []
  bottomPanelVisible.value = true
  activeBottomTab.value = 'code'
  nextTick(() => {
    initializeEditor()
    editor?.layout()
    syncEditorContent()
    void parseEditorContent()
  })
  consoleLogs.value.push({
    text: `[Editor] Opened ${filename}`,
    color: 'text-silver-400'
  })
}

function handleNodeClick(event: any) {
  const node = event?.node || event
  const nodeData = event?.node?.data || event?.data || {}
    selectedGraphNode.value = {
      id: node?.id,
      label: nodeData.label || 'Выбранный элемент',
      type: nodeData.type || nodeData.sublabel || 'STATEMENT',
      code: nodeData.code || '',
      filePath: nodeData.filePath,
      line: nodeData.line,
    }
    graphNodes.value = graphNodes.value.map((candidate) => ({ ...candidate, selected: candidate.id === node?.id }))
  if (nodeData.filePath) {
    const matchingFile = Object.keys(projectFiles.value).find(
      (filePath) => filePath.replaceAll('\\', '/') === nodeData.filePath.replaceAll('\\', '/')
    )
    if (matchingFile) {
      currentFile.value = matchingFile
    }
  }
  if (nodeData.line) {
    cursorLine.value = nodeData.line
    nextTick(() => highlightEditorRange(nodeData.line, nodeData.line))
  }

  // Toggle method tree downwards on click
  if (nodeData.isMethod || nodeData.childCount) {
    toggleMethodExpand(node?.id)
  }

  bottomPanelVisible.value = true
  activeBottomTab.value = 'code'
}

function selectGraphNodeAt(location: any) {
  const node = graphNodes.value.find((candidate) => {
    const data = candidate.data || {}
    return data.filePath?.replaceAll('\\', '/') === location.filePath?.replaceAll('\\', '/') && data.line >= location.startLine && data.line <= location.endLine
  })
  if (!node) return
  selectedGraphNode.value = { id: node.id, label: node.data.label, code: node.data.code, filePath: node.data.filePath, line: node.data.line }
  graphNodes.value = graphNodes.value.map((candidate) => ({ ...candidate, selected: candidate.id === node.id, data: { ...candidate.data, pulse: candidate.id === node.id ? Date.now() : undefined } }))
  const center = { x: node.position.x + 100, y: node.position.y + 40 }
  void setCenter(center.x, center.y, { zoom: 1.1, duration: 550 })
}

function jumpToDefect(defect: any) {
  selectedDefect.value = defect
  const matchingFile = Object.keys(projectFiles.value).find((filePath) => filePath.replaceAll('\\', '/') === defect.location.filePath.replaceAll('\\', '/'))
  if (matchingFile) currentFile.value = matchingFile
  cursorLine.value = defect.location.startLine
  bottomPanelVisible.value = true
  activeBottomTab.value = 'code'
  nextTick(() => {
    initializeEditor()
    editor?.layout()
    syncEditorContent()
    highlightEditorRange(defect.location.startLine, defect.location.endLine)
    selectGraphNodeAt(defect.location)
  })
  consoleLogs.value.push({
    text: `[Defect] ${defect.title} at ${defect.location.filePath}:${defect.location.startLine}`,
    color: 'text-titanium-text'
  })
}

async function optimizeCode() {
  if (!projectLoaded.value || isProcessing.value) return
  verificationResult.value = null
  optimizationChanges.value = null
  optimizationError.value = null
  pendingOptimizationResult.value = null

  isProcessing.value = true
  bottomPanelVisible.value = true
  activeBottomTab.value = 'console'
  selectedGraphNode.value = null
  consoleLogs.value.push({
    text: '[Optimize] Оптимизация AST-правилами (устранение O(n²), StringBuilder)...',
    color: 'text-silver-300'
  })

  try {
    const result = await optimizeProject({
      applyOptimizations: true,
      decomposeMonolith: false
    })

    pendingOptimizationResult.value = result
    diffFile.value = result.after.project.mainFile
    activeBottomTab.value = 'diff'

    consoleLogs.value.push({
      text: result.changes.length > 0
        ? `[Optimize] Подготовлено ${result.changes.length} оптимизаций. Открыт Diff для проверки.`
        : '[Optimize] Безопасных изменений не найдено',
      color: result.changes.length > 0 ? 'text-sage-text' : 'text-silver-300'
    })

    if (result.verification) {
      consoleLogs.value.push({
        text: result.verification.compilationSuccess
          ? `[QualityGate] Компиляция JDK 21 пройдена; поведение сохранено: ${result.verification.behaviorPreserved ? 'да' : 'нет'}; расчетный прирост: ${result.verification.speedupFactor.toFixed(1)}x`
          : `[QualityGate] Ошибка компиляции: ${result.verification.originalErrors || result.verification.optimizedErrors}`,
        color: result.verification.compilationSuccess ? 'text-sage-text' : 'text-[#b58585]'
      })
    }
  } catch (error: any) {
    optimizationError.value = error.message || String(error)
    activeBottomTab.value = 'optimization'
    consoleLogs.value.push({
      text: `[Optimize Error] ${error.message || error}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}

async function runDecomposition() {
  if (!projectLoaded.value || isProcessing.value) return
  verificationResult.value = null
  optimizationChanges.value = null
  optimizationError.value = null
  pendingOptimizationResult.value = null

  isProcessing.value = true
  bottomPanelVisible.value = true
  activeBottomTab.value = 'console'
  selectedGraphNode.value = null
  consoleLogs.value.push({
    text: '[Decomposer] Запуск архитектурной декомпозиции монолита на модули...',
    color: 'text-silver-300'
  })

  try {
    const result = await optimizeProject({
      applyOptimizations: true,
      decomposeMonolith: true
    })

    pendingOptimizationResult.value = result
    diffFile.value = result.after.project.mainFile
    activeBottomTab.value = 'diff'

    const moduleCount = Object.keys(result.after.project.files).length
    consoleLogs.value.push({
      text: `[Decomposer] Монолит успешно разделен на ${moduleCount} модулей: ${Object.keys(result.after.project.files).join(', ')}`,
      color: 'text-sage-text'
    })

    if (result.verification) {
      consoleLogs.value.push({
        text: result.verification.compilationSuccess
          ? `[QualityGate] Модульная сборка JDK 21 успешна; поведение сохранено: ${result.verification.behaviorPreserved ? 'да' : 'нет'}; расчетный прирост: ${result.verification.speedupFactor.toFixed(1)}x`
          : `[QualityGate] Ошибка компиляции: ${result.verification.originalErrors || result.verification.optimizedErrors}`,
        color: result.verification.compilationSuccess ? 'text-sage-text' : 'text-[#b58585]'
      })
    }
  } catch (error: any) {
    optimizationError.value = error.message || String(error)
    activeBottomTab.value = 'optimization'
    consoleLogs.value.push({
      text: `[Decomposer Error] ${error.message || error}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}

const handleLoadDemoMonolith = async () => {
  isProcessing.value = true
  bottomPanelVisible.value = true
  activeBottomTab.value = 'console'
  consoleLogs.value.push({
    text: '[Demo] Загрузка эталонного монолита (~1000 строк, 43 КБ)...',
    color: 'text-silver-300'
  })
  try {
    const result = await loadDemoProject()
    handleProjectLoaded(result)
    consoleLogs.value.push({
      text: `[Demo] Монолит загружен: ${result.graph.nodes.length} узлов CFG, найдено ${result.analysis.defects.length} дефектов. Режим Unreal-вкладок активен.`,
      color: 'text-sage-text'
    })
  } catch (error: any) {
    consoleLogs.value.push({
      text: `[Demo Error] Ошибка загрузки демо-монолита: ${error.message || error}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}

;(window as any).handleLoadDemoMonolith = handleLoadDemoMonolith

async function optimizeCodeWithAi() {
  if (!projectLoaded.value || isProcessing.value) return
  verificationResult.value = null
  optimizationChanges.value = null
  optimizationError.value = null
  pendingOptimizationResult.value = null

  isProcessing.value = true
  bottomPanelVisible.value = true
  activeBottomTab.value = 'console'
  selectedGraphNode.value = null
  consoleLogs.value.push({
    text: '[AI Optimizer] Отправка запроса в Gemini AI (Google AI Studio Cloudflare Relay)...',
    color: 'text-silver-300'
  })

  try {
    const result = await optimizeProjectWithAi()

    // Store candidate for review instead of silently overwriting workspace
    pendingOptimizationResult.value = result
    diffFile.value = result.after.project.mainFile
    activeBottomTab.value = 'diff'

    consoleLogs.value.push({
      text: result.changes.length > 0
        ? `[AI Optimizer] Gemini + AST подготовили ${result.changes.length} оптимизаций. Открыт Diff. Нажмите «Применить в проект» (зеленая кнопка) для записи нового кода и пересчета графа/дефектов.`
        : '[AI Optimizer] Оптимизация готова. Нажмите «Применить в проект» для обновления.',
      color: 'text-sage-text'
    })

    if (result.verification) {
      consoleLogs.value.push({
        text: result.verification.compilationSuccess
          ? `[Verify] Компиляция JDK 21 пройдена; поведение сохранено: ${result.verification.behaviorPreserved ? 'да' : 'нет'}; расчетный прирост: ${result.verification.speedupFactor.toFixed(1)}x`
          : `[Verify] Ошибка компиляции: ${result.verification.originalErrors || result.verification.optimizedErrors}`,
        color: result.verification.compilationSuccess ? 'text-sage-text' : 'text-[#b58585]'
      })
    }
  } catch (error: any) {
    optimizationError.value = error.message || String(error)
    activeBottomTab.value = 'optimization'
    consoleLogs.value.push({
      text: `[AI Optimizer Error] ${error.message || error}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}

async function applyPendingOptimization() {
  if (!pendingOptimizationResult.value) return
  const result = pendingOptimizationResult.value

  isProcessing.value = true
  try {
    const updated = await applyOptimization({
      files: result.after.project.files,
      mainFile: result.after.project.mainFile
    })

    const previousDefectsCount = analysisReport.value?.defects?.length ?? (result.before?.analysis?.defects?.length ?? 0)
    projectFiles.value = { ...updated.project.files }
    diffFile.value = updated.project.mainFile
    currentMainFile.value = updated.project.mainFile
    currentFile.value = updated.project.mainFile
    analysisReport.value = updated.analysis
    setRawGraph(updated.graph, false)

    if (editor) {
      suppressEditorSync = true
      const newMainContent = updated.project.files[updated.project.mainFile] || ''
      if (editor.getValue() !== newMainContent) {
        editor.setValue(newMainContent)
      }
      suppressEditorSync = false
    }

    if (selectedDefect.value && !updated.analysis.defects.some((d: any) => d.id === selectedDefect.value?.id)) {
      selectedDefect.value = null
    }

    const newDefectsCount = updated.analysis.defects.length
    const fixedCount = Math.max(0, previousDefectsCount - newDefectsCount)

    optimizationChanges.value = result.changes
    verificationResult.value = result.verification ?? null
    pendingOptimizationResult.value = null

    consoleLogs.value.push({
      text: `[Workspace] Оптимизация успешно применена: устранено ${fixedCount} дефектов, осталось в коде: ${newDefectsCount}. Граф и редактор обновлены.`,
      color: 'text-sage-text'
    })
  } catch (error: any) {
    consoleLogs.value.push({
      text: `[Workspace] Ошибка применения изменений: ${error.message || error}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}

function discardPendingOptimization() {
  pendingOptimizationResult.value = null
  consoleLogs.value.push({
    text: '[Workspace] Предложенные AI изменения отклонены',
    color: 'text-silver-dark'
  })
}

async function runProjectCode() {
  if (!projectLoaded.value || isProcessing.value) return

  isProcessing.value = true
  bottomPanelVisible.value = true
  activeBottomTab.value = 'console'
  consoleLogs.value.push({
    text: '[Run] Сборка и запуск через Local JDK 21...',
    color: 'text-silver-300'
  })

  try {
    const response = await fetch('http://localhost:8080/api/project/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ input: customInput.value || '' })
    })
    const result = await response.json()

    if (!response.ok) {
      throw new Error(result.details || result.error || `HTTP ${response.status}`)
    }

    if (result.compilationSuccess) {
      if (result.output) {
        result.output.split(/\r?\n/).filter(Boolean).forEach((line: string) => {
          consoleLogs.value.push({ text: line, color: 'text-silver-brushed' })
        })
      }
      consoleLogs.value.push({
        text: `[Run] ${result.mainClass} завершился с exit code ${result.exitCode}`,
        color: result.exitCode === 0 ? 'text-sage-text' : 'text-[#b58585]'
      })
    } else {
      consoleLogs.value.push({
        text: `[Run] ${result.errors || 'Ошибка сборки или выполнения'}`,
        color: 'text-[#b58585]'
      })
    }
  } catch (error) {
    consoleLogs.value.push({
      text: `[Run] Ошибка: ${error instanceof Error ? error.message : String(error)}`,
      color: 'text-[#b58585]'
    })
  } finally {
    isProcessing.value = false
  }
}
const runOptimization = runProjectCode
</script>
















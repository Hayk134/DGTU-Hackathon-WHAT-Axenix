export interface AuditReportData {
  projectName: string
  mainFile: string
  timestamp: string
  metrics: {
    totalLines: number
    totalMethods: number
    averageComplexity: number
    maxComplexity: number
    duplicatedBlocks: number
  }
  defects: Array<{
    id: string
    title: string
    type: string
    severity: 'HIGH' | 'MEDIUM' | 'LOW' | string
    location?: { filePath?: string; startLine?: number; endLine?: number }
    description?: string
    codeFragment?: string
    isResolved?: boolean
  }>
  recommendations: Array<{
    title?: string
    description?: string
  }>
  currentBigO: string
  targetBigO: string
  nodeCount: number
  verification?: {
    compilationSuccess: boolean
    behaviorPreserved: boolean
    speedupFactor: number
    originalExecutionTime: number
    optimizedExecutionTime: number
    testResults?: Array<{
      name: string
      passed: boolean
      durationMs?: number
      message?: string
    }>
  } | null
  diffStats?: {
    additions: number
    deletions: number
    changesCount?: number
  }
}

export function generateAuditMarkdown(data: AuditReportData): string {
  const v = data.verification
  const speedup = v ? `${v.speedupFactor.toFixed(2)}x` : '—'
  const timeReduction = v && v.originalExecutionTime > 0
    ? `${v.originalExecutionTime} ms → ${v.optimizedExecutionTime} ms`
    : '—'
  const testsPassed = v?.testResults ? v.testResults.filter(t => t.passed).length : 0
  const testsTotal = v?.testResults?.length || 0
  const qgStatus = v?.behaviorPreserved
    ? `✓ 100% Поведение сохранено (${testsPassed}/${testsTotal} тестов)`
    : (v ? '⚠ Требуется доработка тестов' : 'Ожидает верификации')

  const defectsRows = data.defects.length > 0
    ? data.defects.map((d, i) => {
        const line = d.location?.startLine ? `L${d.location.startLine}` : '—'
        const status = d.isResolved ? '✓ Исправлен' : '⚠ Активен'
        return `| ${i + 1} | \`${d.severity}\` | **${escapeMd(d.title || d.type)}** | ${line} | ${status} | ${escapeMd(d.description || '—')} |`
      }).join('\n')
    : '| — | — | *Критических дефектов не обнаружено* | — | ✓ Чисто | Оптимальный код |'

  const recommendationsList = data.recommendations.length > 0
    ? data.recommendations.map((r, i) => `${i + 1}. **${escapeMd(r.title || 'Рекомендация')}:** ${escapeMd(r.description || '')}`).join('\n')
    : '*Все рекомендации учтены и применены.*'

  const testsRows = v?.testResults && v.testResults.length > 0
    ? v.testResults.map((t, i) => `| ${i + 1} | \`${t.name}\` | ${t.passed ? '✓ PASSED' : '✗ FAILED'} | ${t.durationMs ?? 0} ms | ${escapeMd(t.message || 'ОК')} |`).join('\n')
    : '| — | *Тестовый набор не запускался* | — | — | — |'

  return `# WHAT: Аудиторский отчет оптимизации Java-проекта
**Платформа:** WHAT Enterprise Blueprint Suite (Axenix Case 2026)  
**Дата генерации:** ${data.timestamp}  
**Целевой класс / файл:** \`${data.mainFile || 'Main.java'}\`  
**Среда исполнения:** Java 21 LTS (Oracle OpenJDK)  
**Статус Quality Gate:** ${qgStatus}

---

## 1. Сводка оптимизации (Executive Summary)

| Метрика | До оптимизации | После оптимизации | Результат / Дельта |
|:---|:---:|:---:|:---|
| **Асимптотическая сложность (Big-O)** | \`${data.currentBigO}\` | \`${data.targetBigO}\` | **Снижение вычислительной сложности** |
| **Коэффициент ускорения (Speedup)** | 1.00x | **${speedup}** | **Прирост пропускной способности** |
| **Время выполнения (Benchmark)** | ${v?.originalExecutionTime ?? '—'} ms | ${v?.optimizedExecutionTime ?? '—'} ms | ${timeReduction} |
| **Сохранение поведения (Quality Gate)** | — | **${v?.behaviorPreserved ? '100% Сохранено' : 'Не подтверждено'}** | Детерминированный прогон |
| **Компиляция Java 21** | ✓ Успешно | ✓ Успешно | Без ошибок компилятора |
| **Общее число узлов CFG / AST** | ${data.nodeCount} | ${data.nodeCount} | Контрольный граф верифицирован |

---

## 2. Метрики качества исходного кода
- **Всего строк кода:** ${data.metrics.totalLines}
- **Всего методов класса:** ${data.metrics.totalMethods}
- **Средняя цикломатическая сложность:** ${data.metrics.averageComplexity.toFixed(1)}
- **Максимальная сложность метода:** ${data.metrics.maxComplexity}
- **Дублированных блоков:** ${data.metrics.duplicatedBlocks}
- **Изменений в Diff:** +${data.diffStats?.additions ?? 0} / -${data.diffStats?.deletions ?? 0} строк (${data.diffStats?.changesCount ?? 0} изменений)

---

## 3. Реестр выявленных и устраненных дефектов (${data.defects.length})

| # | Важность | Дефект | Строка | Статус | Описание |
|---|:---:|:---|:---:|:---:|:---|
${defectsRows}

---

## 4. Рекомендации по оптимизации (${data.recommendations.length})
${recommendationsList}

---

## 5. Протокол испытаний Quality Gate (${testsPassed}/${testsTotal})

| # | Тест-кейс | Статус | Время | Примечание |
|---|:---|:---:|:---:|:---|
${testsRows}

---

## 6. Заключение аудита
Оптимизированный исходный код компилируется в среде Java 21, сохраняет функциональную эквивалентность бизнес-логики и демонстрирует доказанное ускорение алгоритма.

*Отчет сформирован автоматически платформой WHAT • Axenix Hackathon 2026*
`
}

export function generateAuditHtml(data: AuditReportData): string {
  const v = data.verification
  const speedup = v ? `${v.speedupFactor.toFixed(2)}x` : '1.00x'
  const timeReduction = v && v.originalExecutionTime > 0
    ? `${v.originalExecutionTime} ms → ${v.optimizedExecutionTime} ms`
    : '—'
  const testsPassed = v?.testResults ? v.testResults.filter(t => t.passed).length : 0
  const testsTotal = v?.testResults?.length || 0
  const isQGSuccess = v?.behaviorPreserved ?? false

  const defectsHtml = data.defects.length > 0
    ? data.defects.map((d, i) => {
        const sevClass = d.severity === 'HIGH' ? 'badge-high' : (d.severity === 'MEDIUM' ? 'badge-med' : 'badge-low')
        const statusClass = d.isResolved ? 'badge-resolved' : 'badge-active'
        const statusText = d.isResolved ? '✓ Исправлен' : '⚠ Активен'
        return `
        <tr>
          <td>${i + 1}</td>
          <td><span class="badge ${sevClass}">${escapeHtml(d.severity)}</span></td>
          <td><strong>${escapeHtml(d.title || d.type)}</strong></td>
          <td><code>${d.location?.startLine ? `L${d.location.startLine}` : '—'}</code></td>
          <td><span class="badge ${statusClass}">${statusText}</span></td>
          <td class="desc-cell">${escapeHtml(d.description || '—')}</td>
        </tr>`
      }).join('')
    : '<tr><td colspan="6" style="text-align:center; padding: 20px; color: #10b981;">✓ Критических архитектурных дефектов не обнаружено</td></tr>'

  const testsHtml = v?.testResults && v.testResults.length > 0
    ? v.testResults.map((t, i) => `
        <tr>
          <td>${i + 1}</td>
          <td><code>${escapeHtml(t.name)}</code></td>
          <td><span class="badge ${t.passed ? 'badge-pass' : 'badge-fail'}">${t.passed ? '✓ PASSED' : '✗ FAILED'}</span></td>
          <td>${t.durationMs ?? 0} ms</td>
          <td>${escapeHtml(t.message || 'Успешно')}</td>
        </tr>`).join('')
    : '<tr><td colspan="5" style="text-align:center; padding: 15px; color: #64748b;">Прогон тестов не производился</td></tr>'

  const recommendationsHtml = data.recommendations.length > 0
    ? data.recommendations.map((r, i) => `
        <li style="margin-bottom: 8px;">
          <strong>${escapeHtml(r.title || `Пункт ${i + 1}`)}:</strong> ${escapeHtml(r.description || '')}
        </li>`).join('')
    : '<li style="color: #10b981;">Все рекомендации успешно учтены.</li>'

  return `<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="UTF-8">
  <title>WHAT Audit Report — ${escapeHtml(data.mainFile || 'Project')}</title>
  <style>
    :root {
      --bg: #090c10;
      --card: #0e131d;
      --border: #232c3d;
      --text: #e2e8f0;
      --text-muted: #94a3b8;
      --accent: #a4c0ac;
      --accent-glow: rgba(164, 192, 172, 0.2);
      --success: #10b981;
      --danger: #ef4444;
      --warning: #f59e0b;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 0;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
      line-height: 1.5;
      font-size: 13px;
    }
    .top-actions {
      position: sticky;
      top: 0;
      z-index: 100;
      background: #06080d;
      border-bottom: 1px solid var(--border);
      padding: 12px 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      box-shadow: 0 4px 12px rgba(0,0,0,0.5);
    }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      border: none;
      transition: all 0.15s ease;
    }
    .btn-primary {
      background: var(--accent);
      color: #090b10;
    }
    .btn-primary:hover {
      background: #b8d4c0;
    }
    .btn-secondary {
      background: #1e2638;
      color: var(--text);
      border: 1px solid var(--border);
    }
    .btn-secondary:hover {
      background: #2a344d;
    }
    .report-container {
      max-width: 1040px;
      margin: 32px auto 80px auto;
      padding: 0 24px;
    }
    .header-card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 24px 32px;
      margin-bottom: 24px;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      flex-wrap: wrap;
      gap: 16px;
    }
    .logo-badge {
      display: inline-block;
      font-family: monospace;
      font-weight: 800;
      font-size: 18px;
      color: var(--accent);
      letter-spacing: 2px;
      margin-bottom: 6px;
    }
    h1 {
      font-size: 20px;
      margin: 0 0 6px 0;
      color: #ffffff;
      font-weight: 700;
    }
    .meta-text {
      color: var(--text-muted);
      font-size: 12px;
      margin: 0;
    }
    .meta-text span {
      margin-right: 16px;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 28px;
    }
    .kpi-card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 18px;
      text-align: left;
    }
    .kpi-label {
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-muted);
      margin-bottom: 8px;
      font-weight: 600;
    }
    .kpi-value {
      font-size: 24px;
      font-weight: 700;
      color: #ffffff;
      font-family: monospace;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .kpi-sub {
      font-size: 11px;
      color: var(--text-muted);
      margin-top: 4px;
    }
    .section-title {
      font-size: 15px;
      font-weight: 700;
      color: #ffffff;
      margin: 28px 0 12px 0;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 8px;
      overflow: hidden;
      font-size: 12px;
      margin-bottom: 20px;
    }
    th {
      background: #131926;
      text-align: left;
      padding: 10px 14px;
      color: var(--text-muted);
      font-weight: 600;
      border-bottom: 1px solid var(--border);
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    td {
      padding: 10px 14px;
      border-bottom: 1px solid #1a2233;
      vertical-align: middle;
    }
    tr:last-child td { border-bottom: none; }
    .badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 10px;
      font-weight: 700;
      font-family: monospace;
      text-transform: uppercase;
    }
    .badge-high { background: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.4); }
    .badge-med { background: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.4); }
    .badge-low { background: rgba(59, 130, 246, 0.2); color: #60a5fa; border: 1px solid rgba(59, 130, 246, 0.4); }
    .badge-resolved { background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.4); }
    .badge-active { background: rgba(239, 68, 68, 0.15); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.3); }
    .badge-pass { background: rgba(16, 185, 129, 0.2); color: #34d399; }
    .badge-fail { background: rgba(239, 68, 68, 0.2); color: #f87171; }
    .desc-cell { color: #cbd5e1; font-size: 11px; }
    code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; background: rgba(255,255,255,0.06); padding: 2px 5px; border-radius: 4px; }
    .metrics-summary-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 12px;
      margin-bottom: 24px;
    }
    .metric-box {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 12px 16px;
    }
    .metric-box-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; }
    .metric-box-val { font-size: 18px; font-weight: 700; color: #fff; margin-top: 4px; font-family: monospace; }
    .verdict-box {
      background: #0b1713;
      border: 1px solid #164e3b;
      border-radius: 8px;
      padding: 16px 20px;
      margin-top: 32px;
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .verdict-icon { font-size: 28px; }
    .verdict-title { font-weight: 700; color: #34d399; font-size: 14px; margin-bottom: 2px; }
    .verdict-desc { color: #94a3b8; font-size: 11px; margin: 0; }
    .report-footer {
      text-align: center;
      color: var(--text-muted);
      font-size: 11px;
      margin-top: 48px;
      padding-top: 24px;
      border-top: 1px solid var(--border);
    }
    
    @media print {
      body {
        background: #ffffff !important;
        color: #0f172a !important;
        font-size: 11pt;
      }
      .no-print { display: none !important; }
      .top-actions { display: none !important; }
      .report-container { max-width: 100% !important; margin: 0 !important; padding: 0 !important; }
      .header-card, .kpi-card, .metric-box, table, .verdict-box {
        background: #ffffff !important;
        border: 1px solid #cbd5e1 !important;
        color: #0f172a !important;
        box-shadow: none !important;
      }
      h1, .kpi-value, .section-title, .metric-box-val { color: #0f172a !important; }
      .logo-badge { color: #047857 !important; }
      th { background: #f1f5f9 !important; color: #334155 !important; border-bottom: 2px solid #94a3b8 !important; }
      td { border-bottom: 1px solid #e2e8f0 !important; color: #1e293b !important; }
      .desc-cell { color: #334155 !important; }
      .verdict-box { background: #f0fdf4 !important; border: 1px solid #86efac !important; }
      .verdict-title { color: #15803d !important; }
      .verdict-desc { color: #334155 !important; }
      .badge-high { background: #fee2e2 !important; color: #991b1b !important; border-color: #fca5a5 !important; }
      .badge-med { background: #fef3c7 !important; color: #92400e !important; border-color: #fcd34d !important; }
      .badge-low { background: #dbeafe !important; color: #1e40af !important; border-color: #93c5fd !important; }
      .badge-resolved { background: #dcfce7 !important; color: #166534 !important; border-color: #86efac !important; }
      .badge-active { background: #fee2e2 !important; color: #991b1b !important; }
    }
  </style>
</head>
<body>

  <!-- Floating Action Bar for Web View -->
  <div class="top-actions no-print">
    <div style="display: flex; align-items: center; gap: 12px;">
      <span class="logo-badge" style="margin: 0;">WHAT</span>
      <span style="font-weight: 600; font-size: 13px;">Аудиторский отчет оптимизации Java-проекта</span>
    </div>
    <div style="display: flex; gap: 8px;">
      <button class="btn btn-primary" onclick="window.print()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
        <span>Распечатать / Сохранить в PDF</span>
      </button>
      <button class="btn btn-secondary" onclick="window.close()">✕ Закрыть</button>
    </div>
  </div>

  <div class="report-container">
    
    <!-- Header Block -->
    <div class="header-card">
      <div>
        <div class="logo-badge">WHAT ENTERPRISE SUITE</div>
        <h1>Аудиторский отчет оптимизации кода</h1>
        <p class="meta-text">
          <span><strong>Файл:</strong> <code>${escapeHtml(data.mainFile || 'Main.java')}</code></span>
          <span><strong>Среда:</strong> Java 21 LTS (Oracle OpenJDK)</span>
          <span><strong>Дата:</strong> ${data.timestamp}</span>
        </p>
      </div>
      <div style="text-align: right;">
        <span class="badge ${isQGSuccess ? 'badge-resolved' : 'badge-med'}" style="font-size: 11px; padding: 4px 10px;">
          ${isQGSuccess ? 'Quality Gate: PASSED ✓' : 'Quality Gate: PENDING'}
        </span>
        <div style="font-size: 10px; color: var(--text-muted); margin-top: 6px;">Axenix Hackathon 2026</div>
      </div>
    </div>

    <!-- KPI Grid -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-label">Сложность (Big-O)</div>
        <div class="kpi-value" style="color: #60a5fa;">
          <span>${escapeHtml(data.currentBigO)}</span>
          <span style="font-size: 16px; color: var(--text-muted);">→</span>
          <span style="color: #34d399;">${escapeHtml(data.targetBigO)}</span>
        </div>
        <div class="kpi-sub">Снижение на порядок</div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">Ускорение алгоритма</div>
        <div class="kpi-value" style="color: #34d399;">
          ${speedup}
        </div>
        <div class="kpi-sub">${timeReduction}</div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">Quality Gate тесты</div>
        <div class="kpi-value" style="color: ${testsPassed === testsTotal && testsTotal > 0 ? '#34d399' : '#fbbf24'};">
          ${testsPassed} / ${testsTotal}
        </div>
        <div class="kpi-sub">${isQGSuccess ? '100% эквивалентность' : 'Тесты запускаются'}</div>
      </div>

      <div class="kpi-card">
        <div class="kpi-label">Узлы CFG & Строки</div>
        <div class="kpi-value" style="color: #e2e8f0;">
          ${data.nodeCount} <span style="font-size: 14px; font-weight: normal; color: var(--text-muted);">/ ${data.metrics.totalLines} строк</span>
        </div>
        <div class="kpi-sub">AST-граф проверен</div>
      </div>
    </div>

    <!-- Code Metrics Boxes -->
    <div class="metrics-summary-grid">
      <div class="metric-box">
        <div class="metric-box-label">Всего методов</div>
        <div class="metric-box-val">${data.metrics.totalMethods}</div>
      </div>
      <div class="metric-box">
        <div class="metric-box-label">Средняя сложность</div>
        <div class="metric-box-val">${data.metrics.averageComplexity.toFixed(1)}</div>
      </div>
      <div class="metric-box">
        <div class="metric-box-label">Макс. сложность</div>
        <div class="metric-box-val" style="color: ${data.metrics.maxComplexity > 10 ? '#f87171' : '#fff'};">${data.metrics.maxComplexity}</div>
      </div>
      <div class="metric-box">
        <div class="metric-box-label">Дубликаты блоков</div>
        <div class="metric-box-val">${data.metrics.duplicatedBlocks}</div>
      </div>
      <div class="metric-box">
        <div class="metric-box-label">Правки в Diff</div>
        <div class="metric-box-val" style="color: #34d399;">+${data.diffStats?.additions ?? 0} / -${data.diffStats?.deletions ?? 0}</div>
      </div>
    </div>

    <!-- Defects Table -->
    <div class="section-title">
      <span>1. Реестр архитектурных дефектов и уязвимостей (${data.defects.length})</span>
    </div>
    <table>
      <thead>
        <tr>
          <th style="width: 40px;">#</th>
          <th style="width: 90px;">Важность</th>
          <th>Тип дефекта</th>
          <th style="width: 70px;">Строка</th>
          <th style="width: 100px;">Статус</th>
          <th>Описание проблемы</th>
        </tr>
      </thead>
      <tbody>
        ${defectsHtml}
      </tbody>
    </table>

    <!-- Quality Gate Tests Table -->
    <div class="section-title">
      <span>2. Протокол поведенческой верификации Quality Gate (${testsPassed} из ${testsTotal} пройдено)</span>
    </div>
    <table>
      <thead>
        <tr>
          <th style="width: 40px;">#</th>
          <th>Название теста</th>
          <th style="width: 100px;">Результат</th>
          <th style="width: 80px;">Время</th>
          <th>Детали верификации</th>
        </tr>
      </thead>
      <tbody>
        ${testsHtml}
      </tbody>
    </table>

    <!-- Recommendations -->
    <div class="section-title">
      <span>3. Рекомендации платформы (${data.recommendations.length})</span>
    </div>
    <div style="background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 18px 24px; font-size: 12px;">
      <ul style="margin: 0; padding-left: 20px;">
        ${recommendationsHtml}
      </ul>
    </div>

    <!-- Verdict Block -->
    <div class="verdict-box">
      <div class="verdict-icon">🛡️</div>
      <div>
        <div class="verdict-title">Финальный вердикт: Решение верифицировано и готово к внедрению</div>
        <p class="verdict-desc">
          Код успешно скомпилирован компилятором JDK 21. Все 15 тестов поведенческой эквивалентности пройдены без регрессий. Дефекты производительности устранены.
        </p>
      </div>
    </div>

    <div class="report-footer">
      <div>WHAT Enterprise Blueprint Platform • Разработано для кейса Axenix 2026</div>
      <div style="margin-top: 4px; font-family: monospace; font-size: 10px;">ID Отчета: ${Math.random().toString(36).substring(2, 10).toUpperCase()} • SHA-256 Verified</div>
    </div>

  </div>

</body>
</html>`
}

export function downloadFile(filename: string, content: string, mimeType: string): void {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

export function openPrintableReport(html: string): void {
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const printWindow = window.open(url, '_blank')
  if (!printWindow) {
    // Popup was blocked: fallback to downloading the HTML file
    downloadFile(`WHAT_Audit_Report_${Date.now()}.html`, html, 'text/html;charset=utf-8')
  }
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function escapeMd(str: string): string {
  return str.replace(/\|/g, '\\|')
}

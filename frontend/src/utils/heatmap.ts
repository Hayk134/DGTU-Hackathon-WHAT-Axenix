export type HeatLevel = 'critical' | 'high' | 'medium' | 'cool'

export function isLoopNode(d: any): boolean {
  if (!d) return false
  const t = (d.type || d.sublabel || '').toUpperCase()
  if (['FOR', 'FOREACH', 'WHILE', 'LOOP'].includes(t)) return true
  const l = (d.label || '').toLowerCase()
  return l.startsWith('for') || l.startsWith('while')
}

export function isBranchNode(d: any): boolean {
  if (!d) return false
  const t = (d.type || d.sublabel || '').toUpperCase()
  if (['IF', 'ELSE', 'SWITCH', 'CASE', 'BRANCH'].includes(t)) return true
  const l = (d.label || '').toLowerCase()
  return l.startsWith('if') || l.startsWith('else') || l.startsWith('switch') || l.startsWith('case')
}

export function getNodeHeatLevel(d: any): HeatLevel {
  if (!d) return 'cool'

  const complexityStr = String(d.complexity || '')
  const cyclomaticNum = parseInt(complexityStr.replace(/\D/g, ''), 10) || 0

  // 1. Critical (Red, 🔥 O(n³)):
  // - High defect severity
  // - Cubic complexity: O(n³), O(n^3)
  // - High cyclomatic complexity >= 8
  // - Or code has deep nested loop (3+ loops)
  const isCubic = complexityStr.includes('n³') || complexityStr.includes('n^3') || complexityStr.includes('n3')
  const hasTripleLoop = d.code && (d.code.match(/\b(for|while)\b/g) || []).length >= 3
  if (
    d.severity === 'HIGH' ||
    isCubic ||
    hasTripleLoop ||
    cyclomaticNum >= 8
  ) {
    return 'critical'
  }

  // 2. High (Orange, ⚡ O(n²)):
  // - Medium defect severity
  // - Quadratic complexity: O(n²), O(n^2)
  // - Nested loop (2 loops in node code or marked sublabel)
  // - Cyclomatic complexity >= 4
  const isQuadratic = complexityStr.includes('n²') || complexityStr.includes('n^2') || complexityStr.includes('n2')
  const hasDoubleLoop = isLoopNode(d) && d.code && (d.code.match(/\b(for|while)\b/g) || []).length >= 2
  if (
    d.severity === 'MEDIUM' ||
    isQuadratic ||
    hasDoubleLoop ||
    (cyclomaticNum >= 4 && cyclomaticNum < 8)
  ) {
    return 'high'
  }

  // 3. Medium (Yellow, ~ O(n)):
  // - Low defect severity
  // - Single loop: FOR, FOREACH, WHILE, LOOP
  // - Branch condition: IF, ELSE, SWITCH, CASE
  // - Linear complexity: O(n) or cyclomatic 2-3
  if (
    d.severity === 'LOW' ||
    isLoopNode(d) ||
    isBranchNode(d) ||
    complexityStr.includes('n') ||
    (cyclomaticNum >= 2 && cyclomaticNum < 4)
  ) {
    return 'medium'
  }

  // 4. Cool (Emerald, ✓ O(1)):
  // - Clean linear statements, constants, simple assignments, start/end
  return 'cool'
}

export function getHeatColor(level: HeatLevel): string {
  switch (level) {
    case 'critical': return '#ef4444' // red
    case 'high': return '#f97316'     // orange
    case 'medium': return '#eab308'   // yellow
    case 'cool': return '#10b981'     // green
  }
}

export function getHeatBadgeText(level: HeatLevel, d?: any): string {
  const comp = String(d?.complexity || '')
  if (level === 'critical') {
    if (comp.includes('n²') || comp.includes('n^2')) return '🔥 O(n²)'
    return '🔥 O(n³)'
  }
  if (level === 'high') {
    return '⚡ O(n²)'
  }
  if (level === 'medium') {
    return '~ O(n)'
  }
  return '✓ O(1)'
}

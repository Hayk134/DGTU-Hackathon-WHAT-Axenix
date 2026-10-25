import { toPng, toJpeg } from 'html-to-image'

export async function exportGraphImage(
  format: 'png' | 'jpeg', 
  mainFile: string = 'JavaBlueprint',
  onLog?: (msg: string, color?: string) => void
): Promise<void> {
  const flowElement = document.querySelector('.vue-flow') as HTMLElement
  if (!flowElement) {
    onLog?.('[Экспорт] Граф не найден в рабочей области', 'text-[#b58585]')
    return
  }

  const fileBase = mainFile.replace(/\.[^/.]+$/, '')
  const dateStr = new Date().toISOString().slice(0, 10)
  const ext = format === 'jpeg' ? 'jpg' : 'png'
  const fileName = `WHAT_Graph_${fileBase}_${dateStr}.${ext}`

  onLog?.(`[Экспорт] Рендеринг снимка графа в ${format.toUpperCase()} (2x Ultra HD)...`, 'text-silver-300')

  const options = {
    backgroundColor: '#0a0d14',
    pixelRatio: 2,
    skipFonts: true,
    filter: (node: HTMLElement) => {
      if (!node.classList) return true
      if (
        node.classList.contains('vue-flow__minimap') ||
        node.classList.contains('vue-flow__controls') ||
        node.classList.contains('vue-flow__panel')
      ) {
        return false
      }
      return true
    },
    quality: format === 'jpeg' ? 0.92 : undefined
  }

  try {
    const dataUrl = format === 'png' 
      ? await toPng(flowElement, options)
      : await toJpeg(flowElement, options)

    const link = document.createElement('a')
    link.download = fileName
    link.href = dataUrl
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    onLog?.(`[Экспорт] Снимок графа успешно сохранен: ${fileName}`, 'text-sage-text')
  } catch (err: any) {
    console.error('Failed to export graph image:', err)
    onLog?.(`[Экспорт Ошибка] Не удалось экспортировать граф: ${err.message || err}`, 'text-[#b58585]')
  }
}

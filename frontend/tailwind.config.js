/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // GRAPHITE SURFACES (Multi-layered depth)
        'graphite-void': '#040507',
        'graphite-editor': '#050608',
        'graphite-stripe': '#090b10',
        'graphite-sidebar': '#0c0e15',
        'graphite-toolbar': '#0c0e14',
        'graphite-dock': '#08090d',
        'graphite-card': '#12151e',
        'graphite-800': '#0f131c',
        'graphite-850': '#0a0d12',
        'graphite-card-top': '#171a25',
        'graphite-hover': '#1d222f',

        // SILVER CHROME SPECTRUM
        'silver-white': '#ffffff',
        'silver-chrome': '#f8fafc',
        'silver-platinum': '#e2e8f0',
        'silver-brushed': '#cbd5e1',
        'silver-steel': '#94a3b8',
        'silver-dark': '#64748b',
        'silver-600': '#64748b',
        'silver-500': '#64748b',
        'silver-400': '#94a3b8',
        'silver-300': '#cbd5e1',

        // MUTED SEMANTIC ACCENTS
        'sage-text': '#9db3a4',
        'sage-tint': 'rgba(157, 179, 164, 0.12)',
        'sage-border': 'rgba(157, 179, 164, 0.35)',

        'titanium-text': '#cfc4b4',
        'titanium-tint': 'rgba(207, 196, 180, 0.12)',
        'titanium-border': 'rgba(207, 196, 180, 0.35)',

        'ash-text': '#b58585',
        'ash-tint': 'rgba(181, 133, 133, 0.12)',
        'ash-border': 'rgba(181, 133, 133, 0.35)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Consolas', 'monospace'],
      },
      fontSize: {
        'xs': ['0.75rem', { lineHeight: '1rem' }],
        'sm': ['0.875rem', { lineHeight: '1.25rem' }],
        'base': ['1rem', { lineHeight: '1.5rem' }],
      },
      spacing: {
        '18': '4.5rem',
        '88': '22rem',
      },
      borderRadius: {
        DEFAULT: '4px',
      },
      boxShadow: {
        'jb': '0 1px 3px rgba(0, 0, 0, 0.4), 0 1px 2px rgba(0, 0, 0, 0.3)',
      }
    },
  },
  plugins: [],
}

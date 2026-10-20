/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        yomira: {
          bg: '#07070a',
          surface: '#111019',
          card: '#161522',
          border: 'rgba(139, 92, 246, 0.18)',
          purple: '#7c3aed',
          glow: '#a855f7',
          light: '#c084fc',
          text: '#f8fafc',
          muted: '#94a3b8'
        }
      },
      aspectRatio: {
        'poster': '2 / 3'
      }
    },
  },
  plugins: [],
}

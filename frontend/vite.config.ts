import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Eigenstaendige Vite-Konfiguration fuer FamilyHub-UI.
// Aus dem urspruenglichen Figma-Make-Export (Ordner "Figma") uebernommen,
// aber von den Figma-Make-spezifischen Plugins/Toolchain-Dateien (.figma/)
// befreit, da dieses Projekt jetzt ausserhalb von Figma Make lebt.
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
  },
})

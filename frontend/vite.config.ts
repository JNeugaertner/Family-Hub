import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Eigenstaendige Vite-Konfiguration fuer das FamilyHub-Frontend.
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
    // Backend unter derselben Adresse wie das Frontend erreichbar machen, damit Anmelde- und CSRF-Cookie
    // ohne Cross-Origin-Umwege funktionieren.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Main Spring Boot backend target (defaults to port 8080)
const target = process.env.VITE_PROXY_TARGET || 'http://localhost:8080'

// Companion FastAPI backend target (defaults to port 8000)
const aiTarget = process.env.VITE_AI_PROXY_TARGET || 'http://127.0.0.1:8000'

// Dev only: drop the browser's Origin header so Spring treats proxied calls as same-origin.
const proxyOpts = {
  target,
  changeOrigin: true,
  configure: (proxy) => {
    proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
  }
}

// Companion AI calls go through Vite. The /companion-api prefix is removed
// before forwarding, so /companion-api/api/v1/chat reaches FastAPI as /api/v1/chat.
const aiProxyOpts = {
  target: aiTarget,
  changeOrigin: true,
  secure: false,
  rewrite: (path) => path.replace(/^\/companion-api/, ''),
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': proxyOpts,
      '/actuator': proxyOpts,
      '/companion-api': aiProxyOpts
    }
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': proxyOpts,
      '/companion-api': aiProxyOpts
    }
  }
})
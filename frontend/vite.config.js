import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const target = process.env.VITE_PROXY_TARGET || 'http://localhost:8080'

// Dev only: drop the browser's Origin header so Spring treats proxied calls as same-origin.
const proxyOpts = {
  target,
  changeOrigin: true,
  configure: (proxy) => {
    proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
  }
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: { '/api': proxyOpts, '/actuator': proxyOpts }
  },
  preview: {
    port: 4173,
    proxy: { '/api': proxyOpts }
  }
})
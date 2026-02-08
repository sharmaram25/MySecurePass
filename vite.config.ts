import path from 'path';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, '.', '');
    return {
      server: {
        port: 8081,
        host: '0.0.0.0',
        open: true,
      },
      preview: {
        port: 8081,
        host: '0.0.0.0',
        open: true,
      },
      plugins: [
        react(),
        VitePWA({
          registerType: 'autoUpdate',
          includeAssets: ['icon.svg'],
          manifest: {
            name: 'MySecurePass',
            short_name: 'SecurePass',
            description: 'Military-grade client-side password generator.',
            theme_color: '#0f172a',
            background_color: '#0f172a',
            display: 'standalone',
            orientation: 'portrait',
            categories: ['utilities', 'security', 'productivity'],
            icons: [
              {
                src: 'icon.svg',
                sizes: 'any',
                type: 'image/svg+xml',
                purpose: 'any maskable'
              },
              {
                src: 'icon.svg',
                sizes: '192x192',
                type: 'image/svg+xml'
              },
              {
                src: 'icon.svg',
                sizes: '512x512',
                type: 'image/svg+xml'
              }
            ]
          },
          workbox: {
            globPatterns: ['**/*.{js,css,html,ico,png,svg,json}'],
            runtimeCaching: [
              {
                urlPattern: /^https:\/\/cdn\.tailwindcss\.com\/.*/i,
                handler: 'CacheFirst',
                options: {
                  cacheName: 'tailwindcss-cache',
                  expiration: {
                    maxEntries: 10,
                    maxAgeSeconds: 60 * 60 * 24 * 365 // <== 365 days
                  },
                  cacheableResponse: {
                    statuses: [0, 200]
                  }
                }
              },
              {
                  urlPattern: /^https:\/\/esm\.sh\/.*/i,
                  handler: 'CacheFirst',
                   options: {
                      cacheName: 'esm-sh-cache',
                      expiration: {
                          maxEntries: 50,
                          maxAgeSeconds: 60 * 60 * 24 * 30
                      },
                      cacheableResponse: {
                          statuses: [0, 200]
                      }
                   }
              }
            ]
          }
        })
      ],
      define: {
        'process.env.API_KEY': JSON.stringify(env.GEMINI_API_KEY),
        'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY)
      },
      resolve: {
        alias: {
          '@': path.resolve(__dirname, '.'),
        }
      }
    };
});

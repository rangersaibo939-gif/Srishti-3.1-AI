import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import fs from 'fs';
import {defineConfig, Plugin} from 'vite';

function apkDownloadMiddlewarePlugin(): Plugin {
  return {
    name: 'apk-download-middleware',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url ? req.url.split('?')[0] : '';
        const apkPaths = ['/Srishti3.0.apk', '/Srishti3.0-ContinuousVoice-debug.apk', '/api/download/apk', '/download/apk'];
        const zipPaths = ['/Srishti3.0-APK.zip', '/Srishti3.0-ContinuousVoice-debug.zip', '/api/download/zip', '/download/zip'];

        if (apkPaths.includes(url)) {
          const possiblePaths = [
            path.resolve(__dirname, 'public/Srishti3.0.apk'),
            '/storage/emulated/0/Download/Srishti3.0.apk',
            path.resolve(__dirname, 'app/build/outputs/apk/debug/app-debug.apk')
          ];
          const filePath = possiblePaths.find(p => fs.existsSync(p));
          if (filePath) {
            const stat = fs.statSync(filePath);
            res.writeHead(200, {
              'Content-Type': 'application/vnd.android.package-archive',
              'Content-Disposition': 'attachment; filename="Srishti3.0.apk"',
              'Content-Length': stat.size,
              'Access-Control-Allow-Origin': '*',
              'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
              'Access-Control-Allow-Headers': '*',
              'Cache-Control': 'no-cache, no-store, must-revalidate',
              'Pragma': 'no-cache',
              'Expires': '0'
            });
            fs.createReadStream(filePath).pipe(res);
            return;
          }
        } else if (zipPaths.includes(url)) {
          const possiblePaths = [
            path.resolve(__dirname, 'public/Srishti3.0-APK.zip'),
            '/storage/emulated/0/Download/Srishti3.0-APK.zip',
            path.resolve(__dirname, 'app/build/outputs/apk/debug/Srishti3.0-ContinuousVoice-debug.zip')
          ];
          const filePath = possiblePaths.find(p => fs.existsSync(p));
          if (filePath) {
            const stat = fs.statSync(filePath);
            res.writeHead(200, {
              'Content-Type': 'application/zip',
              'Content-Disposition': 'attachment; filename="Srishti3.0-APK.zip"',
              'Content-Length': stat.size,
              'Access-Control-Allow-Origin': '*',
              'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
              'Access-Control-Allow-Headers': '*',
              'Cache-Control': 'no-cache, no-store, must-revalidate',
              'Pragma': 'no-cache',
              'Expires': '0'
            });
            fs.createReadStream(filePath).pipe(res);
            return;
          }
        }
        next();
      });
    }
  };
}

export default defineConfig(() => {
  return {
    plugins: [apkDownloadMiddlewarePlugin(), react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
      dedupe: ['react', 'react-dom'],
    },
    optimizeDeps: {
      include: ['react', 'react-dom', 'lucide-react', 'jszip'],
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});

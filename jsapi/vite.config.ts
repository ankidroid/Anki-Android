import { defineConfig } from 'vite';
import path from 'path';


export default defineConfig({
    build: {
        lib: {
            entry: path.resolve(__dirname, 'src/main.ts'),
            name: 'AnkiDroidJs',
            formats: ['iife']
        },
        outDir: 'dist',
        sourcemap: false,
        reportCompressedSize: false,
        target: ["es2020"],
    },
});

import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.jsonc" },
      miniflare: {
        r2Buckets: ["FILES"],
        bindings: {
          BOOTSTRAP_TOKEN: "test-teachtrack-token",
          STORAGE_SECRET: "test-storage-secret"
        }
      }
    })
  ]
});

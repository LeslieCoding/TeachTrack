import { authenticate } from "./auth";
import { HttpError, json } from "./http";
import { handleAssets } from "./routes/assets";
import { handleCore } from "./routes/core";
import { handleExams } from "./routes/exams";
import { handleExport } from "./routes/export";
import { handleMcp } from "./routes/mcp";
import { handleMigrations } from "./routes/migrations";
import { handlePractices } from "./routes/practices";
import { handleQuestions } from "./routes/questions";
import { handleSync } from "./routes/sync";
import type { Env } from "./types";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    try {
      if (request.method === "OPTIONS") {
        return new Response(null, {
          status: 204,
          headers: {
            "access-control-allow-origin": "*",
            "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
            "access-control-allow-headers": "authorization,content-type",
          },
        });
      }
      if (request.method === "GET" && url.pathname === "/actuator/health") {
        return json({ status: "UP" });
      }
      if (request.method === "GET" && url.pathname === "/") {
        return json({ name: "teachtrack-api", engine: "Cloudflare Workers + D1", health: "/actuator/health" });
      }

      if (url.pathname.startsWith("/api/v1/assets/upload/") || url.pathname.startsWith("/api/v1/assets/download/")) {
        const signed = await handleAssets(request, env, url, null);
        if (signed) return signed;
      }
      const principal = await authenticate(request, env);
      const response = await handleCore(request, env, url, principal)
        ?? await handleQuestions(request, env, url, principal)
        ?? await handleSync(request, env, principal)
        ?? await handleAssets(request, env, url, principal)
        ?? await handleExams(request, env, url, principal)
        ?? await handleExport(request, env, url, principal)
        ?? await handlePractices(request, env, url, principal)
        ?? await handleMcp(request, env, principal)
        ?? await handleMigrations(request, env, principal);
      if (response) return response;
      throw new HttpError(404, "NOT_FOUND", `接口不存在: ${request.method} ${url.pathname}`);
    } catch (error) {
      if (error instanceof HttpError) {
        let message = error.message;
        let details: Record<string, unknown> = {};
        if (error.status === 409) {
          try {
            const parsed = JSON.parse(message) as Record<string, unknown>;
            message = String(parsed.message ?? "版本冲突");
            details = parsed;
          } catch { /* keep original message */ }
        }
        return json({ error: { code: error.code, message, ...details } }, error.status);
      }
      console.error("request failed", error);
      return json({ error: { code: "INTERNAL_ERROR", message: "服务器内部错误" } }, 500);
    }
  },
} satisfies ExportedHandler<Env>;

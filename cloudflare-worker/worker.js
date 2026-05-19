/**
 * Cloudflare Worker for TXBox Remote Control
 *
 * KV Namespace binding required: DEVICES
 *   - Key pattern for registration:  "device:<device_id>"       → JSON { registered_at }
 *   - Key pattern for control URL:   "url:<device_id>"          → control URL string
 *
 * Endpoints:
 *   POST /api/register?device_id=<id>              – register a device
 *   GET  /api/getUrl?device_id=<id>                – get the master control URL for a device
 *   POST /api/setUrl?device_id=<id>&url=<url>      – set the master control URL for a device
 */

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

function errorResponse(message, status = 400) {
  return jsonResponse({ ok: false, error: message }, status);
}

async function handleRegister(request, env) {
  const url = new URL(request.url);
  const deviceId = url.searchParams.get("device_id");
  if (!deviceId || deviceId.trim() === "") {
    return errorResponse("device_id is required");
  }

  const key = `device:${deviceId}`;
  const existing = await env.DEVICES.get(key);
  if (!existing) {
    await env.DEVICES.put(key, JSON.stringify({ registered_at: Date.now() }));
  }

  return jsonResponse({ ok: true, device_id: deviceId });
}

async function handleGetUrl(request, env) {
  const url = new URL(request.url);
  const deviceId = url.searchParams.get("device_id");
  if (!deviceId || deviceId.trim() === "") {
    return errorResponse("device_id is required");
  }

  const controlUrl = await env.DEVICES.get(`url:${deviceId}`);
  return jsonResponse({ ok: true, device_id: deviceId, url: controlUrl || "" });
}

async function handleSetUrl(request, env) {
  const url = new URL(request.url);
  const deviceId = url.searchParams.get("device_id");
  const controlUrl = url.searchParams.get("url");

  if (!deviceId || deviceId.trim() === "") {
    return errorResponse("device_id is required");
  }
  if (!controlUrl || controlUrl.trim() === "") {
    return errorResponse("url is required");
  }

  // Verify device is registered
  const registered = await env.DEVICES.get(`device:${deviceId}`);
  if (!registered) {
    return errorResponse("device not registered", 404);
  }

  await env.DEVICES.put(`url:${deviceId}`, controlUrl);
  return jsonResponse({ ok: true, device_id: deviceId, url: controlUrl });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    if (path === "/api/register" && request.method === "POST") {
      return handleRegister(request, env);
    }
    if (path === "/api/getUrl" && request.method === "GET") {
      return handleGetUrl(request, env);
    }
    if (path === "/api/setUrl" && request.method === "POST") {
      return handleSetUrl(request, env);
    }

    return new Response("Not Found", { status: 404 });
  },
};

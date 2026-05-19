# Cloudflare Worker – TXBox Remote Control

## KV Namespace

Create a KV namespace named **DEVICES** and bind it to the worker as `DEVICES`.

```toml
# wrangler.toml
name = "txbox-remote"
main = "worker.js"
compatibility_date = "2024-01-01"

[[kv_namespaces]]
binding = "DEVICES"
id     = "<your-kv-namespace-id>"
```

## Endpoints

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| `POST` | `/api/register` | `?device_id=<id>` | Register a device |
| `GET`  | `/api/getUrl`   | `?device_id=<id>` | Get master-control URL for a device |
| `POST` | `/api/setUrl`   | `?device_id=<id>&url=<url>` | Set master-control URL for a device |

## Deploy

```bash
npm install -g wrangler
wrangler deploy
```

# PortalProxy

A HTTP/HTTPS proxy server with captive portal functionality.

## Overview

PortalProxy provides:

- HTTP/HTTPS proxy server with configurable port
- Captive portal for network authentication
- IP-based access control and allowlisting
- CONNECT tunneling for HTTPS traffic
- Real-time IP tracking and login/logout functionality

## Environment Variables

### Server Configuration

| Variable      | Default | Description                                 |
|---------------|---------|---------------------------------------------|
| `PROXY_PORT`  | `8000`  | Port for the proxy server                   |
| `PORTAL_PORT` | `8001`  | Port for the captive portal web interface   |
| `PORTAL_HOST` | `null`  | Friendly hostname for the portal (optional) |

### Access Control

| Variable                 | Default | Description                                                  |
|--------------------------|---------|--------------------------------------------------------------|
| `PROXY_ALLOWLIST_DOMAIN` | `null`  | Comma-separated list of allowed domains for CONNECT requests |
| `PROXY_ALLOWLIST_PORT`   | `null`  | Comma-separated list of allowed ports for CONNECT requests   |

Providing empty values for `PROXY_ALLOWLIST_DOMAIN` or `PROXY_ALLOWLIST_PORT` disables allowlisting.

## Usage

### Basic Setup

```bash
./gradlew :portalProxy:run
```

### Portal Interface

Access the captive portal at `http://localhost:8001/` (or your configured `PORTAL_PORT`).

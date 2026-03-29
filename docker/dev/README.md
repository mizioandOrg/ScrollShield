# ScrollShield — Dev SSH Containers

Three SSH-accessible containers that mirror the CI/production environments
for each service. Developers connect via SSH so any IDE with remote-SSH
support (VS Code Remote-SSH, JetBrains Gateway, etc.) works out of the box.

| Container | Host port | Service |
|---|---|---|
| `android-dev` | 2244 | Android SDK 34, Gradle 8.5, JDK 17 |
| `ml-dev` | 2243 | Python 3.11, PyTorch, TensorFlow, all ML deps |
| `signature-api-dev` | 2242 | Python 3.11, FastAPI, Uvicorn |

All ports are bound to `127.0.0.1` on the host — they are not exposed
to the local network.

---

## Prerequisites

- Docker Engine 24+ and Docker Compose v2
- An SSH key pair (ed25519 recommended)

---

## Quick start

```bash
# From repo root — start all three containers
DEV_SSH_PUBKEY="$(cat ~/.ssh/id_ed25519.pub)" \
  docker compose -f docker/dev/docker-compose.dev.yml up -d --build

# Start a single container by service name
DEV_SSH_PUBKEY="$(cat ~/.ssh/id_ed25519.pub)" \
  docker compose -f docker/dev/docker-compose.dev.yml up -d --build android-dev

# Available service names: android-dev  ml-dev  signature-api-dev
```

On first start each container generates its SSH host keys and stores them
on a named volume. Subsequent restarts reuse those keys, so your SSH client
will not report a host-key-changed warning.

---

## Connecting

```bash
ssh -p 2244 devuser@127.0.0.1   # android-dev
ssh -p 2243 devuser@127.0.0.1   # ml-dev
ssh -p 2242 devuser@127.0.0.1   # signature-api-dev
```

Add to `~/.ssh/config` for convenience:

```
Host scrollshield-android
    HostName 127.0.0.1
    Port 2244
    User devuser
    IdentityFile ~/.ssh/id_ed25519

Host scrollshield-ml
    HostName 127.0.0.1
    Port 2243
    User devuser
    IdentityFile ~/.ssh/id_ed25519

Host scrollshield-api
    HostName 127.0.0.1
    Port 2242
    User devuser
    IdentityFile ~/.ssh/id_ed25519
```

If the containers run on a remote server, add a `ProxyJump` to tunnel through:

```
Host your-container-alias
    HostName 127.0.0.1
    Port 2244
    User devuser
    ProxyJump user-on-server@your-server-ip
    IdentityFile ~/.ssh/id_ed25519_your_private_key
```

Replace `Port` with the appropriate value (`2244` / `2243` / `2242`) for each container.

---

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `DEV_SSH_PUBKEY` | Yes | Public key written to `devuser`'s `authorized_keys` on every start. Rotate by setting a new value and running `docker compose ... restart`. |

The entrypoint exits with status 1 if `DEV_SSH_PUBKEY` is unset, so a
misconfigured start fails immediately with a clear error rather than
silently producing an inaccessible container.

---

## Key rotation

Set `DEV_SSH_PUBKEY` to the new public key and restart:

```bash
DEV_SSH_PUBKEY="$(cat ~/.ssh/new_id_ed25519.pub)" \
  docker compose -f docker/dev/docker-compose.dev.yml restart
```

`authorized_keys` is always overwritten on start (no conditional guard),
so the new key takes effect immediately without rebuilding the image.

---

## Workspace mount

The repo root is bind-mounted at `/workspace` inside every container.
Changes made inside the container are reflected on the host and vice versa.

---

## Python dependencies

Python packages are pre-installed at image build time using the same
requirements files referenced by CI:

- **ml-dev**: `ml/requirements.txt` and `ml/requirements-visual.txt`
- **signature-api-dev**: `docker/signature-api/requirements.txt`

No additional `pip install` steps are needed after connecting. If you add
a dependency to a requirements file, rebuild the image:

```bash
docker compose -f docker/dev/docker-compose.dev.yml build ml-dev
docker compose -f docker/dev/docker-compose.dev.yml up -d ml-dev
```

---

## Why sshd runs as root

`sshd` must start as root to:

1. Bind port 22 (privileged port).
2. Call `setuid`/`setgid` to drop privileges to `devuser` after
   authenticating the public key.

`PermitRootLogin no` is set, so root itself cannot log in. The only
account reachable over SSH is `devuser`.

---

## Named volumes

| Volume | Mount point | Purpose |
|---|---|---|
| `*-home` | `/home/devuser` | Shell history, editor config, tool caches |
| `*-host-keys` | `/etc/ssh/host-keys` | SSH host keys (stable across restarts) |

No `VOLUME` instructions are present in the Dockerfiles. Volumes are
declared exclusively in `docker-compose.dev.yml` so CI builds that do not
use compose are not affected by anonymous volume side-effects.

---

## Stopping

```bash
docker compose -f docker/dev/docker-compose.dev.yml down
# To also remove named volumes (destroys persisted home directories):
docker compose -f docker/dev/docker-compose.dev.yml down -v
```

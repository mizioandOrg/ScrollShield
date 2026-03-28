#!/usr/bin/env bash
set -euo pipefail

# -------------------------------------------------------------------
# Validate required environment variable
# -------------------------------------------------------------------
if [[ -z "${DEV_SSH_PUBKEY:-}" ]]; then
    echo "ERROR: DEV_SSH_PUBKEY is not set. Pass it via environment." >&2
    exit 1
fi

# -------------------------------------------------------------------
# SSH host key persistence
# Host keys live on a named volume at /etc/ssh/host-keys so they
# survive container recreation and do not change on every restart,
# preventing host-key-changed warnings on the developer's machine.
# -------------------------------------------------------------------
HOST_KEY_DIR="/etc/ssh/host-keys"
mkdir -p "${HOST_KEY_DIR}"

for type in rsa ecdsa ed25519; do
    key_file="${HOST_KEY_DIR}/ssh_host_${type}_key"
    if [[ ! -f "${key_file}" ]]; then
        ssh-keygen -q -t "${type}" -N "" -f "${key_file}"
        echo "Generated host key: ${key_file}"
    fi
done

# Point sshd at the persisted host keys
{
    echo "HostKey ${HOST_KEY_DIR}/ssh_host_rsa_key"
    echo "HostKey ${HOST_KEY_DIR}/ssh_host_ecdsa_key"
    echo "HostKey ${HOST_KEY_DIR}/ssh_host_ed25519_key"
} >> /etc/ssh/sshd_config

# -------------------------------------------------------------------
# authorized_keys — always overwrite so a rotated DEV_SSH_PUBKEY
# takes effect on the next container restart without rebuilding.
# -------------------------------------------------------------------
AUTHORIZED_KEYS="/home/devuser/.ssh/authorized_keys"
echo "${DEV_SSH_PUBKEY}" > "${AUTHORIZED_KEYS}"
chmod 600 "${AUTHORIZED_KEYS}"
chown devuser:devuser "${AUTHORIZED_KEYS}"

# -------------------------------------------------------------------
# Start sshd in the foreground
# -D  keeps sshd in the foreground (required for PID 1 / Docker)
# -e  logs to stderr so Docker captures output via `docker logs`
# -------------------------------------------------------------------
exec /usr/sbin/sshd -D -e

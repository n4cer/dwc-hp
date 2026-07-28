#!/bin/bash
set -euo pipefail

# Only needed for Claude Code on the web (remote sessions).
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SBT_VERSION="1.12.11"
SBT_DIR="/opt/sbt-${SBT_VERSION}"
SBT_URL="https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz"

# --- sbt ---------------------------------------------------------------
# sbt is not available via apt; project/build.properties pins this exact
# version, so we fetch the official tarball once and cache it under /opt.
if [ ! -x "${SBT_DIR}/bin/sbt" ]; then
  echo "Installing sbt ${SBT_VERSION}..."
  tmp_tgz="$(mktemp)"
  tmp_extract="$(mktemp -d)"
  curl -fsSL "${SBT_URL}" -o "${tmp_tgz}"
  tar xzf "${tmp_tgz}" -C "${tmp_extract}"
  rm -rf "${SBT_DIR}"
  mv "${tmp_extract}/sbt" "${SBT_DIR}"
  rm -f "${tmp_tgz}"
  rm -rf "${tmp_extract}"
else
  echo "sbt ${SBT_VERSION} already installed, skipping download."
fi

echo "export PATH=\"${SBT_DIR}/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"

# --- PostgreSQL ----------------------------------------------------------
# conf/application.conf hardcodes jdbc:postgresql://localhost:5432/dwc with
# user postgres/password "bla" and no H2/test override, so `sbt test` needs
# a real local Postgres instance.
if ! command -v psql >/dev/null 2>&1; then
  echo "Installing PostgreSQL..."
  apt-get update -qq
  apt-get install -y -qq postgresql postgresql-contrib
else
  echo "PostgreSQL already installed, skipping install."
fi

if ! pg_isready -q 2>/dev/null; then
  service postgresql start
  for i in $(seq 1 30); do
    pg_isready -q 2>/dev/null && break
    sleep 1
  done
fi

su - postgres -c "psql -c \"ALTER USER postgres PASSWORD 'bla';\"" >/dev/null

if ! su - postgres -c "psql -lqt" | cut -d '|' -f 1 | tr -d ' ' | grep -qx dwc; then
  echo "Creating dwc database..."
  su - postgres -c "createdb dwc"
else
  echo "dwc database already exists, skipping creation."
fi

echo "Session start hook complete."

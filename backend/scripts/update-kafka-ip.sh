#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/update-kafka-ip.sh --public-ip <PUBLIC_IP> [--private-ip <PRIVATE_IP>] [--file <path>]

Examples:
  ./scripts/update-kafka-ip.sh --public-ip 8.136.198.99
  ./scripts/update-kafka-ip.sh --public-ip 8.136.198.99 --private-ip 172.29.234.111
  ./scripts/update-kafka-ip.sh --public-ip 8.136.198.99 --file ./server.properties
USAGE
}

PUBLIC_IP=""
PRIVATE_IP=""
FILE="server.properties"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --public-ip)
      PUBLIC_IP="${2:-}"
      shift 2
      ;;
    --private-ip)
      PRIVATE_IP="${2:-}"
      shift 2
      ;;
    --file)
      FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$PUBLIC_IP" ]]; then
  echo "Missing --public-ip" >&2
  usage >&2
  exit 1
fi

if [[ ! -f "$FILE" ]]; then
  echo "File not found: $FILE" >&2
  exit 1
fi

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

awk -v pub="$PUBLIC_IP" -v priv="$PRIVATE_IP" '
{
  line=$0
  if (line ~ /^advertised\.listeners=/) {
    gsub(/EXTERNAL:\/\/[^:, \t]+(:[0-9]+)/, "EXTERNAL://" pub "\\1", line)
    if (priv != "") {
      gsub(/INTERNAL:\/\/[^:, \t]+(:[0-9]+)/, "INTERNAL://" priv "\\1", line)
    }
  }
  if (priv != "" && line ~ /^controller\.quorum\.voters=/) {
    gsub(/@[^:, \t]+(:[0-9]+)/, "@" priv "\\1", line)
  }
  print line
}
' "$FILE" > "$tmp_file"

if cmp -s "$FILE" "$tmp_file"; then
  echo "No changes applied. Check patterns or input."
  exit 0
fi

mv "$tmp_file" "$FILE"
echo "Updated: $FILE"
echo "Public IP -> $PUBLIC_IP"
if [[ -n "$PRIVATE_IP" ]]; then
  echo "Private IP -> $PRIVATE_IP"
fi

#!/bin/sh
set -e

echo "[SSL] JAVA_HOME=${JAVA_HOME:-/opt/java/openjdk}"

JAVA_HOME="${JAVA_HOME:-/opt/java/openjdk}"
CACERTS="$JAVA_HOME/lib/security/cacerts"
STOREPASS="${JAVA_CACERTS_PASSWORD:-changeit}"

echo "[SSL] Using cacerts: $CACERTS"

if [ -d /certs ]; then
  echo "[SSL] Found /certs directory:"
  ls -la /certs || true

  echo "[SSL] Importing CA certificates into JVM truststore..."

  for cert in /certs/*.cer /certs/*.crt; do
    [ -f "$cert" ] || continue

    alias="giga-$(basename "$cert" | sed 's/\.[^.]*$//')"

    if keytool -list -keystore "$CACERTS" -storepass "$STOREPASS" -alias "$alias" >/dev/null 2>&1; then
      echo "[SSL] Alias already exists: $alias"
    else
      echo "[SSL] Importing $cert as $alias"
      keytool -importcert \
        -noprompt \
        -trustcacerts \
        -alias "$alias" \
        -file "$cert" \
        -keystore "$CACERTS" \
        -storepass "$STOREPASS"
    fi
  done

  echo "[SSL] Imported aliases:"
  keytool -list -keystore "$CACERTS" -storepass "$STOREPASS" | grep -i 'giga-\|russian_trusted' || true

  echo "[SSL] Updating OS trust store as well..."
  cp /certs/*.crt /usr/local/share/ca-certificates/ 2>/dev/null || true
  cp /certs/*.cer /usr/local/share/ca-certificates/ 2>/dev/null || true
  update-ca-certificates || true
fi

exec java -jar /app/app.jar
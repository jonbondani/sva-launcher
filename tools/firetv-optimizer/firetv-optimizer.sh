#!/bin/bash
# =============================================================================
#  firetv-optimizer.sh: Debloat reversible para Fire OS 8
#  Pensado para Xiaomi Fire TV F Pro (y otros televisores/sticks con Fire OS).
#
#  - Solo DESACTIVA paquetes (pm disable-user), nunca desinstala.
#  - Guarda una copia de cada cambio y permite restaurarlo.
#  - Nunca toca la lista de paquetes protegidos (launcher, ajustes, mando...).
#  - Compatible con el bash 3.2 que trae macOS.
#
#  Uso rápido:  ./firetv-optimizer.sh            (menú interactivo)
#               ./firetv-optimizer.sh ayuda       (todos los comandos)
# =============================================================================
set -u

VERSION="1.11.0"
BASE_DIR="${FTVO_HOME:-$HOME/firetv-optimizer}"
BACKUP_DIR="$BASE_DIR/copias"
ADB="${ADB:-adb}"
TARGET="${FIRETV:-}"
DRY_RUN=0
ASSUME_YES=0
FORCE=0
FORCE_TV=0

# --------------------------------------------------------------------------
# Presentación
# --------------------------------------------------------------------------
if [ -t 1 ]; then
  C_R=$'\033[31m'; C_G=$'\033[32m'; C_Y=$'\033[33m'; C_B=$'\033[36m'; C_D=$'\033[2m'; C_0=$'\033[0m'; C_BOLD=$'\033[1m'
else
  C_R=""; C_G=""; C_Y=""; C_B=""; C_D=""; C_0=""; C_BOLD=""
fi
info() { printf '%s\n' "${C_B}›${C_0} $*"; }
ok()   { printf '%s\n' "${C_G}✓${C_0} $*"; }
warn() { printf '%s\n' "${C_Y}!${C_0} $*"; }
err()  { printf '%s\n' "${C_R}✗${C_0} $*" >&2; }
die()  { err "$*"; exit 1; }
title(){ printf '\n%s\n' "${C_BOLD}$*${C_0}"; }

confirm() {
  [ "$ASSUME_YES" -eq 1 ] && return 0
  printf '%s [s/N] ' "$1"
  read -r ans
  case "$ans" in s|S|si|SI|sí|Sí|y|Y) return 0 ;; *) return 1 ;; esac
}

# --------------------------------------------------------------------------
# Listas de paquetes. Formato: paquete|descripción
# Solo se actúa sobre los que existan en la tele; el resto se ignora.
# --------------------------------------------------------------------------

# Nunca se desactivan, aunque aparezcan en otra lista o se pidan a mano.
PROTECTED_EXACT="
com.amazon.firebat
com.amazon.tv.launcher
com.amazon.tv.settings
com.amazon.tv.settings.v2
com.amazon.device.settings
com.amazon.tv.ime
com.amazon.fireinputdevices
com.amazon.dcp
com.amazon.tcomm
com.amazon.device.messaging
com.amazon.webview.chromium
com.amazon.kindleautomatictimezone
com.amazon.providers.contentsupport
com.amazon.application.compatibility.enforcer
com.amazon.whisperplay.service.install
com.amazon.vizzini
com.amazon.device.sync
com.amazon.tv.oobe
com.amazon.tv.developer.dataservice
com.amazon.logan
com.amazon.tv.csapp
com.amazon.ftvads.deeplinking
com.amazon.client.metrics.api
com.amazon.dp.logger
com.amazon.connectivitydiag
com.amazon.perfc
com.amazon.media.recommendations
com.amazon.privacypassservice
com.amazon.aria
com.amazon.aca
com.amazon.imp
com.amazon.identity.auth.device.authorization
com.amazon.securitysyncclient
com.amazon.venezia
com.amazon.firehomestarter
com.amazon.tahoe
com.amazon.tv.intentsupport
com.amazon.alexadirectivebrokerservice
com.amazon.alexa.datastore.app
com.amazon.device.metrics
"
# Cualquier paquete que encaje con esto también queda protegido
# (entradas HDMI, TDT, configuración inicial, Bluetooth, instalador...).
PROTECTED_REGEX='(mediatek|mstar|tvcenter|factory|hdmicec|\.cec|\.arc$|channelscan|devicecontrol|inputpreference|tifobserver|quicksettings|keypolicy|routing|resolutioncycler|audioprovider|audioeffects|neodelegate|^com\.fireos\.|overlay|launcher|settings|\.ime$|inputdevices|inputs?$|livetv|tvinput|\.tif|hdmi|tuner|setupwizard|oobe|\.dcp$|tcomm|webview|bluetooth|device\.messaging|packageinstaller|systemui|^android$|^com\.android\.)'

list_tier() {
  case "$1" in
    seguro) cat <<'EOF'
com.amazon.client.metrics|Métricas de uso enviadas a Amazon
com.amazon.wirelessmetrics.service|Métricas de red wifi
com.amazon.device.crashmanager|Envío de informes de fallos
com.amazon.tv.fw.metrics|Métricas del sistema
com.amazon.shoptv.client|Compras desde la tele
com.amazon.shoptv.firetv.client|Compras desde la tele (Fire TV)
com.amazon.device.sale.service|Ofertas y promociones de dispositivos
com.amazon.bueller.photos|Amazon Photos (salvapantallas de fotos)
com.amazon.bueller.music|Amazon Music integrado en la interfaz
com.amazon.ods.kindleconnect|Conexión con Kindle
com.amazon.ags.app|Amazon GameCircle (obsoleto)
com.amazon.minitv.android.app|Amazon miniTV
com.amazon.imdb.tv.android.app|Freevee / IMDb TV
EOF
    ;;
    moderado) cat <<'EOF'
com.amazon.alexa.externalmediaplayer.fireos|Alexa: control de reproducción por voz
com.amazon.tv.alexanotifications|Alexa: notificaciones en pantalla
com.amazon.hedwig|Notificaciones push de Amazon
com.amazon.cardinal|Contenido ambiental / recomendaciones
com.amazon.recess|Servicio de contenidos patrocinados
com.amazon.aca|Servicio de contenidos de Amazon
com.amazon.imp|Servicio de recomendaciones/impresiones
com.amazon.alta.h2clientservice|Servicio de cliente de Amazon (fondo)
com.amazon.dpcclient|Cliente de políticas del dispositivo
com.amazon.cloud9|Navegador Silk
com.amazon.tahoe|Amazon Kids (FreeTime)
com.amazon.gamehub|Centro de juegos
com.amazon.audiohome|Inicio de audio (Alexa/música)
com.amazon.spiderpork|Servicio de fondo de Amazon
com.amazon.wifilocker|Guardar contraseñas wifi en tu cuenta Amazon
com.amazon.communication.discovery|Descubrimiento de dispositivos Alexa
com.amazon.whisperplay.contracts|Envío a la tele desde el móvil (Fling)
com.amazon.ftv.profilepicker|Selector de perfiles al encender
EOF
    ;;
    tele) cat <<'EOF'
com.amazon.sneakpeek|Vistas previas automáticas en el inicio
com.amazon.kso.blackbird|Salvapantallas con anuncios
com.amazon.bueller.photos|Amazon Photos (salvapantallas de fotos)
com.amazon.bueller.music|Amazon Music integrado en la interfaz
com.amazon.shoptv.client|Compras desde la tele
com.amazon.minitv.android.app|Amazon miniTV
com.amazon.gamehub|Centro de juegos
com.amazon.storm.lightning.tutorial|Tutorial del mando
com.amazon.tmm.tutorial|Tutoriales
com.amazon.tv.democontent.provider|Contenido de demostración de tienda
com.amazon.wirelessmetrics.service|Métricas de red wifi
com.amazon.perfcollection|Recolector de rendimiento
com.amazon.device.telemetry.emitter|Envío de telemetría
com.amazon.hybridadidservice|Identificador publicitario
com.amazon.tv.acr|Reconocimiento de lo que ves en pantalla (ACR)
com.amazon.ods.kindleconnect|Conexión con Kindle
com.amazon.tv.easyupgrade|Asistente de actualización
com.amazon.spiderpork|Servicio de fondo de Amazon
com.amazon.hedwig|Notificaciones push de Amazon
EOF
    ;;
    ota) cat <<'EOF'
com.amazon.device.software.ota|Descarga de actualizaciones del sistema
com.amazon.device.software.ota.override|Forzado de actualizaciones del sistema
com.amazon.tv.forcedotaupdater.v2|Actualizador forzoso
com.amazon.tv.easyupgrade|Asistente de actualización
EOF
    ;;
    *) return 1 ;;
  esac
}

# Ajustes de privacidad: espacio|clave|valor. Claves de Fire OS; si no existen no pasa nada.
PRIVACY_SETTINGS="
secure|amz_limit_ad_tracking|1|Limitar el seguimiento publicitario de Amazon
secure|limit_ad_tracking|1|Limitar el seguimiento publicitario (Android)
secure|non_amz_limit_ad_tracking|1|Limitar el seguimiento publicitario de otras apps
secure|advertisingIdApp:interestBasedAds:value|0|Anuncios basados en tus intereses: no
secure|USAGE_METRICS_UPLOAD_ENABLED|0|Envío de métricas de uso: no
secure|usage_metrics_marketing_enabled|0|Uso de tus datos para marketing: no
secure|settingsApp:appUsageData:value|0|Recopilar datos de uso de apps: no
secure|settingsApp:deviceUsageData:value|0|Recopilar datos de uso del dispositivo: no
global|com.amazon.tv.settings.featured_item_video_enabled|0|Vídeo automático en los destacados: no
global|com.amazon.tv.settings.featured_item_video_volume|0.0|Volumen del vídeo de destacados: 0
"

# Paquetes que Amazon suele impedir desactivar. «blindar» les quita el permiso de
# ejecutarse en segundo plano (appops). Las actualizaciones (OTA) no se tocan aquí.
SHIELD_PKGS="
com.amazon.device.metrics|Métricas del dispositivo
com.amazon.client.metrics|Métricas de uso enviadas a Amazon
com.amazon.device.crashmanager|Envío de informes de fallos
com.amazon.device.logmanager|Gestor de registros que se envían a Amazon
com.amazon.device.rdmapplication|Gestión remota del dispositivo (telemetría)
"
SHIELD_OPS="RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND"

tier_of() {  # devuelve el nivel al que pertenece un paquete, o vacío
  local p="$1" t
  for t in seguro moderado ota; do
    if list_tier "$t" | cut -d'|' -f1 | grep -qx "$p"; then echo "$t"; return; fi
  done
}

is_protected() {
  local p="$1"
  printf '%s\n' "$PROTECTED_EXACT" | grep -qx "$p" && return 0
  printf '%s\n' "$p" | grep -Eq "$PROTECTED_REGEX" && return 0
  return 1
}

# --------------------------------------------------------------------------
# ADB
# --------------------------------------------------------------------------
need_adb() {
  if ! command -v "$ADB" >/dev/null 2>&1; then
    err "No encuentro adb."
    echo "  Instálalo con Homebrew:  brew install android-platform-tools"
    exit 1
  fi
}

adb_s()  { "$ADB" -s "$TARGET" "$@"; }
dsh()    { adb_s shell "$@" 2>&1 | tr -d '\r'; }

connected_devices() {
  "$ADB" devices 2>/dev/null | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}'
}

connect_ip() {
  local ip="$1"
  case "$ip" in *:*) ;; *) ip="$ip:5555" ;; esac
  info "Conectando con ${ip}… (acepta el aviso «¿Permitir depuración?» en la tele)"
  local out; out=$("$ADB" connect "$ip" 2>&1 | tr -d '\r')
  echo "  $out"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if connected_devices | grep -qx "$ip"; then TARGET="$ip"; ok "Conectado a $ip"; return 0; fi
    sleep 2
  done
  die "No se pudo conectar. Revisa que la depuración ADB esté activa y que aceptaste el aviso en la tele."
}

# --------------------------------------------------------------------------
# Búsqueda de teles en la red: escanea el puerto ADB (5555) de tu red local,
# se conecta a cada aparato que responda y dice qué modelo es.
# --------------------------------------------------------------------------
KNOWN_FILE="$BASE_DIR/teles.txt"

model_name() {   # nombre comercial de los códigos de modelo de Amazon más comunes
  case "$1" in
    AFTKA)   echo "Fire TV Stick 4K Max (1.ª gen., 2021)" ;;
    AFTKRT)  echo "Fire TV Stick 4K Max (2.ª gen., 2023)" ;;
    AFTKM)   echo "Fire TV Stick 4K (2.ª gen.)" ;;
    AFTMM)   echo "Fire TV Stick 4K (1.ª gen.)" ;;
    AFTSSS)  echo "Fire TV Stick (3.ª gen.)" ;;
    AFTSS)   echo "Fire TV Stick Lite" ;;
    AFTGAZL) echo "Fire TV Cube (3.ª gen.)" ;;
    AFTR)    echo "Fire TV Cube (2.ª gen.)" ;;
    *)       echo "" ;;
  esac
}

local_prefix() {   # «192.168.1» de la red a la que está conectado este ordenador
  local ip="" i
  if command -v ipconfig >/dev/null 2>&1; then
    for i in en0 en1 en2 en3 en4 en5; do ip=$(ipconfig getifaddr "$i" 2>/dev/null); [ -n "$ip" ] && break; done
  fi
  [ -n "$ip" ] || ip=$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p')
  [ -n "$ip" ] || ip=$(hostname -I 2>/dev/null | awk '{print $1}')
  printf '%s' "${ip%.*}"
}

port_open() {
  if [ "$(uname)" = Darwin ]; then nc -z -G 1 -w 1 "$1" 5555 >/dev/null 2>&1
  elif command -v nc >/dev/null 2>&1; then nc -z -w 1 "$1" 5555 >/dev/null 2>&1
  else timeout 1 bash -c "</dev/tcp/$1/5555" >/dev/null 2>&1; fi
}

scan_network() {   # imprime las IP con el puerto ADB abierto
  local prefix tmp i n=0
  prefix=$(local_prefix)
  [ -n "$prefix" ] || { warn "No sé cuál es tu red local."; return 1; }
  tmp=$(mktemp "${TMPDIR:-/tmp}/ftvo.XXXXXX")
  info "Buscando teles en $prefix.1-254 (unos segundos)…" >&2
  for i in $(seq 1 254); do
    ( port_open "$prefix.$i" && echo "$prefix.$i" >> "$tmp" ) &
    n=$((n + 1)); [ $((n % 64)) -eq 0 ] && wait
  done
  wait
  # También las que ya conoce adb o que se guardaron otras veces.
  { cat "$tmp"; connected_devices | sed 's/:5555$//' | grep -E '^[0-9.]+$'
    [ -f "$KNOWN_FILE" ] && cut -d'|' -f1 "$KNOWN_FILE"; } | grep . | sort -u | sort -t. -k4,4n
  rm -f "$tmp"
}

adb_state() { "$ADB" -s "$1:5555" get-state 2>/dev/null | tr -d '\r'; }

describe_tv() {    # $1 = IP → «estado|nombre|descripción»
  local ip="$1" st i manu model nice fos name
  "$ADB" connect "$ip:5555" >/dev/null 2>&1
  for i in 1 2 3; do st=$(adb_state "$ip"); [ -n "$st" ] && break; sleep 1; done
  case "$st" in
    device)
      manu=$("$ADB" -s "$ip:5555" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')
      model=$("$ADB" -s "$ip:5555" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
      fos=$("$ADB" -s "$ip:5555" shell getprop ro.build.version.name 2>/dev/null | tr -d '\r')
      name=$("$ADB" -s "$ip:5555" shell settings get global device_name 2>/dev/null | tr -d '\r')
      [ "$name" = null ] && name=""
      nice=$(model_name "$model"); [ -n "$nice" ] || nice="$manu $model"
      echo "ok|${name:-sin nombre}|$nice · ${fos:-?}" ;;
    unauthorized) echo "pendiente|?|Acepta «¿Permitir depuración?» en esa tele" ;;
    *)            echo "no|?|Responde en el puerto 5555 pero no acepta ADB" ;;
  esac
}

cmd_buscar() {     # escanea, enseña la lista y deja elegir; fija TARGET
  need_adb
  local ips ip line st name desc n i choice
  while true; do
    ips=$(scan_network) || return 1
    if [ -z "$ips" ]; then
      warn "No he encontrado ninguna tele con la depuración ADB activa en tu red."
      info "En la tele: Configuración › Mi Fire TV › Opciones para desarrolladores › Depuración ADB: activada."
      printf 'Escribe la IP a mano (Enter para cancelar): '; read -r ip
      [ -n "$ip" ] || return 1
      connect_ip "$ip"; return 0
    fi
    title "Teles encontradas"
    n=0; LIST=""
    for ip in $ips; do
      line=$(describe_tv "$ip"); st=${line%%|*}; line=${line#*|}; name=${line%%|*}; desc=${line#*|}
      n=$((n + 1)); LIST="$LIST$n|$ip|$st|$name
"
      case "$st" in
        ok)        printf '  %s%2d%s  %-15s %s%s%s  %s\n' "$C_BOLD" "$n" "$C_0" "$ip" "$C_G" "$name" "$C_0" "$desc" ;;
        pendiente) printf '  %s%2d%s  %-15s %s%s%s\n' "$C_BOLD" "$n" "$C_0" "$ip" "$C_Y" "$desc" "$C_0" ;;
        *)         printf '  %s%2d  %-15s %s%s\n' "$C_D" "$n" "$ip" "$desc" "$C_0" ;;
      esac
    done
    if printf '%s' "$LIST" | grep -q '|pendiente|'; then
      info "Las marcadas en amarillo piden permiso: acéptalo en la tele con el mando y pulsa r."
    fi
    printf '\nNúmero de la tele (r = volver a buscar, Enter = cancelar): '; read -r choice
    case "$choice" in
      r|R) continue ;;
      "")  return 1 ;;
    esac
    line=$(printf '%s' "$LIST" | grep "^$choice|") || { warn "Número no válido."; continue; }
    ip=$(printf '%s' "$line" | cut -d'|' -f2); st=$(printf '%s' "$line" | cut -d'|' -f3)
    name=$(printf '%s' "$line" | cut -d'|' -f4)
    if [ "$st" != ok ]; then
      [ "$st" = pendiente ] && { info "Acepta el aviso en la tele y pulsa r para volver a buscar."; continue; }
      warn "Esa no acepta ADB."; continue
    fi
    TARGET="$ip:5555"; DEVICE_SERIAL=""
    mkdir -p "$BASE_DIR"
    { [ -f "$KNOWN_FILE" ] && grep -v "^$ip|" "$KNOWN_FILE"; echo "$ip|$name"; } > "$KNOWN_FILE.tmp" 2>/dev/null
    mv "$KNOWN_FILE.tmp" "$KNOWN_FILE"
    ok "Trabajando con $name ($ip)"
    return 0
  done
}

resolve_target() {
  need_adb
  if [ -n "$TARGET" ]; then
    if ! connected_devices | grep -qx "$TARGET"; then
      case "$TARGET" in *.*) connect_ip "$TARGET" ;; *) die "El dispositivo $TARGET no está conectado." ;; esac
    fi
    return
  fi
  local devs n
  devs=$(connected_devices)
  n=$(printf '%s' "$devs" | grep -c . || true)
  if [ "$n" -eq 1 ]; then TARGET="$devs"; return; fi
  # Ninguna o varias: busca en la red y deja elegir.
  cmd_buscar || die "Sin una tele elegida no puedo seguir. También puedes usar:  -d <IP>"
}

check_fireos() {
  local manu model fos rel
  manu=$(dsh getprop ro.product.manufacturer)
  model=$(dsh getprop ro.product.model)
  fos=$(dsh getprop ro.build.version.name)
  rel=$(dsh getprop ro.build.version.release)
  local nice name; nice=$(model_name "$model"); [ -n "$nice" ] || nice="$manu $model"
  name=$(dsh settings get global device_name); [ "$name" = null ] && name=""
  DEVICE_DESC="${name:+$name · }$nice · ${fos:-Fire OS ?} (Android $rel)"
  if ! dsh pm list packages com.amazon.tv.launcher | grep -q "package:com.amazon.tv.launcher"; then
    if [ "$FORCE" -eq 1 ]; then
      warn "No parece Fire OS ($DEVICE_DESC). Sigo porque usaste --force."
    else
      die "Esto no parece un Fire TV ($DEVICE_DESC). Usa --force si estás seguro."
    fi
  fi
}

# Las copias se asocian al número de serie (la IP puede cambiar con el router).
device_id() {
  if [ -z "${DEVICE_SERIAL:-}" ]; then
    DEVICE_SERIAL=$(dsh getprop ro.serialno)
    [ -n "$DEVICE_SERIAL" ] || DEVICE_SERIAL="$TARGET"
  fi
  printf '%s' "$DEVICE_SERIAL" | tr -c 'A-Za-z0-9._-' '_'
}

load_pkg_state() {
  INSTALLED=$(dsh pm list packages | sed -n 's/^package://p' | sort)
  DISABLED=$(dsh pm list packages -d | sed -n 's/^package://p' | sort)
}
pkg_installed() { printf '%s\n' "$INSTALLED" | grep -qx "$1"; }

# Apps que guardaron una red wifi. Si se desactivan, Android borra esas redes y no vuelven
# al reactivarlas (lo documenta Free My TV). Nunca se desactivan.
load_wifi_owners() {
  WIFI_OWNERS=$(dsh dumpsys wifi | grep -o 'cname=[A-Za-z0-9_.]*' | cut -d= -f2 | sort -u)
}
owns_wifi() { printf '%s\n' "${WIFI_OWNERS:-}" | grep -qx "$1"; }

# Una tele con Fire TV integrado (tiene sintonizador, entradas HDMI propias…) no un Stick o Cube.
# En teles, el sistema también controla entradas y TDT, y un fallo puede dejarlas inservibles.
is_tv() {
  printf '%s\n' "$INSTALLED" | grep -qxE 'com\.amazon\.tv\.channelscan|com\.amazon\.tv\.inputpreference\.service|com\.amazon\.mediatek\.livetv'
}
tv_guard() {
  is_tv || return 0
  [ "$FORCE_TV" -eq 1 ] && { warn "Es una tele con Fire TV integrado. Sigo porque usaste --tele."; return 0; }
  err "Esto es una tele con Fire TV integrado, no un Stick."
  err "En teles, desactivar apps del sistema puede romper el menú, Ajustes y las entradas"
  err "(ya pasó en una Xiaomi con Fire OS 8 y solo se arregló restableciendo de fábrica)."
  die "No se aplica nada. Solo con  --tele  y probando cada app por separado con «desactivar»."
}
pkg_disabled()  { printf '%s\n' "$DISABLED"  | grep -qx "$1"; }

# Comprobación de seguridad tras cada tanda de cambios.
safety_check() {
  local dis
  dis=$(dsh pm list packages -d | sed -n 's/^package://p')
  if printf '%s\n' "$dis" | grep -qx "com.amazon.tv.launcher"; then
    warn "El launcher de Amazon estaba desactivado. Reactivándolo…"
    dsh pm enable com.amazon.tv.launcher >/dev/null
  fi
  local home
  home=$(dsh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)
  case "$home" in
    *"/"*) ok "Pantalla de inicio operativa: $home" ;;
    *) warn "No pude confirmar la pantalla de inicio. Si la tele se queda en negro: ./firetv-optimizer.sh restaurar" ;;
  esac
}

# --------------------------------------------------------------------------
# Comandos
# --------------------------------------------------------------------------
cmd_estado() {
  resolve_target; check_fireos; load_pkg_state
  title "Estado de $DEVICE_DESC"
  echo "  Dispositivo:            $TARGET"
  echo "  Paquetes instalados:    $(printf '%s\n' "$INSTALLED" | grep -c .)"
  echo "  Paquetes desactivados:  $(printf '%s\n' "$DISABLED" | grep -c .)"
  local k
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
    printf "  %-28s %s\n" "$k:" "$(dsh settings get global $k)"
  done
  local last; last=$(latest_backup)
  echo "  Última copia:           ${last:-ninguna}"
  if [ "$(dsh settings get secure user_setup_complete)" = "0" ]; then
    warn "user_setup_complete está a 0: con eso Inicio y el botón de Alexa no responden."
    warn "Arréglalo con:  adb -s $TARGET shell settings put secure user_setup_complete 1"
  fi
  if fireos8; then
    for k in window_animation_scale transition_animation_scale animator_duration_scale; do
      case "$(dsh settings get global $k)" in null|1|1.0|"") ;; *)
        anim_warning; warn "Arréglalo con:  ./firetv-optimizer.sh animaciones 1"; break ;;
      esac
    done
  fi
  safety_check
}

print_plan() {  # $1 = nivel; rellena PLAN_TODO
  local tier="$1" pkg desc state n_todo=0 n_absent=0
  PLAN_TODO=""
  title "Nivel «${tier}»"
  while IFS='|' read -r pkg desc; do
    [ -n "$pkg" ] || continue
    if ! pkg_installed "$pkg"; then
      n_absent=$((n_absent + 1)); continue
    elif is_protected "$pkg"; then
      state="${C_Y}protegido, se omite${C_0}"
    elif pkg_disabled "$pkg"; then
      state="${C_D}ya desactivado${C_0}"
    else
      state="${C_G}se desactivará${C_0}"
      PLAN_TODO="$PLAN_TODO $pkg"
      n_todo=$((n_todo + 1))
    fi
    printf '  %-48s %s\n' "$pkg" "$state"
    printf '  %s\n' "${C_D}  $desc${C_0}"
  done <<EOF
$(list_tier "$tier")
EOF
  [ "$n_absent" -gt 0 ] && printf '  %s\n' "${C_D}($n_absent de la lista no existen en esta tele)${C_0}"
  info "$n_todo paquete(s) por desactivar en este nivel."
}

tiers_for() {
  case "$1" in
    seguro)   echo "seguro" ;;
    moderado) echo "seguro moderado" ;;
    ota)      echo "ota" ;;
    *) die "Nivel desconocido: $1 (usa: seguro, moderado u ota)" ;;
  esac
}

cmd_plan() {
  local level="${1:-seguro}"
  resolve_target; check_fireos; load_pkg_state
  info "$DEVICE_DESC"
  local t; for t in $(tiers_for "$level"); do print_plan "$t"; done
  echo; info "Esto es solo una vista previa. Para aplicarlo:  ./firetv-optimizer.sh aplicar $level"
}

new_backup_file() {
  mkdir -p "$BACKUP_DIR"
  local f="$BACKUP_DIR/$(device_id)_$(date +%Y%m%d-%H%M%S)_$1.txt"
  {
    echo "# firetv-optimizer $VERSION"
    echo "# dispositivo: $TARGET: $DEVICE_DESC"
    echo "# fecha: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "# Líneas PKG = paquetes desactivados por esta ejecución."
    echo "# Líneas ANIM = valor anterior de la animación."
    echo "# Líneas SET = valor anterior de un ajuste. Líneas OPS = permiso anterior de una app."
  } > "$f"
  printf '%s' "$f"
}

latest_backup() {
  ls -1t "$BACKUP_DIR/$(device_id)"_*.txt 2>/dev/null | head -n 1
}

cmd_aplicar() {
  local level="${1:-}"
  [ -n "$level" ] || die "Indica el nivel: seguro, moderado u ota"
  resolve_target; check_fireos; load_pkg_state; tv_guard; load_wifi_owners
  info "$DEVICE_DESC"
  local todo="" t
  for t in $(tiers_for "$level"); do print_plan "$t"; todo="$todo$PLAN_TODO"; done

  if [ "$level" = "ota" ]; then
    echo
    warn "Bloquear actualizaciones también bloquea parches de seguridad."
    warn "Amazon puede rechazar el cambio o revertirlo; si ya hay una actualización descargada,"
    warn "puede instalarse igualmente al reiniciar."
  fi
  if [ "$level" = "moderado" ]; then
    echo
    warn "El nivel moderado desactiva partes de Alexa, Silk, Amazon Kids y perfiles."
    warn "El botón de voz del mando puede dejar de funcionar del todo o en parte."
  fi

  if [ -z "${todo// /}" ]; then ok "No hay nada que cambiar."; return; fi
  if [ "$DRY_RUN" -eq 1 ]; then info "Simulación (--simular): no se ha cambiado nada."; return; fi
  echo
  confirm "¿Desactivar estos paquetes? (se guardará una copia para restaurar)" || { info "Cancelado."; return; }

  local bk; bk=$(new_backup_file "$level")
  local pkg out n_ok=0 n_fail=0
  for pkg in $todo; do
    is_protected "$pkg" && continue   # doble seguro
    if owns_wifi "$pkg"; then warn "$pkg: guardó una red wifi; desactivarlo la borraría. No se toca."; n_fail=$((n_fail + 1)); continue; fi
    out=$(dsh pm disable-user --user 0 "$pkg")
    case "$out" in
      *"new state: disabled"*)
        echo "PKG $pkg" >> "$bk"; ok "$pkg"; n_ok=$((n_ok + 1)) ;;
      *[Pp]rotected*|*SecurityException*|*"not allowed"*)
        warn "$pkg: Amazon no permite desactivarlo"; n_fail=$((n_fail + 1)) ;;
      *)
        warn "$pkg: respuesta inesperada: $(printf '%s' "$out" | head -n 1)"; n_fail=$((n_fail + 1)) ;;
    esac
  done
  echo
  ok "$n_ok desactivado(s), $n_fail sin cambiar."
  info "Copia guardada en: $bk"
  safety_check
  echo
  info "Reinicia la tele para notar el efecto completo:  ./firetv-optimizer.sh reiniciar"
}

cmd_desactivar() {   # desactivar un paquete concreto a mano
  local pkg="${1:-}"
  [ -n "$pkg" ] || die "Indica el paquete."
  resolve_target; check_fireos; load_pkg_state
  pkg_installed "$pkg" || die "$pkg no está instalado."
  if is_protected "$pkg"; then die "$pkg está en la lista de protegidos. No se toca."; fi
  tv_guard; load_wifi_owners
  owns_wifi "$pkg" && die "$pkg guardó una red wifi: desactivarlo la borraría. No se toca."
  [ -n "$(tier_of "$pkg")" ] || warn "$pkg no está en ninguna lista revisada. Hazlo solo si sabes qué es."
  confirm "¿Desactivar $pkg?" || return
  local bk; bk=$(new_backup_file manual)
  local out; out=$(dsh pm disable-user --user 0 "$pkg")
  case "$out" in
    *"new state: disabled"*) echo "PKG $pkg" >> "$bk"; ok "Desactivado. Copia: $bk" ;;
    *) rm -f "$bk"; die "No se pudo: $out" ;;
  esac
  safety_check
}

# Fire OS 8 (Android 11, SDK 30) cierra el menú lateral sin abrir nada con las animaciones por
# debajo de 1x. Documentado por CleanYourTV (Fire TV Stick 4K 2023 con Fire OS 8.1.8.5 y teles
# Hisense con Fire OS 8, probado alternando 1.0 y 0.5).
fireos8() {
  local sdk; sdk=$(dsh getprop ro.build.version.sdk | tr -dc '0-9')
  [ "${sdk:-0}" -ge 30 ]
}
anim_warning() {
  err "En Fire OS 8 las animaciones por debajo de 1x rompen el menú lateral (☰):"
  err "Ajustes, Entradas y el perfil se cierran sin abrir nada."
  err "Lo han comprobado otros en un Fire TV Stick 4K de 2023 y en teles con Fire OS 8."
}

cmd_animaciones() {
  local v="${1:-0.5}"
  case "$v" in 0|0.5|1|1.0|0.25) ;; *) die "Valor no válido: usa 0, 0.25, 0.5 o 1" ;; esac
  resolve_target; check_fireos
  case "$v" in
    1|1.0) ;;
    *) if fireos8; then anim_warning; die "No se cambia nada. Para volver a la normalidad:  ./firetv-optimizer.sh animaciones 1"; fi ;;
  esac
  if [ "$DRY_RUN" -eq 1 ]; then info "Simulación: pondría las animaciones a $v"; return; fi
  local bk; bk=$(new_backup_file animaciones)
  local k old
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
    old=$(dsh settings get global $k)
    echo "ANIM $k ${old:-null}" >> "$bk"
    dsh settings put global $k "$v" >/dev/null
  done
  ok "Animaciones a ${v}x (0 = sin animaciones). Copia: $bk"
}

restore_file() {
  local f="$1" kind a b
  [ -f "$f" ] || die "No existe la copia: $f"
  info "Restaurando desde $f"
  while read -r kind a b; do
    case "$kind" in
      PKG)
        if dsh pm enable "$a" | grep -q "new state: enabled"; then ok "Reactivado $a"; else warn "No se pudo reactivar $a"; fi ;;
      SET)
        # a = espacio, b = "clave valor-anterior"
        local key old; key=${b%% *}; old=${b#* }; [ "$old" = "$b" ] && old=null
        if [ "$old" = "null" ] || [ -z "$old" ]; then dsh settings delete "$a" "$key" >/dev/null
        else dsh settings put "$a" "$key" "$old" >/dev/null; fi
        ok "Ajuste $key → ${old/null/valor de fábrica}" ;;
      OPS)
        local op mode; op=${b%% *}; mode=${b#* }; [ "$mode" = "$b" ] && mode=default
        dsh appops set "$a" "$op" "$mode" >/dev/null
        ok "Permiso $op de $a → $mode" ;;
      ANIM)
        if [ "$b" = "null" ] || [ -z "$b" ]; then dsh settings delete global "$a" >/dev/null
        else dsh settings put global "$a" "$b" >/dev/null; fi
        if [ "$b" = "null" ] || [ -z "$b" ]; then ok "Animación $a → valor de fábrica"; else ok "Animación $a → $b"; fi ;;
    esac
  done < "$f"
  mv "$f" "$f.restaurado"
}

# Método lento y seguro para teles: una app cada vez, comprobando y reiniciando entre una y otra.
# Estado por tele en ~/firetv-optimizer/probados_<serie>.txt:  paquete ok|fallo|reiniciar
probe_file() { printf '%s/probados_%s.txt' "$BASE_DIR" "$(device_id)"; }
probe_state() { [ -f "$(probe_file)" ] && awk -v p="$1" '$1==p{s=$2} END{print s}' "$(probe_file)"; }
probe_mark() { mkdir -p "$BASE_DIR"; echo "$1 $2" >> "$(probe_file)"; }

probe_checklist() {
  cat <<EOF
  Comprueba en la tele, en este orden:
    1. El menú lateral se abre y se mueve bien
    2. Ajustes abre, y también sus apartados (Red, Pantalla y sonido, Preferencias…)
    3. Las entradas: HDMI 1, HDMI 2 y TDT (cambia de canal)
    4. Alexa: una pregunta por voz y una búsqueda por voz
EOF
}

cmd_probar() {
  resolve_target; check_fireos; load_pkg_state; load_wifi_owners
  # 1) ¿Queda una prueba a medias, pendiente del reinicio?
  local pending
  pending=$([ -f "$(probe_file)" ] && awk '{s[$1]=$2} END{for (p in s) if (s[p]=="reiniciar") print p}' "$(probe_file)" | head -n 1)
  if [ -n "$pending" ]; then
    title "Prueba pendiente: $pending (desactivada antes del último reinicio)"
    probe_checklist
    if confirm "¿Después de reiniciar sigue funcionando todo?"; then
      probe_mark "$pending" ok; ok "$pending queda desactivada y apuntada como segura en esta tele."
    else
      dsh pm enable "$pending" >/dev/null; probe_mark "$pending" fallo
      warn "$pending reactivada y vetada en esta tele. Reinicia y comprueba que todo vuelve."
      return
    fi
  fi
  # 2) Siguiente candidata de la lista «tele» (o la indicada).
  local pkg="${1:-}" desc="" p d
  if [ -z "$pkg" ]; then
    while IFS='|' read -r p d; do
      [ -n "$p" ] || continue
      pkg_installed "$p" || continue
      pkg_disabled "$p" && continue
      is_protected "$p" && continue
      owns_wifi "$p" && continue
      [ -n "$(probe_state "$p")" ] && continue
      pkg="$p"; desc="$d"; break
    done <<EOF
$(list_tier tele)
EOF
    [ -n "$pkg" ] || { ok "No quedan apps por probar en esta tele."; return; }
  else
    desc=$(list_tier tele | awk -F'|' -v p="$pkg" '$1==p{print $2}')
    [ -n "$desc" ] || die "$pkg no está en la lista revisada para teles. No se prueba."
    pkg_installed "$pkg" || die "$pkg no está instalada."
    owns_wifi "$pkg" && die "$pkg guardó una red wifi; no se toca."
  fi
  local pid; pid=$(dsh pidof "$pkg")
  title "Probar: $pkg"
  info "$desc"
  if [ -n "$pid" ]; then info "Está en marcha ahora: desactivarla libera memoria."
  else warn "No está en marcha ahora: desactivarla apenas ahorra memoria (sí quita lo que hace al arrancar)."; fi
  [ "$DRY_RUN" -eq 1 ] && { info "Simulación: no se ha cambiado nada."; return; }
  confirm "¿Desactivar solo esta app y comprobar?" || return
  local bk; bk=$(new_backup_file probar)
  local out; out=$(dsh pm disable-user --user 0 "$pkg")
  case "$out" in
    *"new state: disabled"*) echo "PKG $pkg" >> "$bk"; ok "Desactivada $pkg" ;;
    *) rm -f "$bk"; die "No se pudo desactivar: $out" ;;
  esac
  echo
  probe_checklist
  echo
  if confirm "¿Funciona todo?"; then
    probe_mark "$pkg" reiniciar
    ok "Bien. Falta la prueba tras reiniciar:"
    info "  1) Reinicia la tele (opción 8 o «reiniciar»). Si ADB se apaga, reactívalo con Alexa > Ajustes."
    info "  2) Vuelve a ejecutar «probar»: primero te preguntará si todo sigue bien tras el reinicio."
  else
    dsh pm enable "$pkg" >/dev/null; probe_mark "$pkg" fallo
    mv "$bk" "$bk.restaurado"
    warn "Reactivada $pkg y vetada en esta tele. Comprueba que todo vuelve; si no, reinicia."
  fi
}

cmd_privacidad() {
  title "Privacidad: se hace desde los menús de la tele"
  info "Estos ajustes se sincronizan con tu cuenta de Amazon: cambiarlos por ADB no sirve o se"
  info "deshace solo (lo documenta CleanYourTV). Hazlo con el mando:"
  info "  Configuración › Preferencias › Configuración de privacidad: desactiva Datos de uso del"
  info "  dispositivo, Recopilar datos de uso de apps y Anuncios basados en intereses."
  info "  Configuración › Preferencias › Contenido destacado: sin vídeo ni audio automáticos."
  info "Si aplicaste la versión antigua de este comando:  ./firetv-optimizer.sh restaurar privacidad"
}

cmd_privacidad_retirado() {
  resolve_target; check_fireos
  title "Ajustes de privacidad"
  local ns key val desc cur todo=""
  while IFS='|' read -r ns key val desc; do
    [ -n "$ns" ] || continue
    cur=$(dsh settings get "$ns" "$key")
    if [ "$cur" = "$val" ]; then printf '  %s %s\n' "${C_D}ya está${C_0}" "$desc"
    else printf '  %s %s\n' "${C_Y}cambiar${C_0}" "$desc"; todo="$todo$ns|$key|$val|$cur
"; fi
  done <<EOF
$PRIVACY_SETTINGS
EOF
  if [ -z "$todo" ]; then echo; ok "Todo aplicado ya."; return; fi
  if [ "$DRY_RUN" -eq 1 ]; then info "Simulación (--simular): no se ha cambiado nada."; return; fi
  echo
  confirm "¿Aplicar estos ajustes? (se guardará una copia para restaurar)" || { info "Cancelado."; return; }
  local bk; bk=$(new_backup_file privacidad)
  while IFS='|' read -r ns key val cur; do
    [ -n "$ns" ] || continue
    echo "SET $ns $key ${cur:-null}" >> "$bk"
    dsh settings put "$ns" "$key" "$val" >/dev/null && ok "$key = $val"
  done <<EOF
$todo
EOF
  info "Copia guardada en: $bk  (deshacer:  ./firetv-optimizer.sh restaurar)"
}

uid_of() { dsh pm list packages -U "$1" | sed -n "s/^package:$1 uid://p" | head -n 1 | tr -dc '0-9'; }

cmd_blindar() {
  err "«blindar» está retirado: quitar permisos a apps que Amazon protege es de alto riesgo"
  die "(se aplicó el día que la tele Xiaomi dejó de funcionar). No se hace nada."
}

cmd_blindar_retirado() {
  resolve_target; check_fireos; load_pkg_state; tv_guard
  title "Apps de Amazon que no se dejan desactivar: sin permiso de segundo plano"
  local pkg desc todo="" uid
  while IFS='|' read -r pkg desc; do
    [ -n "$pkg" ] || continue
    if ! pkg_installed "$pkg"; then printf '  %-44s %s\n' "$pkg" "${C_D}no está instalada${C_0}"; continue; fi
    if pkg_disabled "$pkg"; then printf '  %-44s %s\n' "$pkg" "${C_D}ya desactivada${C_0}"; continue; fi
    if is_protected "$pkg"; then printf '  %-44s %s\n' "$pkg" "${C_D}protegida, no se toca${C_0}"; continue; fi
    uid=$(uid_of "$pkg")
    if [ -z "$uid" ] || [ "$uid" -lt 10000 ]; then
      printf '  %-44s %s\n' "$pkg" "${C_D}comparte usuario con el sistema, no se toca${C_0}"; continue
    fi
    printf '  %-44s %s\n' "$pkg" "$desc"; todo="$todo $pkg"
  done <<EOF
$SHIELD_PKGS
EOF
  if [ -z "${todo// /}" ]; then echo; ok "No hay nada que cambiar."; return; fi
  echo
  info "Seguirán instaladas y activas, pero no podrán funcionar en segundo plano."
  if [ "$DRY_RUN" -eq 1 ]; then info "Simulación (--simular): no se ha cambiado nada."; return; fi
  confirm "¿Aplicar? (se guardará una copia para restaurar)" || { info "Cancelado."; return; }
  local bk op old; bk=$(new_backup_file blindar)
  for pkg in $todo; do
    for op in $SHIELD_OPS; do
      old=$(dsh appops get "$pkg" "$op" | sed -n "s/^$op: \([a-z]*\).*/\1/p" | head -n 1)
      echo "OPS $pkg $op ${old:-default}" >> "$bk"
      dsh appops set "$pkg" "$op" ignore >/dev/null
    done
    dsh am force-stop "$pkg" >/dev/null
    ok "$pkg"
  done
  info "Copia guardada en: $bk  (deshacer:  ./firetv-optimizer.sh restaurar)"
}

cmd_restaurar() {
  resolve_target; check_fireos
  local f="${1:-}"
  # También acepta el nombre de una tanda: seguro, privacidad, blindar, animaciones…
  if [ -n "$f" ] && [ ! -f "$f" ]; then
    local tag="$f"
    f=$(ls -1t "$BACKUP_DIR/$(device_id)"_*_"$tag".txt 2>/dev/null | head -n 1)
    [ -n "$f" ] || die "No hay ninguna tanda «${tag}» pendiente de deshacer."
  fi
  if [ -z "$f" ]; then
    f=$(latest_backup)
    [ -n "$f" ] || die "No hay copias pendientes para este dispositivo en $BACKUP_DIR"
  fi
  [ "$DRY_RUN" -eq 1 ] && { info "Simulación: restauraría $f"; cat "$f"; return; }
  confirm "¿Deshacer los cambios de $(basename "$f")?" || return
  restore_file "$f"
  safety_check
}

# Deshace TODAS las tandas pendientes (de la más nueva a la más antigua) y reactiva
# cualquier app de Amazon desactivada. Para cuando algo ha ido mal.
cmd_rescate() {
  resolve_target
  local files f
  files=$(ls -1t "$BACKUP_DIR/$(device_id)"_*.txt 2>/dev/null)
  title "Rescate: se deshará todo lo hecho con este script en $TARGET"
  [ -n "$files" ] && printf '  %s\n' $(printf '%s\n' "$files" | xargs -n1 basename)
  confirm "¿Deshacer todo?" || return
  local touched q
  touched=$(cat $files /dev/null 2>/dev/null | awk '$1=="OPS"{print $2}' | sort -u)
  for f in $files; do restore_file "$f"; done
  for q in $touched com.amazon.tv.csapp com.amazon.ftvads.deeplinking com.amazon.device.metrics; do
    dsh cmd appops reset "$q" >/dev/null 2>&1 && ok "Permisos de segundo plano restablecidos: $q"
  done
  local dis p; dis=$(dsh pm list packages -d | sed -n 's/^package://p' | grep '^com\.amazon\.' || true)
  for p in $dis; do dsh pm enable "$p" >/dev/null && ok "Reactivado $p"; done
  # Deja fijas las opciones de desarrollador y la depuración ADB (red y USB).
  dsh settings put global development_settings_enabled 1 >/dev/null
  dsh settings put global adb_enabled 1 >/dev/null
  ok "Opciones de desarrollador y depuración ADB: activadas"
  safety_check
  info "Reinicia para dejarlo limpio:  ./firetv-optimizer.sh reiniciar"
}

cmd_restaurar_todo() {
  resolve_target; check_fireos
  local dis; dis=$(dsh pm list packages -d | sed -n 's/^package://p' | grep '^com\.amazon\.' || true)
  if [ -z "$dis" ]; then ok "No hay paquetes de Amazon desactivados."; return; fi
  title "Paquetes de Amazon desactivados:"; printf '  %s\n' $dis
  [ "$DRY_RUN" -eq 1 ] && return
  confirm "¿Reactivarlos todos?" || return
  local p
  for p in $dis; do dsh pm enable "$p" >/dev/null && ok "Reactivado $p"; done
  local f; for f in "$BACKUP_DIR/$(device_id)"_*.txt; do [ -f "$f" ] && mv "$f" "$f.restaurado"; done
  safety_check
}

cmd_auditar() {
  resolve_target; check_fireos; load_pkg_state
  mkdir -p "$BASE_DIR"
  local f="$BASE_DIR/auditoria_$(device_id)_$(date +%Y%m%d-%H%M%S).txt"
  {
    echo "# Auditoría firetv-optimizer $VERSION: $DEVICE_DESC"
    echo "# $(date '+%Y-%m-%d %H:%M:%S')"
    echo
    echo "## Paquetes de Amazon (estado · nivel asignado)"
    local p st tier
    for p in $(printf '%s\n' "$INSTALLED" | grep '^com\.amazon\.\|^amazon\.'); do
      if pkg_disabled "$p"; then st="desactivado"; else st="activo"; fi
      if is_protected "$p"; then tier="PROTEGIDO"; else tier=$(tier_of "$p"); tier="${tier:-sin clasificar}"; fi
      printf '%-55s %-12s %s\n' "$p" "$st" "$tier"
    done
    echo
    echo "## Otros paquetes instalados por el fabricante o el usuario"
    dsh pm list packages -3 | sed -n 's/^package://p' | sort
    echo
    echo "## Consumo de memoria (top procesos)"
    dsh dumpsys meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | head -n 30
  } > "$f"
  ok "Auditoría guardada en: $f"
  info "Los «sin clasificar» son los que conviene revisar antes de tocar nada."
}

# Solo lectura: busca ajustes y propiedades con pinta de función recortada por software
# (valores 0/false/disabled en claves de vídeo, audio, HDR, códecs, modelo…). No cambia nada.
FLAG_WORDS='feature|enable|disable|support|allow|limit|max|cap|hdr|dolby|dv_|vision|atmos|dts|eac3|ac4|av1|vp9|hevc|4k|2160|uhd|60fps|120|framerate|refresh|match|afr|passthrough|hdmi|cec|audio|video|codec|decoder|premium|pro_|tier|model|variant|sku|experiment|beta|debug|dev_|hidden|lowram|low_ram|perf|ram|gpu|game|vrr|allm|qms|ui_mode'
cmd_explorar() {
  resolve_target; check_fireos
  mkdir -p "$BASE_DIR"
  local f="$BASE_DIR/flags_$(device_id)_$(date +%Y%m%d-%H%M%S).txt" ns
  info "Leyendo propiedades y ajustes (solo lectura, no se cambia nada)…"
  {
    echo "# Explorador de flags · firetv-optimizer $VERSION · $DEVICE_DESC"
    echo "# $(date '+%Y-%m-%d %H:%M:%S')"
    echo "# Marcadas con «>>» las que valen 0 / false / disabled / off."
    echo
    echo "## Propiedades del sistema relacionadas (getprop)"
    dsh getprop | grep -Ei "$FLAG_WORDS" | sed -E 's/^(.*: \[(0|false|disabled|off)\])$/>> \1/'
    for ns in global secure system; do
      echo; echo "## settings $ns relacionados"
      dsh settings list "$ns" | grep -Ei "$FLAG_WORDS" | sort | sed -E 's/^(.*=(0|false|disabled|off|0\.0))$/>> \1/'
    done
    echo; echo "## Funciones que declara el sistema (pm list features)"
    dsh pm list features | sed 's/^feature://' | sort
    echo; echo "## Pantalla: modos, HDR y frecuencia"
    dsh dumpsys display | grep -Ei 'supportedModes|mode ?id|hdrCapabilities|supportedHdrTypes|refreshRate|DisplayDeviceInfo|userPreferredMode|matchContent' | head -n 60
    echo; echo "## HDMI / CEC"
    dsh dumpsys hdmi_control | head -n 60
    echo; echo "## Audio: salidas y formatos"
    dsh dumpsys audio | grep -Ei 'encoded|surround|passthrough|format|hdmi' | head -n 60
    echo; echo "## Códecs de vídeo y audio declarados"
    dsh 'cat /vendor/etc/media_codecs*.xml /system/etc/media_codecs*.xml 2>/dev/null' | grep -Eo '<MediaCodec name="[^"]+"( type="[^"]+")?' | sort -u
    echo; echo "## Feature flags de Amazon visibles por ajustes (claves con «feature» o «flag»)"
    for ns in global secure system; do dsh settings list "$ns" | grep -Ei 'feature|flag' | sed "s/^/$ns: /"; done
    echo; echo "## Propiedades persistentes (persist.*): las únicas que a veces se pueden cambiar sin root"
    dsh getprop | grep '^\[persist\.' 
  } > "$f"
  ok "Informe guardado en: $f"
  info "Líneas marcadas con «>>»: $(grep -c '^>>' "$f")"
  info "Pásamelo y te digo cuáles tiene sentido probar y cómo deshacerlo."
}

# Solo lectura: memoria, procesador, almacenamiento y temperatura en un vistazo.
perf_snapshot() {
  local mem total free avail used pct
  mem=$(dsh cat /proc/meminfo)
  total=$(printf '%s\n' "$mem" | awk '/^MemTotal:/ {print $2}')
  avail=$(printf '%s\n' "$mem" | awk '/^MemAvailable:/ {print $2}')
  free=$(printf '%s\n' "$mem" | awk '/^MemFree:/ {print $2}')
  [ -n "$total" ] && [ -n "$avail" ] || { warn "No pude leer la memoria."; return 1; }
  used=$((total - avail)); pct=$((used * 100 / total))
  title "Memoria RAM"
  printf '  Total:       %5d MB\n' $((total / 1024))
  printf '  En uso:      %5d MB  (%d %%)\n' $((used / 1024)) "$pct"
  printf '  Disponible:  %5d MB  (libre del todo: %d MB; el resto es caché que se libera sola)\n' $((avail / 1024)) $((free / 1024))
  if   [ "$pct" -ge 85 ]; then warn "Memoria muy justa: las apps se cerrarán solas al cambiar entre ellas."
  elif [ "$pct" -ge 70 ]; then info "Memoria algo cargada, pero normal en una tele con Fire OS."
  else ok "Memoria holgada."; fi
  title "Apps que más memoria usan"
  dsh dumpsys meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | grep 'K: ' | head -n 12 \
    | sed -E 's/^ *([0-9,]+)K: ([^ ]+).*/\1 \2/' \
    | while read -r kb name; do kb=${kb//,/}; printf '  %5d MB  %s\n' $((kb / 1024)) "$name"; done
  title "Procesador"
  dsh cat /proc/loadavg | awk '{printf "  Carga media (1, 5 y 15 min): %s  %s  %s\n", $1, $2, $3}'
  printf '  Núcleos: %s\n' "$(dsh 'grep -c ^processor /proc/cpuinfo')"
  dsh top -b -n 1 -m 6 -o %CPU,RES,ARGS 2>/dev/null | sed -n '/%CPU/,$p' | head -n 7 | sed 's/^/  /'
  title "Almacenamiento"
  dsh df -h /data | tail -n 1 | awk '{printf "  Tamaño %s · usado %s · libre %s (%s ocupado)\n", $2, $3, $4, $5}'
  title "Temperatura"
  local t; t=$(dsh 'for z in /sys/class/thermal/thermal_zone*/temp; do cat $z; done 2>/dev/null' | sort -n | tail -n 1)
  if [ -n "$t" ] && [ "$t" -gt 1000 ] 2>/dev/null; then printf '  Zona más caliente: %d °C\n' $((t / 1000))
  elif [ -n "$t" ]; then printf '  Zona más caliente: %s °C\n' "$t"
  else echo "  No disponible"; fi
}

cmd_rendimiento() {
  resolve_target; check_fireos
  if [ "${1:-}" = vivo ]; then
    while true; do clear 2>/dev/null; info "$DEVICE_DESC · $(date '+%H:%M:%S') · Ctrl+C para salir"; perf_snapshot; sleep 5; done
  fi
  info "$DEVICE_DESC"
  perf_snapshot
  echo; info "Para verlo actualizándose cada 5 segundos:  ./firetv-optimizer.sh rendimiento vivo"
}

cmd_limpiar() {
  resolve_target; check_fireos
  dsh am kill-all >/dev/null
  ok "Procesos en segundo plano cerrados (se liberará RAM hasta que vuelvan a arrancar)."
}

cmd_reiniciar() {
  resolve_target
  confirm "¿Reiniciar la tele ahora?" || return
  # Android guarda el estado de las apps en disco unos 10 s después de cada cambio: si se reinicia
  # antes, la tele arranca con el estado anterior y parece que reactivó apps que nunca reactivó.
  info "Guardando cambios en disco antes de reiniciar (15 s)…"
  dsh sync >/dev/null 2>&1 || true
  [ "${FIRETV_NO_WAIT:-0}" = 1 ] || sleep 15
  adb_s reboot && ok "Reiniciando. Vuelve a conectar en ~1 minuto."
}

# --------------------------------------------------------------------------
# Sva Launcher (fork con soporte Fire OS y partidos)
# --------------------------------------------------------------------------
SVA_PKG="com.sva.launcher"
SVA_SERVICE="com.sva.launcher/com.sva.launcher.LauncherOverrideService"
SVA_APK_URL="https://github.com/jonbondani/sva-launcher/releases/download/firetv-latest/sva-firetv.apk"

need_sva() {
  dsh pm list packages "$SVA_PKG" | grep -qx "package:$SVA_PKG" \
    || die "Sva Launcher no está instalado. Instálalo con:  ./firetv-optimizer.sh instalar <archivo.apk>"
}

cmd_instalar() {
  local apk="${1:-}"
  if [ -z "$apk" ]; then
    # Sin archivo: descarga la última versión publicada de Sva para Fire TV.
    command -v curl >/dev/null 2>&1 || die "Falta curl para descargar Sva."
    mkdir -p "$BASE_DIR"; apk="$BASE_DIR/sva-firetv.apk"
    info "Descargando la última Sva…"
    curl -fL --progress-bar -o "$apk" "$SVA_APK_URL" || die "No se pudo descargar $SVA_APK_URL"
  fi
  [ -f "$apk" ] || die "No existe el archivo: $apk"
  resolve_target; check_fireos
  # Reinstalar borra el servicio de accesibilidad de la lista de activos: si estaba, se repone.
  local had_home=0
  dsh settings get secure enabled_accessibility_services | grep -q "$SVA_SERVICE" && had_home=1
  info "Instalando $(basename "$apk")…"
  local out; out=$(adb_s install -r "$apk" 2>&1 | tr -d '\r')
  case "$out" in
    *Success*) ok "Instalado."
      if [ "$had_home" = 1 ]; then
        if dsh settings get secure enabled_accessibility_services | grep -q "$SVA_SERVICE"; then
          ok "El botón Home sigue abriendo Sva."
        else
          info "La actualización quitó el permiso de accesibilidad de Sva: lo repongo."
          cmd_sva_inicio on
        fi
        return
      fi ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      die "Ya hay una versión de Sva firmada con otra clave. Desinstálala primero:  adb -s $TARGET uninstall $SVA_PKG" ;;
    *INSTALL_FAILED_OLDER_SDK*)
      die "Esta tele/stick tiene un Android demasiado antiguo para Sva (necesita Android 8 o superior)." ;;
    *) die "No se pudo instalar: $out" ;;
  esac
  info "Para que el botón Home abra Sva:  ./firetv-optimizer.sh sva-inicio"
}

# Home instantáneo: Sva se reabre con una alarma del sistema; con min_futurity=0 salta
# al momento en vez de tras ~5 s. Se respeta cualquier otra constante que ya hubiera.
set_min_futurity() {
  local cur; cur=$(dsh settings get global alarm_manager_constants)
  [ "$cur" = "null" ] && cur=""
  cur=$(printf '%s' "$cur" | tr ',' '\n' | grep -v '^min_futurity=' | grep . | paste -sd, -)
  [ "$1" = "on" ] && cur="${cur:+$cur,}min_futurity=0"
  if [ -n "$cur" ]; then dsh settings put global alarm_manager_constants "$cur" >/dev/null
  else dsh settings delete global alarm_manager_constants >/dev/null; fi
}

cmd_sva_inicio() {
  local mode="${1:-on}"
  resolve_target; check_fireos; need_sva
  local current; current=$(dsh settings get secure enabled_accessibility_services)
  [ "$current" = "null" ] && current=""
  local others; others=$(printf '%s' "$current" | tr ':' '\n' | grep -v "^$SVA_SERVICE$" | grep . | paste -sd: -)
  if [ "$mode" = "off" ]; then
    set_min_futurity off
    # Sin este permiso Sva no puede volver a activarse sola.
    dsh pm revoke "$SVA_PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || true
    if [ -n "$others" ]; then dsh settings put secure enabled_accessibility_services "$others" >/dev/null
    else dsh settings delete secure enabled_accessibility_services >/dev/null; fi
    ok "El botón Home vuelve a abrir el inicio de Amazon."
    return
  fi
  local value="$SVA_SERVICE"; [ -n "$others" ] && value="$others:$SVA_SERVICE"
  dsh settings put secure enabled_accessibility_services "$value" >/dev/null
  dsh settings put secure accessibility_enabled 1 >/dev/null
  set_min_futurity on
  # Permite a Sva reponer el botón Home si una actualización o un cierre forzado lo quita
  # (solo si lo tenías activado; si lo desactivas tú, se queda desactivado).
  if dsh pm grant "$SVA_PKG" android.permission.WRITE_SECURE_SETTINGS 2>&1 | grep -qi "exception\|error"; then
    warn "Esta versión de Sva no puede repararse sola (actualízala). El botón Home funciona igual."
  fi
  if dsh settings get secure enabled_accessibility_services | grep -q "$SVA_SERVICE"; then
    ok "Activado: al pulsar Home se abrirá Sva (el inicio de Amazon sigue instalado y activo)."
    info "Para deshacerlo:  ./firetv-optimizer.sh sva-inicio off"
  else
    warn "Fire OS no aceptó el cambio. Actívalo a mano en Configuración › Accesibilidad si aparece Sva."
  fi
}

# Enlaces privados (lista M3U, calendario): van directos a Sva en la tele y se confirman en pantalla.
# No se guardan en este Mac ni en ningún repositorio.
sva_enviar() {   # $1 = extra, $2 = enlace, [$3 = extra, $4 = enlace]
  case "$2${4:-}" in *"'"*) die "El enlace no puede llevar comillas simples." ;; esac
  case "$2" in http://*|https://*|webcal://*) ;; *) die "El enlace tiene que empezar por https://" ;; esac
  resolve_target; need_sva
  if [ -n "${3:-}" ]; then
    dsh am start -n "$SVA_PKG/.MainActivity" --es "$1" "'$2'" --es "$3" "'$4'" >/dev/null
  else
    dsh am start -n "$SVA_PKG/.MainActivity" --es "$1" "'$2'" >/dev/null
  fi
  ok "Enviado a la tele. Confirma con «Guardar» en la pantalla."
}
cmd_sva_lista() {
  [ -n "${1:-}" ] || die "Uso: sva-lista <enlace M3U> [enlace guía EPG]"
  if [ -n "${2:-}" ]; then sva_enviar set_playlist "$1" set_guide "$2"; else sva_enviar set_playlist "$1"; fi
}
cmd_sva_calendario() {
  [ -n "${1:-}" ] || die "Uso: sva-calendario <enlace iCal de Google Calendar>"
  sva_enviar set_calendar "$1"
}

cmd_sva_equipo() {
  local name="${1:-}" url="${2:-}"
  [ -n "$name" ] && [ -n "$url" ] || die "Uso: sva-equipo \"Nombre\" <enlace-ical>"
  resolve_target; need_sva
  dsh am start -n "$SVA_PKG/.MatchesActivity" --es add_team "'$name|$url'" >/dev/null
  ok "Enviado a la tele: $name. Mira el aviso en pantalla para confirmar."
}

cmd_sva_quitar_equipo() {
  local name="${1:-}"
  [ -n "$name" ] || die "Uso: sva-quitar-equipo \"Nombre\""
  resolve_target; need_sva
  dsh am start -n "$SVA_PKG/.MatchesActivity" --es remove_team "'$name'" >/dev/null
  ok "Quitado de la tele: $name"
}

cmd_conectar() {
  need_adb
  [ -n "${1:-}" ] || die "Uso: conectar <IP>"
  connect_ip "$1"
}

cmd_listas() {
  local t
  for t in seguro moderado ota; do
    title "Nivel «${t}»"
    list_tier "$t" | while IFS='|' read -r p d; do printf '  %-48s %s\n' "$p" "$d"; done
  done
  title "Protegidos (nunca se tocan)"
  printf '%s\n' "$PROTECTED_EXACT" | grep . | sed 's/^/  /'
  echo "  + todo lo que encaje con: $PROTECTED_REGEX"
}

cmd_ayuda() {
cat <<EOF
${C_BOLD}firetv-optimizer $VERSION${C_0}: debloat reversible para Fire OS 8

${C_BOLD}Uso:${C_0} ./firetv-optimizer.sh [opciones] <comando> [argumentos]
     (sin comando abre un menú)

${C_BOLD}Comandos${C_0}
  buscar               Escanea la red, enseña el modelo de cada tele y deja elegir
  conectar <IP>        Conecta por red con la tele (puerto 5555 por defecto)
  estado               Modelo, versión, nº de paquetes desactivados, animaciones
  auditar              Guarda un informe de todos los paquetes de Amazon y su RAM
  listas               Muestra las listas que usa el script
  plan <nivel>         Vista previa sin cambiar nada (seguro | moderado | ota)
  aplicar <nivel>      Desactiva los paquetes del nivel y guarda copia
                         seguro   → telemetría, anuncios, compras, promos
                         moderado → seguro + Alexa, Silk, Kids, perfiles…
                         ota      → bloqueo de actualizaciones (aparte, opcional)
  desactivar <paquete> Desactiva uno concreto (nunca un protegido)
  privacidad           Dice qué cambiar en los menús (por ADB no sirve: va con tu cuenta)
  blindar              Quita el segundo plano a la telemetría que Amazon no deja desactivar
  animaciones [v]      Velocidad de animaciones: 0, 0.25, 0.5 (defecto) o 1. En Fire OS 8 solo 1
  explorar             Informe de flags/ajustes ocultos y capacidades (solo lectura)
  rendimiento [vivo]   Memoria, procesador, almacenamiento y temperatura (vivo = cada 5 s)
  limpiar              Cierra procesos en segundo plano
  restaurar [copia]    Deshace la última copia (o la indicada)
  restaurar <tanda>    Deshace la última tanda de ese tipo (seguro, privacidad, blindar…)
  probar [paquete]     Teles: desactiva UNA app de la lista revisada y te guía para comprobarla
  rescate              Deshace TODO lo hecho con el script en esta tele
  restaurar-todo       Reactiva TODOS los paquetes de Amazon desactivados
  reiniciar            Reinicia la tele

${C_BOLD}Sva Launcher${C_0}
  instalar [apk]                 Instala o actualiza Sva (sin archivo: descarga la última)
  sva-inicio [off]               El botón Home abre Sva al instante (off = vuelve el de Amazon)
  sva-equipo "Nombre" <enlace>   Añade o cambia el calendario iCal de un equipo
  sva-quitar-equipo "Nombre"     Quita un equipo de los partidos
  sva-lista <m3u> [guía]         Manda a Sva una lista de canales M3U (y su guía); se confirma en la tele
  sva-calendario <ical>          Manda a Sva tu calendario de Google (dirección iCal); se confirma en la tele

${C_BOLD}Opciones${C_0}
  -d, --dispositivo <IP>  Tele a usar (también: variable FIRETV)
  -n, --simular           No aplica nada, solo muestra lo que haría
  -y, --si                No pide confirmación
      --force             Permite continuar si no detecta Fire OS
      --tele              Permite desactivar apps en una tele con Fire TV integrado (bajo tu riesgo)

Copias en: $BACKUP_DIR
EOF
}

menu_line() {   # el relleno se calcula en caracteres, no en bytes (tildes)
  local label="$2"; while [ ${#label} -lt 17 ]; do label="$label "; done
  printf '  %s%-2s%s %s %s%s%s\n' "$C_BOLD" "$1" "$C_0" "$label" "$C_D" "$3" "$C_0"
}
menu_head() { printf '\n %s%s%s %s%s%s\n' "$C_B" "$1" "$C_0" "$C_D" "$2" "$C_0"; }

menu_detalle() {
cat <<EOF

${C_BOLD}Orden recomendado${C_0}
  0. Busca y elige la tele: el script escanea tu red y te dice el modelo de cada una.
  1. Mira antes de tocar: Estado, Auditoría y Vista previa no cambian nada.
  2. Limpia: Nivel seguro → Privacidad → Blindar → Animaciones → Reiniciar.
     Cada paso enseña lo que va a hacer y pide confirmación antes.
  3. Usa la tele un día o dos. Si todo va bien y quieres más, Nivel moderado.
  4. Sva: Instalar y después «Home abre Sva». Puedes hacerlo en cualquier momento.
  Si algo va raro: Deshacer la última tanda (repítelo para ir hacia atrás).

${C_BOLD}Qué hace cada opción${C_0}
  Buscar teles    Busca en tu red los aparatos con la depuración ADB activa, se conecta
                  y enseña nombre y modelo. La primera vez la tele pide permiso.
  Estado          Modelo, versión de Fire OS, apps desactivadas y animaciones. Solo lee.
  Auditoría       Guarda un informe con todas las apps de Amazon, su estado y la RAM. Solo lee.
  Rendimiento     RAM en uso y disponible, apps que más memoria gastan, carga del procesador,
                  espacio libre y temperatura. Útil para comparar antes y después. Solo lee.
  Vista previa    Lista las apps que desactivaría el nivel seguro. Solo lee.
  Nivel seguro    Desactiva telemetría, informes de fallos, el reconocimiento de pantalla (ACR),
                  el ID publicitario, compras, promos, Freevee y miniTV.
                  No tiene efectos visibles. Se desactivan (pm disable-user), nunca se borran.
  Privacidad      Explica qué desactivar en los menús de privacidad de la tele. No cambia nada:
                  esos ajustes van con tu cuenta de Amazon y por ADB no sirven.
  Blindar         Las apps de telemetría que Amazon no deja desactivar siguen instaladas,
                  pero sin permiso para funcionar en segundo plano. No toca actualizaciones.
  Animaciones     Pone las animaciones a 0.5x: los menús responden antes. Solo Fire OS 7:
                  en Fire OS 8 rompe el menú lateral y el script se niega.
  Reiniciar       Necesario para notar del todo lo anterior.
  Nivel moderado  Además desactiva partes de Alexa, Silk, Amazon Kids, perfiles y Fling.
                  El botón de voz del mando puede fallar.
  Bloquear OTA    Desactiva las actualizaciones del sistema. No recomendado: pierdes parches.
  Liberar memoria Cierra procesos en segundo plano. Es puntual, vuelven a arrancar.
  Explorar flags  Informe de ajustes ocultos, HDR, códecs y modos de pantalla. Solo lee.
  Instalar Sva    Descarga la última Sva de GitHub (o usa un APK tuyo) y la instala.
  Home abre Sva   El botón Home del mando abre Sva al instante. Amazon sigue instalado.
  Home de Amazon  Deshace lo anterior.
  Deshacer        Revierte la última tanda guardada (apps, ajustes y permisos).
  Reactivar todo  Reactiva todas las apps de Amazon desactivadas, sean de la tanda que sean.
EOF
}

menu_pause() { printf '\n%sPulsa Enter para volver al menú…%s' "$C_D" "$C_0"; read -r _; }

cmd_menu() {
  resolve_target; check_fireos
  while true; do
    printf '\n%s firetv-optimizer %s %s· %s%s\n' "$C_BOLD" "$VERSION" "$C_D" "$DEVICE_DESC" "$C_0"
    printf ' %sSigue los pasos en orden. Todo se puede deshacer.%s\n' "$C_D" "$C_0"
    menu_head "PASO 0 · ELEGIR TELE" "(${TARGET%:5555})"
    menu_line 0 "Buscar teles"    "Escanea tu red, dice el modelo de cada una y eliges"
    menu_head "PASO 1 · MIRAR" "(no cambia nada)"
    menu_line 1 "Estado"          "Modelo, versión y cuántas apps hay desactivadas"
    menu_line 2 "Auditoría"       "Informe de todas las apps de Amazon y su memoria"
    menu_line p "Rendimiento"     "Memoria, procesador, espacio y temperatura ahora mismo"
    menu_line 3 "Vista previa"    "Qué desactivaría el nivel seguro, sin tocar nada"
    menu_head "PASO 2 · LIMPIAR" "(recomendado, en este orden)"
    menu_line 4 "Nivel seguro"    "Telemetría, anuncios, compras y promos. Sin efectos visibles"
    menu_line 5 "Privacidad"      "Qué cambiar en los menús de la tele (por ADB no sirve)"
    menu_line 6 "Blindar"         "RETIRADO: alto riesgo, no hace nada"
    menu_line 7 "Animaciones"     "Menús más ágiles (0.5x). Solo Fire OS 7"
    menu_line 8 "Reiniciar"       "Para notar el efecto completo"
    menu_head "PASO 3 · OPCIONAL" "(tras usar la tele un par de días)"
    menu_line t2 "Probar una app" "Teles: una app cada vez, comprobando y reiniciando (lo seguro)"
    menu_line 9 "Nivel moderado"  "Además Alexa, Silk, Kids, perfiles. La voz puede fallar"
    menu_line m "Liberar memoria" "Cierra procesos en segundo plano (puntual)"
    menu_line e "Explorar flags"  "Ajustes ocultos, HDR, códecs, modos de pantalla (solo lee)"
    menu_line o "Bloquear OTA"    "Sin actualizaciones del sistema. No recomendado"
    menu_head "SVA LAUNCHER" ""
    menu_line i "Instalar Sva"    "Descarga e instala la última versión"
    menu_line h "Home abre Sva"   "El botón Home abre Sva al instante"
    menu_line a "Home de Amazon"  "El botón Home vuelve al inicio de Amazon"
    menu_head "DESHACER" ""
    menu_line u "Deshacer"        "Revierte la última tanda (repite para ir más atrás)"
    menu_line t "Reactivar todo"  "Reactiva todas las apps de Amazon desactivadas"
    echo
    menu_line "?" "Ayuda"         "Orden recomendado y explicación de cada opción"
    menu_line q "Salir"           ""
    printf '\nOpción: '; read -r o
    echo
    # Cada acción va en un subshell: un error no cierra el menú.
    case "$o" in
      0) if cmd_buscar; then check_fireos || true; fi ;;
      1) ( cmd_estado ) ;;            p|P) ( cmd_rendimiento ) ;;            2) ( cmd_auditar ) ;;         3) ( cmd_plan seguro ) ;;
      4) ( cmd_aplicar seguro ) ;;    5) ( cmd_privacidad ) ;;      6) ( cmd_blindar ) ;;
      7) ( cmd_animaciones 0.5 ) ;;   8) ( cmd_reiniciar ) ;;       9) ( cmd_aplicar moderado ) ;;
      t2|T2) ( cmd_probar ) ;;
      m|M) ( cmd_limpiar ) ;;         e|E) ( cmd_explorar ) ;;      o|O) ( cmd_aplicar ota ) ;;
      i|I) printf 'Ruta de un APK (Enter = descargar la última): '; read -r f
           f=${f%\"}; f=${f#\"}; f=${f%\'}; f=${f#\'}; f=${f% }
           ( cmd_instalar "$f" ) ;;
      h|H) ( cmd_sva_inicio on ) ;;   a|A) ( cmd_sva_inicio off ) ;;
      u|U) ( cmd_restaurar ) ;;       t|T) ( cmd_restaurar_todo ) ;;
      \?) menu_detalle ;;
      q|Q) exit 0 ;;
      *) warn "Opción no válida"; continue ;;
    esac
    menu_pause
  done
}

# --------------------------------------------------------------------------
# Argumentos
# --------------------------------------------------------------------------
ARGS=()
CMD=""
while [ $# -gt 0 ]; do
  case "$1" in
    -d|--dispositivo|--device) TARGET="${2:-}"; shift 2; continue ;;
    -n|--simular|--dry-run)    DRY_RUN=1 ;;
    -y|--si|--yes)             ASSUME_YES=1 ;;
    --force)                   FORCE=1 ;;
    --tele)                    FORCE_TV=1 ;;
    -h|--help)                 CMD="ayuda" ;;
    *) if [ -z "$CMD" ]; then CMD="$1"; else ARGS+=("$1"); fi ;;
  esac
  shift
done
# Conserva los argumentos con espacios ("Real Madrid"). Compatible con bash 3.2.
if [ ${#ARGS[@]} -gt 0 ]; then set -- "${ARGS[@]}"; else set --; fi

case "${CMD:-menu}" in
  menu)            cmd_menu ;;
  conectar)        cmd_conectar "$@" ;;
  buscar)          cmd_buscar ;;
  estado)          cmd_estado ;;
  auditar)         cmd_auditar ;;
  listas)          cmd_listas ;;
  plan)            cmd_plan "$@" ;;
  aplicar)         cmd_aplicar "$@" ;;
  desactivar)      cmd_desactivar "$@" ;;
  animaciones)     cmd_animaciones "$@" ;;
  privacidad)      cmd_privacidad ;;
  blindar)         cmd_blindar ;;
  limpiar)         cmd_limpiar ;;
  rendimiento)     cmd_rendimiento "$@" ;;
  explorar)        cmd_explorar ;;
  restaurar)       cmd_restaurar "$@" ;;
  restaurar-todo)  cmd_restaurar_todo ;;
  rescate)         cmd_rescate ;;
  probar)          cmd_probar "$@" ;;
  reiniciar)       cmd_reiniciar ;;
  sva-lista)       cmd_sva_lista "$@" ;;
  sva-calendario)  cmd_sva_calendario "$@" ;;
  instalar)        cmd_instalar "$@" ;;
  sva-inicio)      cmd_sva_inicio "$@" ;;
  sva-equipo)      cmd_sva_equipo "$@" ;;
  sva-quitar-equipo) cmd_sva_quitar_equipo "$@" ;;
  ayuda|help)      cmd_ayuda ;;
  *) err "Comando desconocido: $CMD"; cmd_ayuda; exit 1 ;;
esac

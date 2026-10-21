# firetv-optimizer

Debloat **reversible** para televisores y sticks con **Fire OS 8**. Probado contra la estructura de paquetes del Xiaomi Fire TV F Pro. Se ejecuta desde el Mac por red, con `adb`.

- Solo **desactiva** paquetes; nunca desinstala.
- Cada cambio queda guardado en `~/firetv-optimizer/copias/` y se puede deshacer.
- El script lleva una lista de **protegidos** que nunca toca: launcher, ajustes, teclado, mando, entradas HDMI y TDT, cuenta Amazon, WebView, Bluetooth y configuración inicial.
- Después de cada cambio comprueba que la pantalla de inicio sigue funcionando.

## 1. Preparar el Mac

```sh
brew install android-platform-tools
chmod +x firetv-optimizer.sh
```

## 2. Activar ADB en la tele (una sola vez)

1. **Configuración › Dispositivo y software › Acerca de**. En sticks el menú se llama **Mi Fire TV**.
2. Pulsa **7 veces** sobre el nombre del televisor. Aparecerá «Ya eres desarrollador».
3. Vuelve atrás y entra en **Opciones para desarrolladores**. Activa **Depuración ADB**.
4. Apunta la IP de la tele. Está en **Acerca de › Red**.

La primera vez que conectes, la tele mostrará «¿Permitir depuración?». Acepta, y marca la opción de recordar este equipo si quieres que no vuelva a preguntar.

## 3. Uso recomendado (en este orden)

```sh
./firetv-optimizer.sh buscar                 # escanea la red, dice el modelo de cada tele y eliges
./firetv-optimizer.sh auditar                 # informe de qué hay en tu tele
./firetv-optimizer.sh plan seguro             # vista previa, no cambia nada
./firetv-optimizer.sh aplicar seguro          # telemetría, anuncios, compras, promos
./firetv-optimizer.sh animaciones 0.5         # interfaz más ágil
./firetv-optimizer.sh privacidad              # anuncios personalizados, métricas, vídeo automático
./firetv-optimizer.sh blindar                 # telemetría que Amazon no deja desactivar
./firetv-optimizer.sh reiniciar
```

Usa la tele un día o dos. Si todo va bien y quieres ir más lejos:

```sh
./firetv-optimizer.sh plan moderado
./firetv-optimizer.sh aplicar moderado        # Alexa, Silk, Amazon Kids, perfiles…
```

No hace falta saber la IP: `buscar` (o la opción 0 del menú) escanea tu red local en el puerto ADB 5555, se conecta a cada aparato que responda y enseña su nombre y modelo (por ejemplo «Salón · Fire TV Stick 4K Max (2.ª gen.)»). La primera vez, cada tele pide permiso en pantalla. Las elegidas se recuerdan en `~/firetv-optimizer/teles.txt`. Si ya sabes la IP, `-d 192.168.1.19` delante de cualquier comando.

Sin argumentos (`./firetv-optimizer.sh`), el script abre un **menú** ordenado por pasos (Mirar → Limpiar → Opcional, más Sva y Deshacer), con una línea explicando cada opción. Pulsa `?` para ver el orden recomendado y una explicación más larga. Desde el menú, «Instalar Sva» descarga sola la última versión. Con `./firetv-optimizer.sh ayuda` ves todos los comandos. Con `-n` simulas cualquier comando sin aplicar nada.

### Niveles

| Nivel | Qué desactiva | Efecto secundario |
|---|---|---|
| `seguro` | Telemetría, informes de fallos, compras, promociones, Freevee y miniTV | Ninguno visible |
| `moderado` | Lo anterior más partes de Alexa, notificaciones de Amazon, Silk, Amazon Kids, selector de perfiles, Fling… | El botón de voz puede fallar; no hay perfiles ni Amazon Kids |
| `ota` | Actualizaciones automáticas del sistema | **Pierdes parches de seguridad.** Amazon puede rechazarlo o revertirlo |

`ota` va por separado y nunca se aplica con los otros niveles.

### Privacidad y «blindar»

- `privacidad` escribe los ajustes de Amazon de seguimiento publicitario, anuncios por intereses, métricas de uso y vídeo automático de los destacados. Guarda el valor anterior de cada uno. Las claves están comprobadas en Fire OS 7; en Fire OS 8 algunas pueden no tener efecto, así que conviene revisar también el apartado 5.
- `blindar` es para las apps de telemetría que Amazon no deja desactivar (`metrics`, `crashmanager`, `rdmapplication`, `ftvads.deeplinking`, `csapp`): siguen instaladas, pero se les quita el permiso de funcionar en segundo plano. No toca las actualizaciones del sistema.

### Explorar flags ocultos

`./firetv-optimizer.sh explorar` es de solo lectura. Guarda en `~/firetv-optimizer/flags_*.txt` las propiedades y ajustes de vídeo, audio, HDR, códecs y modelo, y marca con `>>` los que valen 0/false/off. Sirve para buscar funciones recortadas por software. Las propiedades `ro.*` no se pueden cambiar sin root; los ajustes `settings` y algunas `persist.*` sí.

Las dos ideas de privacidad vienen de [dumbtv](https://github.com/humblemuzzu/dumbtv) (MIT) y las dos se deshacen con `restaurar`.

## Seguridad tras el incidente de la tele Xiaomi (octubre de 2026)

Aplicar el nivel seguro ampliado, Privacidad y Blindar en una Xiaomi F Pro con Fire OS 8.1.8.5 dejó sin funcionar el menú lateral, Ajustes y las entradas. Reactivarlo todo no lo arregló y hubo que restablecer de fábrica. Desde la versión 1.7.0:

- **Las teles quedan bloqueadas.** Si el script detecta una tele con Fire TV integrado (sintonizador, entradas propias), no desactiva nada. Solo con `--tele`, y la recomendación es hacerlo app a app con `desactivar`, comprobando menú, Ajustes y entradas, y reiniciando entre una y otra.
- **El nivel seguro vuelve a la lista original**, sin las apps añadidas desde dumbtv.
- **Nunca se desactiva una app que guardó una red wifi**, porque Android borraría esa red y no vuelve al reactivarla.
- **Más apps protegidas:** todo lo de MediaTek y las piezas de Fire OS que llevan las entradas, el HDMI-CEC, la TDT y los ajustes rápidos.
- **Privacidad y Blindar están marcados como experimentales.** Se aplicaron el día del incidente y no está descartado que influyeran.

### Teles: el método «probar» (desde la 1.8.0)

En una tele con Fire TV integrado no se aplica ningún nivel de golpe. `./firetv-optimizer.sh probar` (opción t2 del menú) desactiva **una sola app** de una lista revisada para Fire OS 8 en teles con el inicio de Amazon, ordenada de más beneficio y menos riesgo a menos:

1. Te dice si la app está en marcha (si no lo está, desactivarla apenas ahorra memoria).
2. La desactiva y te pide comprobar el menú lateral, Ajustes y sus apartados, las entradas (HDMI y TDT) y Alexa.
3. Si algo falla, la reactiva al momento y la veta para esa tele.
4. Si todo va bien, te pide reiniciar y volver a ejecutar `probar`, que primero confirma que tras el reinicio sigue todo bien.

El resultado de cada app se guarda en `~/firetv-optimizer/probados_<serie>.txt`.

Tras investigar el incidente: `client.metrics.api`, `dp.logger`, `logan`, `tv.developer.dataservice`, `connectivitydiag` y `perfc` pasan a la lista de nunca tocar (otras teles los desactivan solo cuando sustituyen el inicio de Amazon), y «Blindar» queda retirado (quitar permisos a apps que Amazon protege es de alto riesgo). `rescate` ahora también restablece esos permisos (`cmd appops reset`), que reactivar no deshace.

Antes de nada de esto, lo que más acelera la tele sin riesgo: quitar el vídeo automático de los destacados, desinstalar ES File Explorer y las apps que no uses, y cambiar el salvapantallas de anuncios.

## 4. Deshacer

```sh
./firetv-optimizer.sh restaurar          # deshace la última tanda (repítelo para ir más atrás)
./firetv-optimizer.sh restaurar-todo     # reactiva TODO lo de Amazon que esté desactivado
```

**Si la tele se queda en negro o se comporta raro**, ADB sigue funcionando. Ejecuta `restaurar-todo` y reinicia. El último recurso es el restablecimiento de fábrica: mantén pulsados **Atrás + derecha** del mando durante 10–15 segundos.

## 5. Ajustes que conviene cambiar a mano en la tele

`privacidad` intenta aplicar los dos primeros por ADB, pero en Fire OS 8 no está garantizado. Compruébalos aquí:

- **Preferencias › Contenido destacado**: desactiva la reproducción automática de vídeo y audio.
- **Preferencias › Configuración de privacidad**: desactiva Datos de uso del dispositivo, Recopilar datos de uso de apps y Anuncios basados en intereses.
- **Preferencias › Notificaciones**: desactiva las de Amazon.
- **Aplicaciones › Administrar aplicaciones**: desinstala de forma normal las apps de streaming que no uses.

## Limitaciones

- **No puedo deshacer la actualización de la interfaz.** Fire OS no permite volver a una versión anterior.
- **No se puede cambiar el launcher de verdad.** Amazon bloquea los launchers alternativos. Con Sva, `sva-inicio` hace que el botón Home la abra al instante encima del inicio de Amazon, que sigue instalado.
- Los nombres de paquete pueden cambiar entre versiones de Fire OS. El script **ignora** los que no existen en tu tele y no inventa nada. Si el informe de `auditar` muestra paquetes «sin clasificar» que te interesen, revísalos antes de desactivarlos con `desactivar <paquete>`.
- Algunas actualizaciones de Fire OS reactivan paquetes. Basta con volver a ejecutar `aplicar`.

## Seguridad

Cuando termines, **desactiva la Depuración ADB** en la tele. Mientras está activa, cualquier equipo de tu red puede pedir acceso.

# Sva en Fire TV (rama `firetv`)

Esta rama adapta Sva Launcher a Fire OS y añade **Partidos**: los próximos encuentros de tus equipos en la pantalla de inicio, y al pulsar uno se abre la app donde lo ves.

## Cambios respecto al original

- **Botón Home en Fire OS.** El servicio de accesibilidad de Sva ahora reconoce el inicio de Amazon (`com.amazon.tv.launcher`). Fire OS no deja elegir otro launcher, así que Sva se reabre en cuanto aparece el de Amazon. El de Amazon **sigue instalado y activo**; no hay que desactivarlo.
- **Partidos.** Icono de balón arriba a la derecha, junto a Red y Ajustes.
  - Cada equipo usa un **calendario público iCal**, como el de FotMob o el del propio club.
  - Equipos por defecto: Real Madrid, Málaga y Sevilla, sin enlace todavía. Puedes añadir o quitar los que quieras.
  - La **tarjeta central del inicio** muestra el partido en directo, o el siguiente si empieza en menos de 36 horas. Si está en directo, al pulsarla se abre directamente la app.
  - La competición se deduce del texto del evento: LaLiga, LaLiga Hypermotion, Champions, Europa League, Conference, Copa del Rey, Supercopa, Mundial de Clubes o amistosos.
  - La primera vez que abras un partido de una competición, Sva te pregunta con qué app lo ves y lo recuerda. Para cambiarla, mantén pulsado el partido o entra en «Apps».
  - Si un partido sale en los calendarios de dos de tus equipos, solo aparece una vez.

- **Juegos Android en la pestaña Juegos.** Además de la biblioteca NES, aparecen los juegos Android instalados, como los ports nativos de proyectos de decompilación. Los que se declaran juego salen solos; los demás se añaden con la tarjeta «+ Añadir juego Android». Mantén pulsada una tarjeta para quitarla.

## Instalar en un Fire TV Stick

Requisito: Fire OS 7 u 8, que corresponde a Android 9 o superior. Los sticks muy antiguos con Fire OS 6 (Android 7.1) no son compatibles.

**Opción A: desde el Mac**, con `firetv-optimizer.sh`:

```sh
./firetv-optimizer.sh conectar <IP-del-stick>
./firetv-optimizer.sh instalar sva-firetv.apk
./firetv-optimizer.sh sva-inicio            # el botón Home abre Sva
```

**Opción B: sin ordenador**, con la app Downloader en el propio stick:

1. Instala **Downloader** (de AFTVnews) desde la Appstore de Amazon.
2. Ve a **Configuración › Mi Fire TV › Opciones para desarrolladores › Instalar apps desconocidas** y activa Downloader.
3. En Downloader, escribe:
   `https://github.com/jonbondani/sva-launcher/releases/download/firetv-latest/sva-firetv.apk`
4. Instala y abre Sva.
5. Para el botón Home, mira si aparece Sva en **Configuración › Accesibilidad**. Si no aparece, hay que activarlo por ADB con `sva-inicio`.

## Añadir equipos

Desde la tele: **Partidos › Equipos › Añadir equipo**, o pulsa un equipo y elige **Cambiar calendario**.

Desde el ordenador (más cómodo que teclear una URL con el mando):

```sh
./firetv-optimizer.sh sva-equipo "Real Madrid" "https://…/calendario.ics"
./firetv-optimizer.sh sva-quitar-equipo "Sevilla"
```

**Dónde sacar el enlace:** en fotmob.com, abre la página del equipo y pulsa **Sincronizar calendario**. Copia el enlace `.ics` o `webcal://`. Cualquier otro calendario iCal público también sirve.

## Actualizar

Cada push a la rama `firetv` vuelve a publicar `firetv-latest`. Sin el secreto `FIRETV_DEBUG_KEYSTORE_BASE64`, cada compilación se firma con una clave distinta. En ese caso, para actualizar hay que desinstalar primero (`adb uninstall com.sva.launcher`), y se pierden los ajustes. Usa **Ajustes › Copia de seguridad** antes de desinstalar.

## Juegos (ports de decompilaciones)

Casi todos los ports necesitan **archivos de tu propia copia del juego**. Ni este repo ni Sva descargan ROMs.

1. Instala el APK oficial del port, sacado de la página de *Releases* de su proyecto, con `./firetv-optimizer.sh instalar juego.apk` o con Downloader.
2. Copia los archivos de tu juego donde indique el port.
3. Ábrelo desde **Juegos**. Si no aparece, usa «+ Añadir juego Android».

Necesitas un **mando Bluetooth**; el mando de la tele no sirve para jugar. Los ports 3D (N64 en adelante) pueden ir justos en un Fire TV Stick.

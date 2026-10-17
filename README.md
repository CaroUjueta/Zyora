# Zyora

Aplicación de escritorio en Java que une una **lista de tareas** (al estilo Microsoft To Do) y un
**calendario** (al estilo Outlook/Google Calendar) en un solo lugar: las tareas no viven separadas de
tu agenda, sino que puedes **arrastrarlas al calendario para reservarles tiempo**.

## Funciones

**Inicio**
- Saludo, un calendario pequeño del mes (los días con algo agendado llevan un punto; al hacer clic
  en un día se abre en el calendario) y **Mis tareas**: lo pendiente para los próximos 7 días
  (también lo vencido) y lo completado hoy, con etiquetas «Hoy», «Mañana» o la fecha.
- La primera vez Zyora pregunta tu nombre para el saludo; haz clic en el saludo para cambiarlo.
  Se guarda en cada aparato por separado (en el PC, en `~/.zyora/profile.properties`).
- En el teléfono, Inicio es la primera pestaña, con lo mismo que en el PC.

**Tareas**
- Listas propias con color (crear; renombrar, cambiar el color o eliminar con clic derecho, o
  manteniendo presionada la lista en el teléfono).
- **Sublistas**: clic derecho → «Nueva sublista». Una lista muestra también las tareas de sus
  sublistas, y en la barra lateral se pueden plegar con ▾.
- Listas inteligentes: **Mi día**, **Importante**, **Planeado** y **Todas las tareas**.
- Pasos (subtareas), notas, fecha de vencimiento, marcar como importante y completadas.

**Calendario**
- Vistas de **mes**, **semana** y **día**, con línea de la hora actual.
- Eventos con hora o de todo el día (también de varios días), color, ubicación y notas.
- Doble clic en un hueco para crear un evento; arrastra eventos para moverlos.

**La unión de las dos**
- Las tareas con fecha de vencimiento aparecen en el calendario como elementos de todo el día.
- Panel **Tareas sin programar**: arrastra una tarea a una hora y se convierte en un bloque de
  tiempo (con la duración que elijas). Puedes completarla desde el propio calendario.
- **Mi día** muestra junto a tus tareas la agenda de hoy (eventos + bloques de tareas).
- Arrastra un bloque a la fila "Todo el día" para quitarle la hora y dejarlo solo con fecha.

**Widget de escritorio (PC)**
- Pulsa **Widget de escritorio** abajo en la barra lateral: aparece un calendario del mes flotando
  en el escritorio, igual al widget del teléfono (los eventos y tareas de cada día, «Hoy», ‹ ›).
- Arrástralo a donde quieras; al hacer clic en un día se abre Zyora en ese día. Clic derecho:
  abrir Zyora, dejarlo siempre encima de las ventanas, ocultarlo o salir de Zyora.
- Mientras el widget esté abierto, Zyora sigue funcionando aunque cierres la ventana principal
  (y el teléfono puede seguir sincronizando). Se recuerda si estaba abierto y dónde, en
  `~/.zyora/widget.properties`.
- Para que aparezca al encender el PC: crea un acceso directo a `Zyora.exe --widget` (o
  `javaw -jar ...-all.jar --widget`) en la carpeta que abre `Win + R` → `shell:startup`. Con
  `--widget` arranca solo el widget, sin la ventana principal.

## Requisitos

- JDK 21 o superior (por ejemplo [Eclipse Temurin](https://adoptium.net/)).
- No hace falta instalar Maven: el proyecto incluye el wrapper (`mvnw` / `mvnw.cmd`).

## Ejecutar

```bash
# Windows
mvnw.cmd javafx:run

# Linux / macOS
./mvnw javafx:run
```

Crear un `.jar` ejecutable (funciona en el sistema operativo donde se compila):

```bash
./mvnw package
java -jar desktop/target/zyora-0.1.0-SNAPSHOT-all.jar
```

Pruebas: `./mvnw test`

Crear `Zyora.exe` para Windows (en Windows, con un JDK 21 que traiga `jpackage`):

```bat
mvnw.cmd package -DskipTests -Djavafx.platform=win
mkdir jpackage-in & copy desktop\target\zyora-0.1.0-SNAPSHOT-all.jar jpackage-in\
jpackage --type app-image --name Zyora --input jpackage-in --main-jar zyora-0.1.0-SNAPSHOT-all.jar ^
    --main-class com.zyora.Launcher --app-version 0.1.0 --icon desktop\packaging\zyora.ico --dest out
```

Queda en `out\Zyora\` (el `.exe` necesita las carpetas `app` y `runtime` que lo acompañan).

## App de Android

En `android/` está la app para el teléfono (Kotlin + Jetpack Compose) con un **widget de mes**
para la pantalla de inicio. Usa el mismo modelo y la misma lógica que el escritorio: el módulo
Gradle `android/core` compila directamente los archivos de `core/`.

- Abre la carpeta `android/` en Android Studio y ejecuta la configuración `app`.
- O desde la terminal: `cd android && ./gradlew :app:installDebug` (con un teléfono o emulador conectado).
- Widget: mantén presionada la pantalla de inicio → Widgets → Zyora · Mes. Las flechas cambian
  de mes y al tocar un día se abre la app en ese día.
- **Notificación de tareas de hoy**: queda fija en la barra de notificaciones mientras tengas
  tareas pendientes para hoy (también las vencidas, y con la hora si tienen bloque en el
  calendario). Se actualiza sola con cada cambio, a medianoche y al reiniciar el teléfono, y si la
  deslizas para quitarla vuelve a aparecer. Desaparece al completar las tareas del día; para
  apagarla: **Listas → Notificación de tareas de hoy**. La primera vez Android pide permiso para
  mostrar notificaciones.

## Sincronizar el teléfono y el PC

Por Wi‑Fi, sin cuentas ni servicios externos. Los dos aparatos deben estar en la misma red.

1. En el PC abre Zyora y pulsa **Sincronizar con el teléfono** (abajo en la barra lateral).
   Aparecen la dirección del PC y un código de 6 dígitos.
2. En el teléfono ve a **Listas → Sincronizar con el PC**, escribe esa dirección y el código y
   pulsa **Sincronizar ahora**.

Desde entonces el teléfono se sincroniza solo cada vez que abres la app y al salir de ella si
cambiaste algo; también puedes pulsar «Sincronizar ahora» cuando quieras. Los cambios van en los
dos sentidos: gana la edición más reciente de cada tarea, lista o evento, y lo que borras en un
lado se borra en el otro. Las listas con el mismo nombre (como «Tareas») se juntan en una sola.

- Zyora tiene que estar abierta en el PC (escucha en el puerto 47821). La primera vez Windows
  puede preguntar si permite el acceso a la red: acepta para **redes privadas**.
- Ejecuta la app de escritorio en Windows (`java -jar ...-all.jar`), no dentro de WSL: desde WSL
  la dirección que se muestra es interna y el teléfono no llega a ella.
- El código se guarda en `~/.zyora/sync.properties`; bórralo para generar uno nuevo.
- Usa la hora de cada aparato para decidir qué cambio es más reciente, así que conviene que ambos
  tengan la hora automática.

## Datos

Todo se guarda automáticamente en `~/.zyora/zyora.json` (en Windows,
`C:\Users\<usuario>\.zyora\zyora.json`). Para usar otro archivo:
`-Dzyora.data=ruta/al/archivo.json`.

Antes Zyora se llamaba Planora y guardaba todo en `~/.planora/planora.json`. La primera vez que se
abre con el nombre nuevo, mueve esa carpeta a `~/.zyora` sola (datos, nombre, código de
sincronización y widget).

## Estructura

```
core/        modelo y lógica, sin interfaz (Java 17, para poder usarlo también en Android)
└── src/main/java/com/zyora/
    ├── model/     Task, Step, TaskList, CalendarEvent, Syncable, Tombstone
    └── service/   Planner (lógica y notificación de cambios), JsonPlannerStore (persistencia),
                   ChangeTracker (fecha de última modificación), SyncMerger (mezcla los datos de
                   dos aparatos), SyncProtocol, SmartList, AgendaItem
desktop/     la app de escritorio (JavaFX)
└── src/main/java/com/zyora/
    ├── ZyoraApp.java / Launcher.java   arranque de la app
    ├── sync/      SyncServer: recibe al teléfono por Wi‑Fi
    └── ui/        Inicio, ventana principal, barra lateral, listas de tareas, detalle de tarea,
                   calendario (MonthView, TimeGridView, DayColumn), ventanas flotantes y
                   DesktopWidget (el widget de escritorio)
desktop/src/main/resources/com/zyora/
    ├── icons/     ícono de la ventana (zyora-*.png) y nav/: íconos de la barra lateral
    └── ui/        zyora.css
desktop/packaging/   appPc.png (diseño original) y zyora.ico, el ícono del .exe
android/packaging/   appCelLight.png y appCelDark.png, diseños originales del ícono del teléfono
                     (en la app: res/drawable-nodpi y res/drawable-night-nodpi, modo oscuro)
```

Para sincronizar, cada tarea, lista y evento guarda cuándo cambió por última vez (`updatedAt`) y
los borrados quedan registrados (`deleted` en el JSON), así el otro aparato puede aplicar los
mismos cambios.

## Ideas para seguir

- Eventos y tareas recurrentes.
- Recordatorios / notificaciones del sistema.
- Redimensionar bloques arrastrando su borde.
- Sincronización con Google Calendar / Outlook (CalDAV o Microsoft Graph).
- Tema oscuro y búsqueda.
- Instalador `.msi` (con WiX Toolset).

## Tecnologías

Java 21 · JavaFX 21 · Jackson · JUnit 5 · Maven

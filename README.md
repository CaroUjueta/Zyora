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
  Se guarda solo en este PC, en `~/.planora/profile.properties`.

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
java -jar desktop/target/planora-0.1.0-SNAPSHOT-all.jar
```

Pruebas: `./mvnw test`

Crear `Zyora.exe` para Windows (en Windows, con un JDK 21 que traiga `jpackage`):

```bat
mvnw.cmd package -DskipTests -Djavafx.platform=win
mkdir jpackage-in & copy desktop\target\planora-0.1.0-SNAPSHOT-all.jar jpackage-in\
jpackage --type app-image --name Zyora --input jpackage-in --main-jar planora-0.1.0-SNAPSHOT-all.jar ^
    --main-class com.planora.Launcher --app-version 0.1.0 --icon desktop\packaging\planora.ico --dest out
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
- El código se guarda en `~/.planora/sync.properties`; bórralo para generar uno nuevo.
- Usa la hora de cada aparato para decidir qué cambio es más reciente, así que conviene que ambos
  tengan la hora automática.

## Datos

Todo se guarda automáticamente en `~/.planora/planora.json` (en Windows,
`C:\Users\<usuario>\.planora\planora.json`). Para usar otro archivo:
`-Dplanora.data=ruta/al/archivo.json`.

## Estructura

```
core/        modelo y lógica, sin interfaz (Java 17, para poder usarlo también en Android)
└── src/main/java/com/planora/
    ├── model/     Task, Step, TaskList, CalendarEvent, Syncable, Tombstone
    └── service/   Planner (lógica y notificación de cambios), JsonPlannerStore (persistencia),
                   ChangeTracker (fecha de última modificación), SyncMerger (mezcla los datos de
                   dos aparatos), SyncProtocol, SmartList, AgendaItem
desktop/     la app de escritorio (JavaFX)
└── src/main/java/com/planora/
    ├── PlanoraApp.java / Launcher.java   arranque de la app
    ├── sync/      SyncServer: recibe al teléfono por Wi‑Fi
    └── ui/        Inicio, ventana principal, barra lateral, listas de tareas, detalle de tarea,
                   calendario (MonthView, TimeGridView, DayColumn) y ventanas flotantes
desktop/src/main/resources/com/planora/
    ├── icons/     ícono de la ventana (planora-*.png) y nav/: íconos de la barra lateral
    └── ui/        planora.css
desktop/packaging/   appPc.png (diseño original) y planora.ico, el ícono del .exe
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

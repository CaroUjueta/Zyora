# Planora

Aplicación de escritorio en Java que une una **lista de tareas** (al estilo Microsoft To Do) y un
**calendario** (al estilo Outlook/Google Calendar) en un solo lugar: las tareas no viven separadas de
tu agenda, sino que puedes **arrastrarlas al calendario para reservarles tiempo**.

## Funciones

**Tareas**
- Listas propias con color (crear, renombrar, eliminar con clic derecho).
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
java -jar target/planora-0.1.0-SNAPSHOT-all.jar
```

Pruebas: `./mvnw test`

## Datos

Todo se guarda automáticamente en `~/.planora/planora.json` (en Windows,
`C:\Users\<usuario>\.planora\planora.json`). Para usar otro archivo:
`-Dplanora.data=ruta/al/archivo.json`.

## Estructura

```
src/main/java/com/planora/
├── PlanoraApp.java / Launcher.java   arranque de la app
├── model/     Task, Step, TaskList, CalendarEvent
├── service/   Planner (lógica y notificación de cambios), JsonPlannerStore (persistencia),
│              SmartList, AgendaItem
└── ui/        ventana principal, barra lateral, listas de tareas, detalle de tarea,
               calendario (MonthView, TimeGridView, DayColumn) y diálogo de eventos
```

## Ideas para seguir

- Eventos y tareas recurrentes.
- Recordatorios / notificaciones del sistema.
- Redimensionar bloques arrastrando su borde.
- Sincronización con Google Calendar / Outlook (CalDAV o Microsoft Graph).
- Tema oscuro y búsqueda.
- Instalador nativo con `jpackage`.

## Tecnologías

Java 21 · JavaFX 21 · Jackson · JUnit 5 · Maven

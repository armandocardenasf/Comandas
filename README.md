# Comandas 2.1.0 con modo corporativo opcional

Ver [README_CORPORATIVO.md](README_CORPORATIVO.md) para instalación, activación, sincronización y firma. Se conserva debajo la documentación de las versiones anteriores.

# Comandas 2.0.5

Correcciones de navegación, pagos en dólares, mesas compartidas, avisos e inventario por producto. Consultar [guía de esta versión](docs/ACTUALIZACION_2_0_5.md).

# Comandas 2.0.4

Correcciones de cobro, login, listas, calendarios, reportes e impresión. Consultar [guía de esta versión](docs/ACTUALIZACION_2_0_4.md).

# Actualización 2.0.3

Logo de empresa, Botella predeterminada, detalle de comanda en reportes, cortes generales/detallados, impresión ESC/POS y exportación XLSX. Consultar [guía de actualización](docs/ACTUALIZACION_2_0_3.md).

# Comandas

Proyecto Kotlin y Jetpack Compose para abrir en Android Studio. No utiliza Flutter ni el motor Dart.

## Requisitos

- Android Studio con soporte para Android Gradle Plugin 8.9.2 (Meerkat o una versión posterior compatible).
- JDK 17 completo, Android SDK Platform 35 y Build Tools 35.0.0.
- Android 12 / API 31 o superior. Se conserva la columna SQLite calculada `sale_payments.total_pago`, disponible en SQLite 3.31+; Android API 31 incluye SQLite 3.32.
- Para impresión: impresora ESC/POS de Bluetooth clásico RFCOMM/SPP, emparejada en Ajustes de Android. No se asume compatibilidad con BLE o impresoras propietarias.

## Abrir y ejecutar

1. Extraer el ZIP y abrir la carpeta `Comandas` con File → Open.
2. Seleccionar JDK 17 en Settings → Build Tools → Gradle → Gradle JDK.
3. Instalar SDK 35 si Android Studio lo solicita y sincronizar Gradle.
4. Seleccionar un dispositivo Android 12+ y ejecutar `app`.
5. En una instalación nueva se incluyen los datos iniciales del proyecto: `admin / 1234` y `cajero / 5678`. Cambiar esas contraseñas desde Permisos antes de operar.
6. Crear las mesas desde Catálogos: el proyecto original no incluye mesas iniciales.

Comandos:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Windows: usar `gradlew.bat`.

La aplicación tiene ID `com.enverolab.comandas`, diferente de `com.example.comandas`, para instalarse junto al POS original sin reemplazarlo. La compilación release requiere configurar una firma propia; el proyecto no incluye claves privadas.

## Diseño original restaurado — versión 2.0.2

El Menú vuelve a incluir el armado de la comanda: tarjetas con presentaciones,
resumen, cliente, personas, descuentos, notas y totales MXN/USD. Comandas recupera
Todas/Abiertos/Pagados, tarjetas y detalle con acciones. Se restauran la paleta,
la navegación con logotipo, la imagen del acceso y el marco de cobros.

En teléfono, usa las pestañas **Menú / Resumen**, **Órdenes / Detalle** y
**Resumen / Pago** para acceder a los paneles. En tableta horizontal se muestran
simultáneamente. Para guardar una comanda nueva utiliza **Continuar** desde el
resumen; para una existente, **Guardar cambios**.

Ver `docs/RESTAURACION_DISENO_ORIGINAL.md` para la comparación con Flutter.

## Orientación y pantallas

Se admite vertical y horizontal según la rotación del dispositivo. En ventanas
estrechas el botón **Secciones** abre la navegación; en ventanas amplias aparece
la barra lateral. La comanda en preparación conserva sus datos al cambiar de
sección y al recrear la actividad. Las listas y formularios se desplazan para
acceder a los controles en ventanas bajas.

Para actualizar una instalación nativa existente, utiliza la misma firma de la
instalación actual y conserva su almacenamiento. **No desinstales la aplicación
para resolver un conflicto de firma**: primero exporta un respaldo ZIP y compila
el proyecto con la clave utilizada en esa instalación. El APK generado en esta
entrega usa una firma debug de evaluación; no se dispone de las claves privadas
de instalaciones compiladas en otras computadoras ni de entregas anteriores.

## Módulos

- Menú: búsqueda por nombre, descripción o código; categorías; variantes y fotografías.
- Comandas: alta, edición, eliminación de comandas abiertas, mesa, cliente, personas, descuentos por artículo, descuento general y observaciones.
- Cobros: métodos configurables, pagos mixtos, MXN/USD según método, factor de conversión, propinas por importe o porcentaje, cambio, pagos gratuitos, división exacta por personas y asignación por cantidades de artículos.
- Inventario: entradas, salidas, indicadores de existencias e historial persistente; descarga de variantes según `factor_descarga`.
- Permisos y catálogos: roles admin, super_cajero y cajero; CRUD de usuarios, empleados, clientes, productos, variantes, categorías, mesas, métodos, menú, empresa y folios.
- Historial: consulta y reimpresión de ventas.
- Reportes: ventas, comandas, productos, categorías, pagos y cierres; día, semana, mes y rango; gráficas de barras, CSV, PDF e impresión.
- Cierres: parcial/total y prohibición de nuevos cortes después de un cierre total del mismo día.
- Respaldos: exportación/importación de base e imágenes, validación de esquema y copia previa.

## Migrar los datos reales

El RAR recibido no incluye una base `restaurant.db` operativa ni las imágenes capturadas en dispositivos. Incluye código, recursos gráficos y datos iniciales. Por ello, las ventas y existencias de una instalación real deben exportarse desde el dispositivo original.

Ver `docs/MIGRACION_DATOS.md`. No desinstalar el original ni borrar su almacenamiento hasta comprobar el resultado.

## Verificación y límites

El estado exacto de compilación y pruebas está registrado en `docs/VALIDACION.md`. La validación con impresora física, la aceptación visual y la comparación con una base de producción deben realizarse antes de sustituir el POS en operación.

Se conserva el modelo de datos original con adiciones en tablas `native_*`. Se han corregido problemas de integridad y cálculo, detallados en `docs/FLUJOS_Y_COMPATIBILIDAD.md`. La interfaz nativa reproduce los flujos funcionales; no es una copia visual idéntica de Flutter.

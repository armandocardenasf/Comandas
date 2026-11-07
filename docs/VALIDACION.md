# Validación — 2.0.5

- Compilación :app:assembleDebug y Android Lint correctos; paquete mx.ensenada.comandas, versionCode 13 y versión 2.0.5.
- Corrida completa de 60 pruebas: cero fallos y errores. Incluye 29 de repositorio, 19 de dominio/formato/cálculos y 12 de interfaz.
- Verificación adicional de los cuatro recorridos de navegación/pagos/inventario con comprobación de la existencia actualizada en la lista: cuatro aprobados, sin fallos. Los XML consolidados conservan la ejecución más reciente de cada caso.
- Doce escenarios de interfaz repartidos entre 1280×720, 800×360, 360×800 y 800×1280 dp; Robolectric API 31, gráficos nativos. Se verifican reapertura de Detalles, entrada directa a Pago, conversión y limpieza de montos, referencia MXN de USD, pagos mixtos, guardado transaccional, accesos rápidos, aviso con cierre temporal, inventario por producto, captura de entrada, filtro temporal y opciones de exportación.
- Mesas: se comprueban varias comandas abiertas en una mesa, cobro/eliminación de una sin liberar las demás y cambio de una comanda a otra mesa.
- Inventario: fechas de movimientos, filtros por producto/rango y precio de venta actual; salida mayor a existencia rechazada sin movimiento falso.
- Lint: cero errores y siete advertencias informativas (dos ConfigurationScreenWidthHeight, ObsoleteSdkInt, UnusedResources, MonochromeLauncherIcon y dos UseKtx). No se ocultan errores con baseline.
- Esquema: 18 tablas originales, 44 registros iniciales, 14 productos, 18 variantes, claves externas e integridad correctas. SQLite permanece en versión 11; no hay cambios de dependencias.
- APK firmado y certificado comparado con el APK 2.0.4 entregado: coincide. Android 12/API 31 mínimo, target SDK 35.
- En docs/evidencias/pruebas-completas está el informe de las 60 pruebas; pruebas-inventario-adicional contiene las cuatro comprobaciones posteriores y tests contiene los XML consolidados.
- Pendiente: prueba con dispositivo e impresora físicos; los ensayos simulados no verifican conexión Bluetooth ni alimentación/corte real del papel.

## Registro anterior

# Validación — 2.0.4

- :app:assembleDebug, :app:testDebugUnitTest y :app:lintDebug: BUILD SUCCESSFUL.
- 51 pruebas, cero fallos y cero errores: 26 de repositorio, 15 de dominio/importes, dos de formato de tickets y ocho de interfaz.
- Se verificaron agrupaciones por día, producto, categoría y comanda, pagos, exclusión de comandas abiertas de las ventas, descuentos y residuo de centavos, importación y respaldos.
- Tickets: importes alineados a la última columna en 32, 42 y 48 caracteres; ajuste de nombres e importes extensos sin truncarlos.
- Interfaz: cuatro tamaños simulados, acceso, catálogo, confirmación/cancelación de borrado, calendario y captura manual, comanda, cobro, rotación, ventanas de reportes y exportación.
- Inspección de capturas: login, cobro, catálogos y reportes; se incluyen capturas de la ejecución en docs/evidencias/interfaz.
- Lint: cero errores y seis advertencias (ConfigurationScreenWidthHeight, ObsoleteSdkInt, UnusedResources, MonochromeLauncherIcon y dos UseKtx). No se usa baseline para ocultar errores.
- Esquema original: 18 tablas, 44 registros iniciales, 14 productos, 18 variantes, integridad y claves externas correctas.
- APK verificado: mx.ensenada.comandas, versionCode 12, versionName 2.0.4, Android 12 mínimo. La firma de evaluación cambió respecto a 2.0.3; conservar la firma propia al actualizar una instalación.
- Sin cambios de versiones de bibliotecas ni de versión SQLite 11. Se mantienen el identificador, los datos y el respaldo de logotipo.
- Pendiente: prueba con la impresora y el dispositivo físicos. Los ensayos Robolectric no sustituyen Bluetooth, codificación, alimentación y corte real del papel.

## Registro anterior

# Validación — 2.0.3

- Compilación debug y Android Lint ejecutados para la actualización.
- 47 pruebas: 15 de dominio, 24 de repositorio y ocho de interfaz en cuatro tamaños simulados.
- Nuevas comprobaciones: reporte con descuento general, límite temporal del corte, estructura de exportación XLSX, conservación del logo en respaldo y restauración, selección inicial de Botella, apertura/cierre del detalle de comanda y ventana de reporte.
- Verificador de esquema: 18 tablas originales, 44 registros iniciales, 14 productos, 18 variantes; integridad y claves externas correctas.
- Se conserva applicationId, versión SQLite 11 y las dependencias de 2.0.2. Se agrega Empresa.logo sin eliminar datos.
- Firma debug comparada con el APK anterior: coincide. Las instalaciones compiladas por el usuario requieren su propia firma original.
- Pendiente: impresora física y revisión en el dispositivo operativo; los ensayos Robolectric no sustituyen la verificación Bluetooth y de alimentación/corte de papel.

Los reportes XML/HTML de esta entrega están en docs/evidencias. Para uso y alcance de los cortes históricos, consultar ACTUALIZACION_2_0_3.md.

## Validación anterior (2.0.2)

# Validación — 2.0.2-original-ui

## Resultado ejecutado

- Compilación Android :app:assembleDebug: correcta, versionCode 10, paquete mx.ensenada.comandas.
- :app:testDebugUnitTest: 45 pruebas, cero fallos y cero errores.
- :app:lintDebug: cero errores y ocho advertencias informativas: cinco GradleDependency por versiones más recientes disponibles, ConfigurationScreenWidthHeight, ObsoleteSdkInt y MonochromeLauncherIcon. No se cambian las versiones de bibliotecas en esta corrección de interfaz ni se ocultan errores mediante baseline.
- tools/verify_schema.py: correcto; 18 tablas heredadas, 44 registros iniciales, 14 productos, 18 variantes, integridad, claves externas y columna calculada total_pago.
- APK debug firmado y verificado; Android 12/API 31 mínimo, target SDK 35.

## Cobertura

- 15 pruebas de importes/contraseñas, incluida variación de cantidades y descuento proporcional con redondeo decimal.
- 22 pruebas de repositorio, incluidas transacciones, cobros mixtos, rollback, asignación de artículos, inventario, cierres, importación/migración, filtros abierta/pagada/todas y acciones rápidas acotadas para cajeros. El total de tarjetas se comprueba aunque monto_por_pagar heredado esté desactualizado, sin modificar ese dato.
- Ocho pruebas de interfaz: dos flujos en cuatro tamaños simulados: 1280×720 dp, 800×360 dp, 360×800 dp y 800×1280 dp. Se usa Robolectric API 31 con gráficos nativos.

El flujo principal elige Botella y Copa como líneas distintas; elimina una; configura mesa y personas; aplica cantidades, descuento por artículo, descuento general y nota; registra un cliente; cambia entre secciones; recrea la actividad conservando el borrador; guarda y edita la orden; comprueba que tarjeta y detalle coincidan en $228.00; cobra y consulta pagados con edición y pago bloqueados. Otra recreación comprueba los pagos preparados.

Los controles se ejercitan mediante acciones semánticas Compose; no equivalen a una prueba táctil o de teclado físico en todos los dispositivos. Se conservan las pruebas transaccionales de la entrega anterior. Los gráficos nativos permitieron capturar e inspeccionar el diseño del propio código, sin generar maquetas.

## Evidencias

Reportes XML, HTML de Lint, resultado de compilación y capturas en docs/evidencias. Las capturas utilizan datos iniciales y registros de prueba, no información de producción. Se revisaron menú horizontal/vertical, resumen vertical y tarjetas/detalle con importes coincidentes.

## Límites y actualización

Pendiente aceptación con el dispositivo y los datos operativos del negocio: teclado, tamaño de letra, cámara, permisos Bluetooth, impresora, papel, PDF/CSV y respaldo real.

Se conserva el esquema SQLite 11 y el ID de aplicación. No desinstalar ni borrar datos para resolver diferencias de firma. El APK de evaluación de esta entrega usa un certificado debug distinto del de la entrega anterior; para actualizar aquella instalación se debe compilar o firmar con la clave que ya utiliza. No se incluyen claves privadas.

## SHA-256 del APK

`a90233503c58c2430cdce82d8feefc660d1053cb975d488347dcb7534f9b451a`

## Nombre del proyecto

El proyecto Gradle y la carpeta raíz se denominan Comandas. Se verificó con
`gradlew projects` y `gradlew :app:assembleDebug`: compilación correcta después
del cambio de nombre. La evidencia está en evidencias/cambio-nombre.txt.
Los reportes anteriores conservan las rutas de su ejecución histórica.
El SHA-256 de esta documentación corresponde al APK de evaluación publicado
con la versión 2.0.2; este cambio actualiza el proyecto fuente.

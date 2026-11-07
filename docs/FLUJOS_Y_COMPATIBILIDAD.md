# Flujos revisados y decisiones de compatibilidad

## Flujo de operación

Acceso local → validar usuario activo → cargar catálogos → abrir menú → seleccionar productos/variantes → indicar mesa, personas y cliente → aplicar descuentos y observaciones → guardar comanda abierta → editar o recuperar → preparar pagos → confirmar → registrar venta y artículos → registrar pagos y asignaciones → descargar inventario → marcar comanda pagada → liberar mesa → consultar o imprimir ticket → reportar ventas → realizar corte.

El commit del cobro contiene todas las escrituras: folio, venta, detalle, pagos, asignaciones, stock, movimientos y estado de comanda. Un fallo revierte todas. `sales.folio_comanda` UNIQUE y la comprobación del estado impiden cobrar dos veces.

## Equivalencias

| Flutter | Android nativo |
|---|---|
| main.dart, AuthWrapper, login_screen/login_form | MainActivity, PosApp/Login, PosRepository.login |
| responsive_layout, sidenavbar | Navegación lateral y módulos Compose |
| home, product providers, grid_products | Menú, búsqueda, selector de variantes y editor de comanda |
| order_provider, order_repository, items/summary | PosRepository.saveOrder, Draft, Line, Totals |
| payments_provider, payment_method_section, payment_dialog | PaymentScreen, PaymentDialog, Payment |
| equal_split_section | Totals.split y pagos editables por persona |
| item_split_section | Asignaciones de cantidades por artículo en cada pago |
| inventario_provider, consultas | Inventory y native_inventory_moves |
| permisos, create_user_form, user_table | Catálogos de usuarios, autorización por rol en repositorio |
| catalogo, show_catalog_form, settings_dialog | Formularios por esquema, referencias, Empresa/Folios |
| reportes_repository/provider, gráficas | Reportes SQL y barras Compose |
| printing/pdf/csv | PdfDocument, PrintManager, FileProvider y CSV |
| blue_thermal_printer local + esc_pos_utils | BluetoothSocket RFCOMM y ESC/POS, CP850 |
| image_picker | Selección de imágenes y captura mediante aplicación de cámara |
| shared_preferences | Preferencias privadas nativas para ID de sesión e impresión |
| sqflite | SQLiteOpenHelper, esquema original y migración 10 → 11 |

## Bibliotecas fijadas

| Elemento | Versión / decisión |
|---|---|
| AGP | 8.9.2 |
| Gradle Wrapper | 8.11.1 |
| JDK | 17 |
| Kotlin y plugin Compose | 2.1.20, misma versión |
| Compose BOM | 2025.04.01 |
| Activity Compose | 1.10.1 |
| Lifecycle | 2.9.0 |
| Core KTX | 1.16.0 |
| Coroutines Android | 1.10.2 |
| SDK compilación / target | 35 / 35 |
| SDK mínimo | 31 |
| Persistencia, PDF, ZIP y Bluetooth | APIs de plataforma, sin plugins Flutter |
| JUnit / Robolectric / AndroidX Test Core | 4.13.2 / 4.14.1 / 1.6.1; pruebas, no APK |

Las versiones se fijan deliberadamente; no se usa `+` ni se solicita instalar automáticamente la versión más reciente. La resolución efectiva y compilación se informan en VALIDACION.md.

Referencias oficiales:

- https://developer.android.com/build/releases/agp-8-9-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/compose-kotlin
- https://developer.android.com/reference/android/database/sqlite/package-summary
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/reference/android/graphics/pdf/PdfDocument
- https://developer.android.com/training/printing/custom-docs
- https://docs.gradle.org/8.11.1/release-notes.html

## Compatibilidad de datos y ajustes intencionales

- Las 18 tablas del origen conservan su definición SQL, incluyendo `total_pago` calculado y la grafía heredada `INTENGER` de sale_items. Se usa SQLite nativo para evitar una conversión implícita de esquema de Room.
- Las tablas `native_payment_meta` conservan el factor histórico y los importes originales; el catálogo puede cambiar después de una venta.
- `native_item_quantities` registra cantidades parciales por persona además de la relación heredada sale_item_assignment. No se pueden asignar más unidades de las que contiene la comanda.
- Los importes se calculan con BigDecimal y HALF_UP a centavos. La persistencia heredada sigue usando REAL para mantener el formato de datos. La división igual distribuye centavos remanentes y suma exactamente el total.
- La propina no cubre el precio de la orden. Como en Flutter, sale_payments.propina se conserva en MXN; native_payment_meta guarda la propina original. El campo calculado heredado mezcla monto en moneda de pago con propina en MXN para pagos USD; no debe usarse como agregado contable entre monedas.
- Se conserva la regla Gratis del origen: basta un método cuyo nombre contenga gratis para autorizar el cobro; la venta mantiene monto_pagado igual al total de la orden, como hace el código original. No se redefine esa política comercial.
- Las salidas de inventario limitan el stock mínimo a cero como el original. No se añade un bloqueo de ventas por existencias insuficientes.
- Los movimientos de inventario eran listas en memoria en Flutter; ahora se guardan para evitar perderlos al cerrar.
- Los folios se asignan dentro de la transacción y nunca por debajo del máximo existente.
- Se conserva el alcance de roles observado: admin/super_cajero administran usuarios y catálogos; cajero opera los demás módulos. El campo permissions no tenía aplicación de permisos individuales en el original y se conserva como dato, sin inventar una política.
- Se corrige la doble resta del descuento de artículo encontrada en agregados de productos/categorías: el reporte nativo suma el neto una vez. El descuento general queda registrado en ventas; los agregados por producto muestran netos por artículo antes de repartir ese descuento general.
- La edición se restringe a comandas abiertas. La liberación de mesas y todo el cobro son transaccionales.
- El original almacenaba contraseñas como texto. Se usan hashes PBKDF2 y migración al primer login, sin imprimir contraseñas ni guardar nuevas en texto.
- No se copian carpetas .cxx, build, SDKs, rutas locales ni caches del proyecto original.

## Límites que requieren aceptación

- Interfaz nueva, no identidad visual píxel a píxel; se conservan los flujos funcionales y se añade interfaz adaptable a orientación vertical/horizontal.
- La compatibilidad Bluetooth cubre protocolo clásico SPP/ESC-POS. Se requiere verificar CP850, 32/42/48 columnas, corte y reconexión con el modelo real. Los tickets Bluetooth se generan con ESC/POS textual, como el flujo revisado.
- La exportación CSV y el PDF mantienen contenido funcional, con formato nativo distinto al Flutter. Las gráficas son barras nativas.
- La integración del helper de exportación en Flutter y la comparación con datos reales no se ejecutan sin el dispositivo/base original.
- La tabla products conserva las comprobaciones REAL heredadas; algunos precios decimales pueden ser rechazados por la restricción exacta del origen. Cambiar esa restricción requiere una migración de datos aparte, no un borrado de la tabla.

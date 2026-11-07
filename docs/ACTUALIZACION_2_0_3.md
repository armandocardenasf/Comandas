# Comandas 2.0.3

## Uso

- Configuración → Datos de la empresa y logotipo (administrador): seleccionar PNG/JPG, guardar. También disponible desde Catálogos → Empresa. La imagen sustituye al logotipo del menú lateral y encabeza las impresiones Bluetooth. Quitar logotipo recupera la imagen predeterminada del menú.
- Menú: Botella se selecciona inicialmente si existe; una selección manual de otra presentación sigue respetándose.
- Reportes → Ventas o Comandas → pulsar ficha o Ver comanda: detalle desplazable con Cerrar ventana e impresión.
- Reportes → Cierres → pulsar ficha o Ver reporte de corte: alternar General / categorías y Detalle / productos; ambas vistas terminan con métodos de pago separados por moneda, importes y propinas.
- Reporte general / detallado y XLSX: usa el rango aplicado, no fechas aún sin aplicar. Exportar XLSX abre el selector de Android para guardar el archivo. General incluye hojas Resumen, Categorías y Pagos; Detalle incluye Resumen, Productos, Detalle y Pagos.
- Imprimir usa la impresora Bluetooth emparejada y configurada. Papel 58 mm: 32 columnas / imagen hasta 384 puntos; 80 mm: 48 columnas / hasta 576 puntos. Reportes en renglones verticales para evitar tablas demasiado anchas. Se usa imagen raster ESC/POS, centrada y en blanco y negro. Se necesita comprobar compatibilidad con el modelo físico.

## Datos y alcance histórico

Se agrega Empresa.logo de manera aditiva al abrir la base, conservando tablas, ventas, identificador de aplicación y versión SQLite 11. Respaldos ZIP incluyen logo y actualizan su ruta al importar. No se cambia ninguna dependencia.

Los cortes originales son acumulados del día: el reporte histórico filtra ventas del día hasta la hora de registro del corte; no representa movimientos desde el corte parcial anterior. La base original no guardaba una instantánea de los artículos por corte. Categorías se resuelven desde el catálogo actual; renombrarlas cambia la agrupación histórica. Los importes por producto distribuyen proporcionalmente el descuento general de la venta; se muestran con precisión monetaria en resumen. Las propinas no se suman a las ventas de productos.

Pagos se agrupan por método y moneda original; importe_moneda muestra el recibido registrado y total_moneda incluye propina. El recibido puede superar la venta cuando hubo cambio; el sistema original no conserva una asignación de cambio por método. El total de ventas se consulta en Resumen, no sumando el dinero recibido. Se muestra equivalente MXN según factor histórico y el número de pagos_sin_factor. Si falta factor en USD, el equivalente no está disponible para esos pagos; nunca se usa el tipo de cambio actual para inventar el histórico.

Para actualizar una instalación, compilar usando su misma firma. No desinstalar ni borrar almacenamiento. El APK debug es de evaluación.

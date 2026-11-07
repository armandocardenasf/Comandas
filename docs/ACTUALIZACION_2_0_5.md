# Comandas 2.0.5 — navegación, pagos e inventario

## Comandas y accesos rápidos

- Cada pulsación de una comanda solicita abrir Detalles, incluso si es la misma comanda seleccionada antes de regresar a Órdenes.
- Pagar abre directamente la pestaña Pago en pantallas con pestañas. Resumen sigue disponible; al girar se conserva la pestaña elegida. En pantallas amplias continúan visibles ambos paneles.
- Finalizar el recibo regresa a Comandas. El enlace Nueva comanda →, arriba a la derecha de Órdenes, abre Menú.
- Menú y su resumen tienen acceso directo a Comandas. Cambiar de sección conserva un borrador sin guardar; no se descarta silenciosamente. Después de guardar/cobrar la comanda habitual, Menú abre la captura nueva.

## Captura de pagos

- El importe inicial se calcula en la unidad del método: saldo MXN dividido entre su factor. Al cambiar el método de un importe sugerido, se recalcula desde el mismo saldo para evitar acumular redondeos.
- El sugerido se redondea hacia arriba a dos decimales para cubrir el saldo. Ejemplo: $130.00 MXN con factor 19.50 propone $6.67 USD, equivalentes a $130.07 MXN; $0.07 se reflejan como cambio. El usuario puede capturar un importe diferente o parcial.
- Si se cambia de método después de capturar un importe manual, se convierte su equivalente; una propina fija también se convierte. Los porcentajes de propina permanecen como porcentajes.
- El campo muestra MXN/USD y tiene un icono × para vaciarlo. No se agrega un pago con campo vacío.
- Un pago USD muestra `(equivalente MXN)` a la izquierda de su monto USD. En teléfonos estrechos se usa una segunda línea monetaria compacta para no recortar importes ni botones.
- La asignación por artículos aplica el mismo factor al usar el importe asignado. Se mantienen validación de pagos insuficientes, pagos mixtos, cambio, registro transaccional e inventario.

## Mesas compartidas

Una mesa puede tener varias comandas abiertas. Crear otra no bloquea por ocupación. La mesa sigue Ocupada mientras haya al menos una comanda abierta y pasa a Disponible al cerrar o eliminar la última. Mover una comanda a otra mesa respeta las que permanecen en la anterior.

## Avisos

Los avisos globales aparecen en el centro con contraste alto, permanecen aproximadamente cinco segundos y tienen una opción de cierre inmediato. También se muestran allí las validaciones de captura y reportes. Un aviso nuevo reinicia el tiempo y un temporizador anterior no puede borrar uno posterior.

## Inventario

Al abrir Inventario se muestra exclusivamente la tabla de productos: código, nombre, descripción, existencia, precio de venta y últimas entradas/salidas. Seleccionar un renglón habilita Entrada, Salida y Ver movimientos. La tabla permite desplazamiento horizontal para mantener las columnas alineadas.

- Entrada/Salida conservan el registro manual; una salida que exceda la existencia se rechaza sin modificar stock ni generar un movimiento falso.
- Ver movimientos abre una ventana del producto seleccionado. Su rango inicial es desde hoy menos un mes hasta hoy; se admiten calendario y captura manual AAAA-MM-DD. Aplicar rango actualiza la consulta.
- Los movimientos muestran fecha/hora, entrada o salida, cantidad, precio de venta actual y referencia de venta o movimiento manual. No se limita la consulta a los 200 registros anteriores.
- Se pueden imprimir con el formato de ticket configurado y exportar a PDF o XLSX. XLSX incluye información del producto/rango y una hoja de movimientos.
- Los totales indican cantidades de entradas y salidas; no se presentan como costo de compra.

Las fechas se calculan únicamente con los movimientos registrados. Productos importados sin movimientos previos muestran Sin registro; no se inventa una fecha a partir de la existencia inicial. El precio mostrado es el de venta actual del catálogo: la estructura histórica no conserva el precio vigente de cada movimiento. Se etiqueta expresamente y no se interpreta como costo o precio histórico.

## Actualización

Se conservan applicationId, versión SQLite 11, catálogos, datos y respaldo con logotipo. No se modifican versiones de dependencias. Compilar con la misma clave de la instalación existente. No desinstalar ni borrar datos para resolver diferencias de firma. La impresión física requiere validación con el modelo de impresora operativo.

El APK de evaluación 2.0.5 conserva el certificado del APK de evaluación 2.0.4 entregado. Las instalaciones compiladas con una clave propia requieren seguir usando esa clave.

# Comandas 2.0.4 — cobros, listas y reportes

## Cambios de uso

- Acceso: tarjeta centrada con borde, usuario y contraseña; se retiró la imagen lateral de botellas. El formulario permite desplazamiento y uso del teclado.
- Cobro: Total MXN, Total USD, Recibido, Falta y Cambio en renglones separados, con importes alineados a la derecha. USD usa el tipo de cambio de Empresa; si no está configurado, se informa sin inventar una conversión. Se conservan pagos mixtos, propinas, asignación de artículos y división entre personas.
- Pagos preparados: cada registro ocupa una sola fila, con importe a la derecha e iconos de editar y quitar. Continúan guardándose únicamente al confirmar cobro.
- Catálogos y permisos: filas de una línea con ID, código cuando aplica, nombre, clasificación y precio cuando aplica. Las acciones editar y eliminar se ubican al extremo derecho. Eliminar abre una confirmación y respeta las restricciones del repositorio y las relaciones existentes.
- Historial de ventas: filas compactas de folio, comanda, fecha, hora e importe; el icono de consulta abre el recibo. Las ventas confirmadas permanecen de consulta: no se introduce borrado físico que pudiera dejar inventario, comandas y pagos inconsistentes.
- Fechas: los campos Desde y Hasta aceptan escritura AAAA-MM-DD o selección mediante su icono de calendario. Aplicar rango valida ambas fechas y su orden. Día, Semana y Mes conservan acceso directo.
- Realizar corte aparece como primer botón destacado. Reporte general / detallado y XLSX tiene un botón propio destacado.

## Reportes

Todos usan ventas confirmadas y el periodo aplicado. Las comandas abiertas se consultan en el módulo Comandas y no se cuentan como ventas.

| Reporte | Una fila por | Importe |
|---|---|---|
| Ventas | Día y producto/presentación | Venta neta MXN; total del periodo y métodos de pago al final |
| Comandas | Comanda cobrada | Total de venta final, con consulta de recibo y pagos |
| Detalles comandas | Comanda y producto/presentación | Cantidad e importe neto; el detalle y la impresión muestran los pagos y el total de cada comanda |
| Productos | Producto/presentación, con miniatura a la izquierda | Cantidad e importe neto MXN |
| Categorías | Categoría | Cantidad e importe neto MXN |
| Pagos | Método y moneda | Recibido; el detalle separa propina y total |
| Cierres | Corte registrado | Importe registrado; el icono abre el reporte general/detallado |

Los nombres largos pueden desplazarse horizontalmente dentro de su celda para conservar una sola línea y mantener visibles los importes y los iconos. La consulta de una fila de Ventas muestra las comandas completas que contienen ese producto en el día seleccionado, identificadas como tales.

La vista General muestra categorías, total de venta y pagos. La vista Detalle muestra productos, total y pagos de cada comanda, más el resumen de pagos del conjunto. Se conservan CSV, PDF, impresión y XLSX. Los cortes históricos mantienen su criterio acumulado hasta la hora del corte; no equivalen a ventas desde el corte parcial anterior.

## Tickets e impresión

Un único formateador compone encabezados centrados, separadores y columnas de descripción e importe. Los importes terminan en el borde derecho de la columna. Los nombres extensos se ajustan sin cortar cantidades. Vista previa y PDF usan fuente monoespaciada; Bluetooth selecciona fuente ESC/POS A, de ancho fijo.

- 58 mm: configurar 32 columnas.
- 80 mm: configurar 48 columnas; se conserva la alternativa de 42.
- Configuración y respaldos → Columnas determina el ancho del mismo formato para todos los recibos y reportes.
- Los tickets incluyen el logotipo configurado, artículos, subtotales, descuento general, total, propinas, métodos recibidos y cambio cuando puede reconstruirse con los tipos de cambio históricos.

Recibido no equivale necesariamente a venta: puede incluir dinero que se devuelve como cambio. Se separan las monedas y las propinas. Los importes por producto distribuyen el descuento general y asignan el residuo de centavos al último artículo, para cuadrar exactamente con la venta.

## Datos y actualización

No cambia el identificador de la app, la versión SQLite 11 ni las dependencias. Se conservan datos, importación y respaldos con logotipo.

Compilar con la firma de la instalación actual para actualizar sin desinstalar. El APK de evaluación de esta entrega usa una firma diferente a la del APK de evaluación 2.0.3; no desinstalar el POS operativo para resolver un conflicto de firma. Queda por validar la impresión y el corte de papel con la impresora física concreta.

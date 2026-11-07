# Restauración del diseño original — 2.0.2

## Base de la revisión

Se comparó el código Flutter de Comandas.rar con la entrega Kotlin 2.0.1. La entrega anterior había sustituido la composición original por listas y formularios genéricos. Esta revisión recupera el diseño funcional de Menú, Comandas, acceso y el marco de cobros, manteniendo la adaptación vertical autorizada previamente.

No se utilizó una base de producción para crear las capturas: los datos son los iniciales del proyecto y registros de prueba.

## Correspondencia de pantallas y controles

| Referencia Flutter | Faltante o diferencia | Corrección Kotlin |
| --- | --- | --- |
| app_theme.dart | Paleta vino/burdeos ajena al original | Gris #F2F2F2, blanco #FFFFFF, crema #FFF3E6, café #5C421D y tan #C9AC84; esquema oscuro basado en los colores originales |
| sidenavbar.dart | Barra lateral ancha y sin la identidad original | Logotipo del proyecto, navegación de 90 dp, iconos y selección crema; menú superior equivalente en ventanas estrechas |
| login_screen.dart / login_form.dart | Formulario sin imagen de la vinícola | Imagen cruzvinos del original junto al formulario en ventanas amplias; formulario desplazable en teléfono |
| home.dart | Menú separado del armado de una comanda | Catálogo y resumen en la misma pantalla, proporción 5:3 cuando hay ancho suficiente |
| menu_section.dart / welcome_widget.dart | Encabezado y búsqueda sin composición original | Bienvenida, mensaje original y búsqueda redondeada |
| category_cards.dart | Selector desplegable en lugar de botones | Fila desplazable de categorías, Todo y selección naranja por ID de categoría |
| grid_products.dart / product_card.dart | Lista de productos sin tarjetas ni variantes visibles | Cuadrícula de tarjetas, imagen sin recorte, nombre, descripción, precio y presentaciones elegibles |
| product_card.dart | Fotografía accesible solo desde catálogo administrativo | Pulsación larga o Foto para elegir imagen o cámara desde el POS; actualización limitada al campo foto |
| summary_section.dart | Resumen incompleto | Mesa, cliente, alta rápida, personas, artículos, descuentos, nota, subtotal y totales MXN/USD |
| personas_input_row.dart | Solo campo de texto | Botones 1–4 y campo para otra cantidad |
| order_item_card.dart | Controles de edición separados del resumen | Cantidades +/−, descuento por artículo, edición y eliminación en cada renglón |
| discount_and_comments.dart | Falta de accesos del resumen | Descuento general en porcentaje/MXN y nota, con indicadores de contenido |
| order_summary_box.dart | Falta de conversión y desglose | Subtotal, descuento, total MXN, total USD y tipo de cambio de Empresa |
| continue_order_button.dart | Flujo iniciado desde un botón externo | Continuar desde el resumen y Guardar cambios al editar; guardado transaccional existente |
| orders.dart / order_header.dart | Solo listado de órdenes abiertas | Filtros Todas, Abiertos y Pagados |
| orders_grid.dart / order_card.dart | Renglones sin composición original | Tarjetas con mesa, folio, personas, estado, fecha, hora, eliminación y total; selección crema |
| order_details_panel.dart / order_item_tile.dart | Falta de panel de detalle | Panel 4:3 con artículos, cantidades, descripción, código, precio, descuento y total |
| order_summary_section.dart | Sin notas ni acciones agrupadas | Desglose MXN/USD y acciones Notas, Editar, Pagar e Imprimir; órdenes pagadas se consultan sin permitir nuevo cobro o edición |
| payments.dart | Distribución vertical genérica | Marco con resumen a la izquierda y acciones de cobro a la derecha, proporción 1:1 en ancho suficiente |

Las demás pantallas heredan paleta, superficies y formas compartidas; esta revisión no reproduce individualmente todos los layouts administrativos de Flutter.

## Adaptación vertical

En pantallas estrechas, Menú/Resumen, Órdenes/Detalle y Resumen/Pago se presentan como pestañas para conservar todos los controles. Se mantiene el desplazamiento de listas y formularios y la conservación del borrador al cambiar de sección o recrear la actividad. El original Flutter bloqueaba la orientación horizontal; ese bloqueo no se reinstala.

Los tamaños de letra, objetivos táctiles y márgenes se ajustan a Android. Se recuperan la estructura y los colores del original sin prometer identidad de cada píxel entre Flutter y Compose.

## Datos y permisos

- Se conserva el ID mx.ensenada.comandas, el esquema SQLite versión 11 y las tablas heredadas. No se reinicia ni se resembra una instalación existente.
- La creación rápida de clientes y la actualización de fotografía desde el POS están disponibles para sesiones activas, como en Flutter. No habilitan edición general de catálogos para cajeros.
- El total de las tarjetas se calcula a partir de sus artículos y descuentos con la misma aritmética decimal del detalle. No depende de un campo total inexistente ni altera los importes almacenados en bases heredadas. Los artículos se recorren con un cursor para evitar cargar todo el historial de renglones en memoria.
- Al cambiar cantidades se conserva proporcionalmente el descuento por unidad. La edición mantiene el tipo de descuento porcentaje/MXN.
- El importe USD del resumen usa Empresa.tipo_cambio. Los cobros conservan el factor del método de pago seleccionado; no se igualan automáticamente ambos valores.
- Las imágenes de productos capturadas en el dispositivo requieren el respaldo ZIP que las incluya. El RAR trae un placeholder, no las fotografías operativas. Las rutas válidas de una instalación nativa existente se siguen utilizando.

## Archivos principales

- ui/OriginalStyle.kt: colores, formas, logotipo y navegación.
- ui/RestoredPosScreens.kt: catálogo, tarjetas, borrador, resumen, órdenes y marco de cobros.
- ui/PosApp.kt: integración, acceso, cobros existentes y formularios compartidos.
- ui/PosViewModel.kt: carga de categorías, variantes y empresa junto con los datos existentes.
- data/PosRepository.kt: consulta de todos los estados, total de tarjetas y operaciones rápidas acotadas.

## Verificación

Consultar VALIDACION.md para el resultado ejecutado. Las pruebas de interfaz recorren selección de presentación, cantidades, descuentos, nota, cliente nuevo, cambio entre secciones, recreación de actividad, guardado, edición, cobro y consulta de pagados. Las capturas nativas del propio código están en docs/evidencias/interfaz.

La aceptación final requiere revisar el dispositivo y los datos del negocio. Cámara, Bluetooth e impresión física se deben verificar en sus dispositivos correspondientes.

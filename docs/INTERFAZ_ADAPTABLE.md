# Interfaz adaptable — 2.0.2

La orientación vertical y horizontal se mantienen. No se reinstala el bloqueo landscape que tenía Flutter.

## Pantallas

- Menú: catálogo y resumen 5:3 en ventanas amplias; pestañas Menú/Resumen en ventanas estrechas. Para guardar se utiliza Continuar desde el resumen.
- Comandas: tarjetas y detalle 4:3 en ventanas amplias; pestañas Órdenes/Detalle en ventanas estrechas. Los filtros incluyen Todas, Abiertos y Pagados.
- Cobros: resumen y acciones 1:1, o pestañas Resumen/Pago según el espacio.
- Inventario: productos y movimientos en paneles o pestañas, como en la entrega adaptable anterior.
- Navegación: barra lateral con logotipo y colores originales en ancho suficiente; botón Secciones en ventanas estrechas.
- Formularios y listas: desplazamiento para acceder a acciones en ventanas bajas y con teclado. Se conserva estado guardable en borradores y pagos.

La estructura y paleta restauradas se documentan en RESTAURACION_DISENO_ORIGINAL.md. En las capturas se utilizan datos de prueba y el placeholder original cuando no existe una fotografía operativa.

## Actualización de una instalación existente

1. Exportar un respaldo ZIP desde Configuración del POS instalado.
2. Compilar el nuevo proyecto con la misma firma que utiliza esa instalación e instalar encima de ella. No desinstalar ni borrar almacenamiento para solucionar una diferencia de firma.
3. Activar Rotación automática en Android para alternar orientaciones.
4. Abrir en Android Studio la raíz Comandas de una extracción nueva, usando JDK 17 y SDK 35.

Se conservan el identificador mx.ensenada.comandas y el esquema SQLite 11. El APK debug de evaluación no contiene las claves privadas usadas por otras instalaciones. Consultar README.md y MIGRACION_DATOS.md antes de actualizar o trasladar datos.

## Comprobación en el dispositivo

- Revisar imágenes y tamaños de letra con los datos del negocio.
- Crear una comanda, seleccionar presentación, cantidad, cliente, personas, descuentos y nota. Girar y comprobar su conservación.
- Consultar abiertas/pagadas, seleccionar una orden y comprobar detalle, importes y acciones.
- Preparar pagos, girar antes de confirmar y verificar métodos, propinas, asignaciones y saldo.
- Probar teclado, cámara, PDF/CSV, impresión y respaldos en los dispositivos correspondientes.

La validación automatizada se describe en VALIDACION.md. No sustituye la aceptación con el dispositivo y la impresora del negocio.

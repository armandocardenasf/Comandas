# Validación Comandas 2.1.0

Base de fuentes: 2.0.5. Cambios aditivos: modo corporativo opcional, parámetros, REST HTTPS, registro, login con acceso local autorizado, imágenes y configuración, inventario asignado, cola durable de cortes y sincronización con reintentos.

Se compiló `:app:assembleDebug`, se ejecutaron 64 pruebas sin fallas y Lint finalizó con cero errores (11 advertencias no bloqueantes). La suite conserva las pruebas de navegación, pagos, reportes, impresión y base de datos de la versión anterior y añade cuatro casos corporativos.

El esquema original de negocio y semillas se conserva; se agregan tablas `native_corporate_*`. La prueba de migración de la base heredada ahora verifica la versión 12. Se añadió una espera de inicialización al helper de pruebas de interfaz para impedir una carrera con la restauración de sesión.

La compilación posterior al formateo de los nuevos archivos Kotlin también se verificó. Las evidencias compartidas están en `../../corporativo/docs/evidencias/` y el detalle de pruebas y límites en `../../corporativo/docs/VALIDACION.md`.

Pendiente de aceptación en sus equipos: registro HTTPS real, Android Keystore, descarga de imágenes, operación prolongada sin conexión, reinicios y restricciones de batería, impresora Bluetooth y actualización firmada con su llave original. No se cambió el servidor productivo ni se incluyen credenciales reales.

# Comandas Android 2.1.0

Actualización sobre las fuentes 2.0.5, versionCode 14 y esquema SQLite 12. Conserva aplicación nativa Kotlin/Compose, folios, comandas independientes, pagos, inventario, respaldos, reportes e impresión. Agrega integración corporativa opcional; inicialmente está desactivada.

## Contenido añadido

`corporate/CorporateStore.kt`: tablas auxiliares, copias históricas y cola transaccional. `CorporateClient.kt`: REST HTTPS con Retrofit/OkHttp. `CorporateManager.kt`: registro, login, sincronización, imágenes y asignaciones. `SecretStore.kt`: cifrado con Android Keystore. `CorporateWorker.kt`: envío y reintento mediante WorkManager. `ui/CorporateSettings.kt`: parámetros y acciones.

## Compilar

Abrir este proyecto en Android Studio compatible con AGP 8.9.2, JDK 17 completo, SDK 35 y Build Tools 35.0.0. Android mínimo 12/API 31.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El APK de pruebas se genera en `app/build/outputs/apk/debug/`. Ver `docs/VALIDACION_CORPORATIVA.md` para los resultados de la entrega. Para distribución use su proceso release y llave de firma ORIGINAL. No se incluye una llave privada. Un APK firmado con otra llave no se instala como actualización sobre el existente. Hacer respaldo antes del piloto; no desinstalar la aplicación actual para resolver un conflicto de firma sin preservar sus datos.

## Preparar una terminal nueva

1. Instalar la aplicación y acceder con administrador local. En una base nueva se conservan los datos y el acceso inicial del proyecto original (`admin` / `1234`); cambiar esa clave y no utilizarla como cuenta corporativa.
2. En el portal crear empresa, catálogos y usuario móvil. Cambiar la clave temporal en el primer acceso web. Las cuentas móviles tienen acceso restringido para esta operación si no poseen permisos de portal.
3. Crear terminal con número único en la empresa. Generar activación y copiar código de empresa, ID UUID de terminal y código temporal (vence en 30 minutos).
4. Android → Configuración → Configuración corporativa → Registrar terminal nueva. Capturar URL HTTPS del servidor SIN `/api/v1`, datos de activación y revisar número asignado. El valor inicial del parámetro es 1.
5. Si hay historia o movimientos existentes, respaldar, revisar coincidencias por códigos y confirmar conciliación. Terminar comandas abiertas antes de registrar. Los productos nuevos inician sin existencias; no se copia todo el stock de empresa.
6. Activar y descargar empresa. La app cierra la sesión local y pide acceso corporativo. Configurar impresora Bluetooth en cada equipo.
7. Asignar inventario desde el portal o registrar entradas locales autorizadas. Sincronizar y comprobar que cada asignación se aplica una sola vez.

Si el registro se completó y se interrumpió la descarga, no genere otra instalación: use Actualizar catálogos e imágenes y después Guardar configuración con el modo activado. Las credenciales ya quedaron registradas. Si se perdió la respuesta del registro antes de guardar credenciales, un administrador deberá revocar esa activación e iniciar un registro nuevo; no hay registro automático inseguro con el mismo código consumido.

## Funcionamiento

- Login corporativo en línea obtiene sesión y permisos. La caché local permite acceso por la vigencia de empresa (0 a 30 días, inicialmente 7). No almacena contraseñas del servidor: guarda un verificador local derivado de la clave que el usuario introdujo.
- La primera autenticación y preparación requieren conexión. Un rechazo de usuario/clave no se trata como falta de Internet. La vigencia local se comprueba al operar y al restaurar la sesión.
- La sincronización al login no bloquea acceso autorizado si falla la descarga. La actualización de catálogos se posterga mientras existan comandas abiertas, conservando precios/variantes actuales.
- La descarga aplica un manifiesto completo de metadatos en una transacción y descarga solo imágenes faltantes según su huella. Guarda imágenes localmente para trabajar sin conexión.
- Catálogos compartidos se editan en el portal. Las existencias se modifican por movimientos, no se reemplazan al actualizar catálogo. Las variantes/elementos desactivados se conservan para historia.
- El corte se guarda con su contenido y cola en la misma transacción local. WorkManager intenta enviarlo al existir conexión; reinicios no borran la cola. Android puede diferir tareas por batería o cierre forzado. Abrir la app o pulsar Enviar cortes pendientes fuerza otro intento.
- La salida por transferencia se aplica solo con stock suficiente. El destino recibe la entrada después de la confirmación de salida. Cada asignación local tiene una marca durable para no reaplicarla si se pierde un acuse.
- El número y empresa no se cambian silenciosamente tras registrar. Para sustituir un dispositivo, enviar primero sus pendientes y coordinar revocación/activación con el administrador. No clonar bases e identidades en equipos nuevos.

## Respaldo

Los ZIP de respaldo siguen incluyendo SQLite y las imágenes según el mecanismo existente. Tokens y claves Keystore no se incluyen. Una base restaurada en otro equipo requiere revisión y nuevo registro supervisado; no quedará autenticada por copiar el respaldo. Conservar los cortes pendientes y su identidad antes de cualquier restauración. No cambiar de empresa con una base activa.

## Referencias

README.md conserva las instrucciones originales. La carpeta corporativo del paquete incluye backend, portal, SQL y despliegue. Ningún dato de servidor, SMTP ni credencial corporativa está incrustado en el APK.

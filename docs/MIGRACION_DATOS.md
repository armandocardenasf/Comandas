# Migración de datos sin desinstalar el POS original

## Ruta recomendada: respaldo ZIP

1. Mantener el proyecto y la instalación original de Flutter.
2. Copiar `tools/export_flutter_backup.dart` a `lib/utils/export_native_backup.dart` en Flutter.
3. Agregar `archive: ^3.6.1` a `pubspec.yaml` y ejecutar `flutter pub get`. Esta dependencia solo se necesita en el exportador Flutter; el proyecto Kotlin usa ZIP del JDK.
4. Incorporar una acción administrativa de exportación y detener temporalmente las operaciones de caja durante la exportación.
5. Desde esa acción ejecutar:

```dart
import 'dart:io';
import 'package:printing/printing.dart';
import 'package:comandas/data/database/db_helper.dart';
import 'package:comandas/utils/export_native_backup.dart';

final db = await DatabaseHelper().database;
final backup = await exportNativeBackup(db);
await Printing.sharePdf(
  bytes: await backup.readAsBytes(),
  filename: backup.uri.pathSegments.last,
);
```

6. Guardar el archivo ZIP en un lugar accesible y conservar una copia independiente. El helper utiliza `VACUUM INTO` para obtener una instantánea consistente, incluyendo datos confirmados del WAL. Requiere SQLite 3.27+ y espacio libre. La integración del helper Flutter requiere prueba en el dispositivo original; no se ha compilado como parte del APK Kotlin.
7. Instalar el nuevo APK junto al original.
8. Iniciar sesión como admin. Abrir Configuración → Importar ZIP / restaurant.db → seleccionar el ZIP → confirmar.
9. La aplicación valida versión 10/11, integridad SQLite y las columnas esperadas antes de sustituir la base. Copia previamente la base actual a `files/pre-import-*.db`. Tras importar, la sesión se cierra.
10. Iniciar sesión con las credenciales de la base importada y comparar usuarios, catálogos, cantidades, últimas ventas, folios, descuentos, propinas, comandas abiertas y cierres.
11. Exportar un nuevo respaldo desde Kotlin y conservar ambas aplicaciones durante la aceptación.

## Datos conservados

Se conservan nombres de tablas y columnas, IDs, relaciones, documentos históricos, productos, variantes, clientes, empleados, métodos, empresa, folios, usuarios, roles y el campo `permissions`.

La migración 10 → 11 agrega tablas nativas y no ejecuta DROP, borrado de tablas ni reinicialización de datos. Las contraseñas heredadas se convierten a PBKDF2 al iniciar sesión correctamente; se conserva la contraseña válida del usuario y se actualiza `ultimo_acceso`.

Las fotografías se incluyen en `photos/` y `photos.json` relaciona su ruta anterior con el archivo del ZIP. Al importar, se actualiza `productos.foto` para apuntar al almacenamiento privado de la nueva aplicación. Las fotografías faltantes en el dispositivo original no pueden recuperarse desde el código fuente.

## Alternativa: base SQLite

Puede importarse una base `restaurant.db` versión 10/11 consistente. Si se obtiene mediante ADB desde una instalación de depuración, detener la app primero y obtener una instantánea que incluya el WAL; copiar únicamente el archivo principal mientras hay operaciones activas puede omitir ventas recientes. Esta alternativa no incluye fotos externas y requiere trasladarlas por respaldo ZIP.

El nuevo ID de aplicación impide acceder directamente al almacenamiento privado del POS Flutter. No se utiliza acceso root ni se presupone que una aplicación pueda leer los archivos privados de otra.

## Recuperación

Ante un fallo de validación previo, la base actual queda intacta. Ante un fallo al abrir la base copiada, se restaura la copia previa. Puede recuperar los respaldos privados con Android Studio Device Explorer en la compilación debug. Después de una importación correcta, exporte inmediatamente un ZIP y guárdelo fuera del dispositivo.

La reversión hacia Flutter exige comprobar que el consumidor tolere la versión 11 y los hashes PBKDF2; no importe una base Kotlin directamente al Flutter original sin adaptar y probar su lector. Se mantiene una copia independiente del origen para volver atrás.

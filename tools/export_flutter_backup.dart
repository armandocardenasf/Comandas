// Add archive: ^3.6.1 to pubspec.yaml before using this helper.
// Copy this file to lib/utils/export_native_backup.dart in the original project.
import 'dart:convert';
import 'dart:io';
import 'package:archive/archive.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

/// Invoke while POS operations are paused. Share the resulting ZIP with the
/// existing Printing.sharePdf(bytes: ..., filename: ...) mechanism.
/// VACUUM INTO creates a consistent snapshot and includes committed WAL data.
Future<File> exportNativeBackup(Database db) async {
  final directory = await getTemporaryDirectory();
  final stamp = DateTime.now().millisecondsSinceEpoch;
  final snapshot = File(p.join(directory.path, 'restaurant-$stamp.db'));
  final escaped = snapshot.path.replaceAll("'", "''");
  await db.execute("VACUUM INTO '$escaped'");
  final archive = Archive();
  final bytes = await snapshot.readAsBytes();
  archive.addFile(ArchiveFile('restaurant.db', bytes.length, bytes));
  final products = await db.rawQuery("SELECT foto FROM productos WHERE foto IS NOT NULL AND foto<>''");
  final photos = <Map<String, String>>[];
  final seen = <String>{};
  for (final row in products) {
    final path = row['foto']?.toString() ?? '';
    if (!seen.add(path)) continue;
    final photo = File(path);
    if (!await photo.exists()) continue;
    final entry = 'photos/${photos.length}-${p.basename(path)}';
    final data = await photo.readAsBytes();
    archive.addFile(ArchiveFile(entry, data.length, data));
    photos.add({'old': path, 'entry': entry});
  }
  final metadata = utf8.encode(jsonEncode(photos));
  archive.addFile(ArchiveFile('photos.json', metadata.length, metadata));
  final zip = File(p.join(directory.path, 'Comandas-migracion-$stamp.zip'));
  await zip.writeAsBytes(ZipEncoder().encode(archive)!);
  await snapshot.delete();
  return zip;
}

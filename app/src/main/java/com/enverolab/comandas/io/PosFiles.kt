package com.enverolab.comandas.io

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.enverolab.comandas.data.PosDatabase
import com.enverolab.comandas.domain.*
import org.json.JSONArray
import org.json.JSONObject

class PosFiles(val context: Context, val store: PosDatabase) {
    private fun file(name: String) =
        File(context.cacheDir, "exports/$name").also { it.parentFile!!.mkdirs() }

    fun uri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** OOXML sin dependencias adicionales; texto inline evita interpretar fórmulas. */
    fun xlsx(title: String, sheets: Map<String, List<Row>>): File {
        fun xml(value: Any?) =
            value
                ?.toString()
                .orEmpty()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .filter { it >= ' ' || it == '\n' || it == '\t' }
        val output = file("${title}-${System.currentTimeMillis()}.xlsx")
        ZipOutputStream(output.outputStream()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>${sheets.keys.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>""",
            )
            entry(
                "_rels/.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            entry(
                "xl/workbook.xml",
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.keys.mapIndexed { i,name -> "<sheet name=\"${xml(name.take(31))}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>" }.joinToString("")}</sheets></workbook>""",
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.keys.indices.joinToString("") { "<Relationship Id=\"rId${it+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>" }}</Relationships>""",
            )
            sheets.entries.forEachIndexed { index, (_, rows) ->
                val columns = rows.flatMap { it.keys }.distinct()
                val data =
                    listOf(columns.map { it as Any? }) + rows.map { row -> columns.map { row[it] } }
                val content =
                    data.joinToString("") { row ->
                        "<row>" +
                            row.joinToString("") { value ->
                                if (value is Number) "<c><v>${xml(value)}</v></c>"
                                else
                                    "<c t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
                            } +
                            "</row>"
                    }
                entry(
                    "xl/worksheets/sheet${index+1}.xml",
                    """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$content</sheetData></worksheet>""",
                )
            }
        }
        return output
    }

    fun csv(title: String, rows: List<Row>): File {
        val columns = rows.flatMap { it.keys }.distinct()
        fun cell(value: Any?): String {
            var text = value?.toString().orEmpty()
            if (
                text.startsWith('=') ||
                    text.startsWith('+') ||
                    text.startsWith('-') ||
                    text.startsWith('@')
            )
                text = "'$text"
            return "\"${text.replace("\"","\"\"")}\""
        }
        return file("${title}-${LocalDate.now()}.csv").also {
            it.writeText(
                "\uFEFF" +
                    columns.joinToString(",") { cell(it) } +
                    "\r\n" +
                    rows.joinToString("\r\n") { row -> columns.joinToString(",") { cell(row[it]) } }
            )
        }
    }

    fun pdf(title: String, text: String): File {
        val output = file("${title}-${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        try {
            val paint =
                Paint().apply {
                    textSize = 11f
                    typeface = android.graphics.Typeface.MONOSPACE
                    isAntiAlias = true
                }
            val lines = buildList {
                Ticket.render(
                        text,
                        context
                            .getSharedPreferences("printer", Context.MODE_PRIVATE)
                            .getInt("width", 32),
                    )
                    .lines()
                    .forEach { line ->
                        if (line.isEmpty()) add("")
                        else {
                            var rest = line
                            while (rest.isNotEmpty()) {
                                val fit = paint.breakText(rest, true, 505f, null).coerceAtLeast(1)
                                add(rest.take(fit))
                                rest = rest.drop(fit)
                            }
                        }
                    }
            }
            lines
                .chunked(48)
                .ifEmpty { listOf(listOf(title)) }
                .forEachIndexed { index, pageLines ->
                    val page =
                        doc.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    page.canvas.drawText(title, 45f, 35f, paint)
                    pageLines.forEachIndexed { i, line ->
                        page.canvas.drawText(line, 45f, 65f + i * 15f, paint)
                    }
                    page.canvas.drawText("Página ${index+1}", 45f, 810f, paint)
                    doc.finishPage(page)
                }
            output.outputStream().use(doc::writeTo)
        } finally {
            doc.close()
        }
        return output
    }

    fun backup(): File {
        val db = store.exportDatabase()
        val out = file("Comandas-respaldo-${System.currentTimeMillis()}.zip")
        val photos =
            store.rows(
                "SELECT id,foto FROM productos WHERE foto IS NOT NULL AND foto<>'' UNION ALL SELECT id,logo AS foto FROM Empresa WHERE logo IS NOT NULL AND logo<>''"
            )
        val manifest = JSONArray()
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("restaurant.db"))
            db.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            photos.forEachIndexed { index, row ->
                val source = File(row.text("foto"))
                if (source.isFile) {
                    val entry = "photos/$index-${source.name}"
                    zip.putNextEntry(ZipEntry(entry))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    manifest.put(JSONObject().put("old", row.text("foto")).put("entry", entry))
                }
            }
            zip.putNextEntry(ZipEntry("photos.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
        }
        return out
    }

    fun restore(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: error("Archivo no disponible")
        val staged =
            File(context.cacheDir, "restore-${System.currentTimeMillis()}").also { it.mkdirs() }
        val source = File(staged, "source")
        input.use { i -> source.outputStream().use { i.copyTo(it) } }
        val isZip = source.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
        try {
            if (!isZip) {
                val backup = store.importDatabase(source.inputStream())
                return "Datos importados. Respaldo previo: ${backup.name}. Imágenes externas requieren respaldo ZIP."
            }
            var total = 0L
            ZipInputStream(source.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory)
                    val dest = File(staged, entry.name).canonicalFile
                    require(dest.path.startsWith(staged.canonicalPath + File.separator)) {
                        "Ruta de respaldo inválida"
                    }
                    require(
                        entry.name == "restaurant.db" ||
                            entry.name == "photos.json" ||
                            entry.name.startsWith("photos/")
                    ) {
                        "Contenido no reconocido"
                    }
                    dest.parentFile!!.mkdirs()
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total < 512L * 1024 * 1024) { "Respaldo demasiado grande" }
                            out.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val database = File(staged, "restaurant.db")
            require(database.isFile) { "Falta restaurant.db" }
            val metadata = File(staged, "photos.json")
            val manifest = if (metadata.isFile) JSONArray(metadata.readText()) else JSONArray()
            val newPhotos =
                File(context.filesDir, "photos/import-${System.currentTimeMillis()}").also {
                    it.mkdirs()
                }
            val mapping = mutableListOf<Pair<String, String>>()
            for (i in 0 until manifest.length()) {
                val row = manifest.getJSONObject(i)
                val photo = File(staged, row.getString("entry")).canonicalFile
                require(photo.path.startsWith(staged.canonicalPath + "/photos/") && photo.isFile)
                val dest = File(newPhotos, "$i-${photo.name}")
                photo.copyTo(dest)
                mapping += row.getString("old") to dest.path
            }
            try {
                val backup = store.importDatabase(database.inputStream())
                try {
                    store.transaction { db ->
                        mapping.forEach { (old, new) ->
                            db.execSQL(
                                "UPDATE productos SET foto=? WHERE foto=?",
                                arrayOf(new, old),
                            )
                            db.execSQL("UPDATE Empresa SET logo=? WHERE logo=?", arrayOf(new, old))
                        }
                    }
                } catch (e: Exception) {
                    store.importDatabase(backup.inputStream())
                    throw e
                }
                return "Datos e imágenes importados. Respaldo previo: ${backup.name}"
            } catch (e: Exception) {
                newPhotos.deleteRecursively()
                throw e
            }
        } finally {
            staged.deleteRecursively()
        }
    }

    fun importLogo(uri: Uri): File {
        val source = importPhoto(uri)
        try {
            val bitmap = logoImage(source.path) ?: error("Seleccione una imagen PNG o JPG válida")
            val output = File(context.filesDir, "photos/logo-${System.nanoTime()}.png")
            output.outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            return output
        } finally {
            source.delete()
        }
    }

    fun importPhoto(uri: Uri): File {
        val output =
            File(context.filesDir, "photos/${System.currentTimeMillis()}.jpg").also {
                it.parentFile!!.mkdirs()
            }
        context.contentResolver.openInputStream(uri)!!.use { input ->
            output.outputStream().use { input.copyTo(it) }
        }
        return output
    }
}

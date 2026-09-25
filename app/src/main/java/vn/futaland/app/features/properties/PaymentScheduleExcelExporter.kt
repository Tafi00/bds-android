package vn.futaland.app.features.properties

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the payment-schedule workbook as a real `.xlsx` file — same two sheets
 * as the web export ("Tong quan" + "Bang tinh theo dot") — so chat apps such as
 * Zalo receive an attachment they can open instead of raw text.
 */
object PaymentScheduleExcelExporter {
    private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun share(
        context: Context,
        policy: PaymentSchedulePolicy,
        result: PaymentScheduleResult,
        unitLabel: String,
        projectName: String
    ) {
        val file = export(context, policy, result, unitLabel, projectName)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_XLSX
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bảng tính thanh toán $unitLabel")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Xuất bảng tính thanh toán"))
    }

    fun export(
        context: Context,
        policy: PaymentSchedulePolicy,
        result: PaymentScheduleResult,
        unitLabel: String,
        projectName: String
    ): File {
        val summary = mutableListOf<List<XlsxCell>>(listOf(XlsxCell.Header("Chỉ tiêu"), XlsxCell.Header("Giá trị")))
        summary += listOf(XlsxCell.Text("Mã căn"), XlsxCell.Text(unitLabel))
        summary += listOf(XlsxCell.Text("Chính sách"), XlsxCell.Text(policy.name))
        if (policy.code.isNotBlank()) summary += listOf(XlsxCell.Text("Mã chính sách"), XlsxCell.Text(policy.code))
        if (projectName.isNotBlank()) summary += listOf(XlsxCell.Text("Dự án"), XlsxCell.Text(projectName))
        summary += listOf(XlsxCell.Text("Giá niêm yết (VNĐ)"), XlsxCell.Money(result.basePrice.toDouble()))
        summary += listOf(XlsxCell.Text("Chiết khấu (%)"), XlsxCell.Number(result.discountPercent))
        summary += listOf(XlsxCell.Text("Tiền chiết khấu (VNĐ)"), XlsxCell.Money(result.discountAmount.toDouble()))
        summary += listOf(XlsxCell.Text("Tiền đặt cọc quy định (VNĐ)"), XlsxCell.Money(result.depositAmount.toDouble()))
        summary += listOf(XlsxCell.Text("Giá thanh toán thực tế (VNĐ)"), XlsxCell.Money(result.netPrice.toDouble()))
        summary += listOf(XlsxCell.Text("Ghi chú"), XlsxCell.Text("Dữ liệu tham khảo, sẽ điều chỉnh theo điều kiện thực tế bàn giao."))

        val detail = mutableListOf<List<XlsxCell>>(
            listOf(
                XlsxCell.Header("Đợt TT"), XlsxCell.Header("Mốc thời gian / Điều kiện"), XlsxCell.Header("Tỷ lệ quy đổi (%)"),
                XlsxCell.Header("Số tiền thực tế cần đóng"), XlsxCell.Header("Tích lũy đã đóng"), XlsxCell.Header("Ghi chú điều kiện")
            )
        )
        result.rows.forEach { row ->
            val timing = row.name + if (row.timing.isNotBlank()) " — ${row.timing}" else ""
            detail += listOf(
                XlsxCell.Number(row.order.toDouble()),
                XlsxCell.Text(timing),
                row.percent?.let { XlsxCell.Number(it) } ?: XlsxCell.Text(""),
                XlsxCell.Money(row.amount.toDouble()),
                XlsxCell.Money(row.cumulative.toDouble()),
                XlsxCell.Text(row.note)
            )
        }

        val bytes = XlsxWriter.workbook(
            listOf(
                XlsxWriter.Sheet("Tong quan", listOf(34.0, 48.0), summary),
                XlsxWriter.Sheet("Bang tinh theo dot", listOf(8.0, 48.0, 16.0, 24.0, 24.0, 60.0), detail)
            )
        )
        val code = unitLabel.ifBlank { policy.code }.replace(Regex("[^A-Za-z0-9._-]"), "-").ifBlank { "export" }
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(folder, "bang-tinh-thanh-toan-$code.xlsx").apply { writeBytes(bytes) }
    }
}

sealed interface XlsxCell {
    data class Header(val value: String) : XlsxCell
    data class Text(val value: String) : XlsxCell
    data class Number(val value: Double) : XlsxCell
    data class Money(val value: Double) : XlsxCell
}

/** Minimal SpreadsheetML writer — inline strings, one styles part, no external dependency. */
object XlsxWriter {
    data class Sheet(val name: String, val columnWidths: List<Double>, val rows: List<List<XlsxCell>>)

    fun workbook(sheets: List<Sheet>): ByteArray {
        val entries = linkedMapOf<String, String>()
        val overrides = sheets.indices.joinToString("") {
            """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        entries["[Content_Types].xml"] =
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                """<Default Extension="xml" ContentType="application/xml"/>""" +
                """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                overrides + "</Types>"
        entries["_rels/.rels"] =
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                "</Relationships>"
        entries["xl/workbook.xml"] =
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                sheets.mapIndexed { i, s -> """<sheet name="${escape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                "</sheets></workbook>"
        entries["xl/_rels/workbook.xml.rels"] =
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                sheets.indices.joinToString("") {
                    """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
                } +
                """<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
                "</Relationships>"
        // Styles: 0 = default, 1 = bold header, 2 = #,##0 money, 3 = wrapped text
        entries["xl/styles.xml"] =
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
                """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
                """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
                """<fill><patternFill patternType="solid"><fgColor rgb="FFE8F5EE"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
                """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
                """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
                """<cellXfs count="4">""" +
                """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
                """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
                """<xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
                """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>""" +
                "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"
        sheets.forEachIndexed { i, sheet -> entries["xl/worksheets/sheet${i + 1}.xml"] = worksheet(sheet) }

        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + body).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun worksheet(sheet: Sheet): String {
        val cols = sheet.columnWidths.mapIndexed { i, w ->
            """<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>"""
        }.joinToString("")
        val rows = StringBuilder()
        sheet.rows.forEachIndexed { r, row ->
            rows.append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = "${columnName(c)}${r + 1}"
                rows.append(
                    when (cell) {
                        is XlsxCell.Header -> """<c r="$ref" t="inlineStr" s="1"><is><t>${escape(cell.value)}</t></is></c>"""
                        is XlsxCell.Text -> """<c r="$ref" t="inlineStr" s="3"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>"""
                        is XlsxCell.Number -> """<c r="$ref"><v>${number(cell.value)}</v></c>"""
                        is XlsxCell.Money -> """<c r="$ref" s="2"><v>${number(Math.rint(cell.value))}</v></c>"""
                    }
                )
            }
            rows.append("</row>")
        }
        return """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""" +
            "<cols>$cols</cols><sheetData>$rows</sheetData></worksheet>"
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            name.insert(0, ('A' + remainder))
            n = (n - 1) / 26
        }
        return name.toString()
    }

    private fun number(value: Double): String {
        if (!value.isFinite()) return "0"
        return if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
    }

    private fun escape(value: String): String {
        val out = StringBuilder()
        value.forEach { ch ->
            when {
                ch == '&' -> out.append("&amp;")
                ch == '<' -> out.append("&lt;")
                ch == '>' -> out.append("&gt;")
                ch == '"' -> out.append("&quot;")
                ch == '\n' || ch == '\t' -> out.append(ch)
                // XML 1.0 forbids other control characters.
                ch.code >= 0x20 -> out.append(ch)
            }
        }
        return out.toString()
    }
}

package tj.msu.backend.tool

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.io.FileInputStream

@Component
class XlsInspector : CommandLineRunner {

    override fun run(vararg args: String?) {
        val files = listOf("enf.xls", "gf.xls")
        
        files.forEach { filename ->
            println("=== Inspecting $filename ===")
            try {
                FileInputStream(filename).use { fis ->
                    val workbook = WorkbookFactory.create(fis)
                    val sheet = workbook.getSheetAt(0)
                    
                    // Inspect first 20 rows, first 10 columns
                    for (r in 0..20) {
                        val row = sheet.getRow(r) ?: continue
                        val rowValues = StringBuilder()
                        for (c in 0..15) {
                            val cell = row.getCell(c)
                            if (cell != null) {
                                rowValues.append("[${cell.toString()}] ")
                            }
                        }
                        if (rowValues.isNotEmpty()) {
                           println("Row $r: $rowValues")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error reading $filename: ${e.message}")
            }
            println("================================\n")
        }
    }
}

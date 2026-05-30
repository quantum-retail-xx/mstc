package com.mstc.dashboard;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Reads lots from an Excel file (Sheet1). Expected columns per row:
 * lot number, quantity, girth, length, wastage percentage
 * For now we only use the lot number column (first column).
 */
public class ExcelLotReader {

    private final Path filePath;

    public ExcelLotReader(Path filePath) {
        this.filePath = filePath;
    }

    public Set<String> readLotNumbers() throws Exception {
        Set<String> lots = new HashSet<>();
        try (InputStream is = new FileInputStream(filePath.toFile()); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0); // sheet1
            if (sheet == null) return lots;

            int firstRow = sheet.getFirstRowNum();
            int lastRow = sheet.getLastRowNum();
            // Skip header row by starting at firstRow + 1
            for (int r = firstRow + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell c = row.getCell(0); // first column: lot number
                if (c == null) continue;
                String lotStr = null;
                try {
                    if (c.getCellType() == CellType.STRING) {
                        lotStr = c.getStringCellValue();
                    } else if (c.getCellType() == CellType.NUMERIC) {
                        double v = c.getNumericCellValue();
                        // remove trailing .0 if integer
                        if (v == Math.floor(v)) lotStr = String.valueOf((long) v);
                        else lotStr = String.valueOf(v);
                    } else {
                        lotStr = c.toString();
                    }
                } catch (Exception e) { lotStr = c.toString(); }

                if (lotStr != null) {
                    lotStr = lotStr.trim();
                    if (!lotStr.isEmpty()) {
                        // normalize e.g. remove .0
                        lotStr = lotStr.replaceAll("\\.0$", "");
                        lots.add(lotStr);
                    }
                }
            }
        }
        return lots;
    }
}



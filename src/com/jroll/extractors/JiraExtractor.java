package com.jroll.extractors;

import com.jroll.data.Ticket;
import com.opencsv.CSVReader;
import com.opencsv.bean.HeaderColumnNameTranslateMappingStrategy;
import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jroll on 9/27/15.
 * Extract data from the csv file
 */
public class JiraExtractor extends Extractor {



    public static ArrayList<HashMap<String, String>> parseCSVToMap(String fileName) throws IOException {

        HeaderColumnNameTranslateMappingStrategy<Ticket> beanStrategy = new HeaderColumnNameTranslateMappingStrategy<Ticket>();
        beanStrategy.setType(Ticket.class);

        CSVReader csvReader = new CSVReader(new FileReader(fileName));
        //List<String[]> content = csvReader.readAll();
        String[] header = csvReader.readNext();
        String[] row = csvReader.readNext();
        ArrayList<HashMap<String, String>> rowList = new ArrayList<HashMap<String, String>>();

        while (row != null) {
            int colIndex = 0;
            HashMap<String, String> rowMap = new HashMap<String, String>();
            for (String col : row) {
                rowMap.put(header[colIndex], col);
                //System.out.println(header[colIndex]);
                //System.out.println(col);
                colIndex++;
                if (colIndex >= header.length)
                    break;
            }
            row = csvReader.readNext();
            rowList.add(rowMap);
        }
        return rowList;
    }

    public static ArrayList<HashMap<String, String>> parseExcel(String fileName) throws IOException, BiffException {
        Workbook workbook = Workbook.getWorkbook(new File(fileName));
        Sheet sheet = workbook.getSheet(1);
        int numCols = 83;
        int row = 1;
        String[] header = new String[numCols];
        ArrayList<HashMap<String, String>> rowList = new ArrayList<HashMap<String, String>>();

        for (int i = 0; i < numCols; i++) {
            header[i] = sheet.getCell(i, 0).getContents();
            //System.out.println(header[i]);
        }

        for (row = 1; row < sheet.getRows(); row++) {
            if (!"qpid".equals(sheet.getRow(row)[0].getContents().trim().toLowerCase())) {
                System.out.println("||bad|| " + sheet.getRow(row)[0].getContents().toLowerCase());
                continue;
            }
            HashMap<String, String> rowMap = new HashMap<String, String>();
            int j = 0;
            for (Cell cell : sheet.getRow(row)) {

                rowMap.put(header[j], cell.getContents());
                //System.out.println(cell.getContents());
                j++;
            }
            rowList.add(rowMap);
        }
        return rowList;
    }
}

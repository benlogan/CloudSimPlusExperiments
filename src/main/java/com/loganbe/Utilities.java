package com.loganbe;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Utilities {

    public static void writeCsv(String csvContent, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(csvContent);
            System.out.println("CSV file written successfully to " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

}
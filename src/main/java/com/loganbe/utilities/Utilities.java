package com.loganbe.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Utilities {

    public static final Logger LOGGER = LoggerFactory.getLogger(Utilities.class.getSimpleName());

    public static void writeCsv(String csvContent, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(csvContent);
            // use of file:// etc is simply to make the link clickable in console
            LOGGER.info("CSV file written successfully to file://" + new File(filePath).getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error writing to file: " + e.getMessage());
        }
    }

}
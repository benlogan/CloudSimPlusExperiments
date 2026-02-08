package com.loganbe.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtilities {

    public static final Logger LOGGER = LoggerFactory.getLogger(FileUtilities.class.getSimpleName());

    public static void writeCsv(String csvContent, String filePathString) {
        String value = System.getenv("CSV");
        if(value.equals("false")) {
            LOGGER.error("Bypassing CSV Export!");
            return;
        }

        Path filePath = Paths.get(filePathString);
        try {
            // Create parent directories if they do not exist
            Files.createDirectories(filePath.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
                writer.write(csvContent);
                // use of file:// etc is simply to make the link clickable in console
                //LOGGER.info("CSV file written successfully to file://" + new File(filePath).getAbsolutePath());
            }

        } catch (IOException e) {
            LOGGER.error("Error writing to file", e);
        }
    }

}
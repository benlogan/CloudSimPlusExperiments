package com.loganbe.power;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import com.loganbe.utilities.Maths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * representing the carbon emissions of software, based on energy consumption
 * this could constitute a major PhD and framework contribution, with more work
 */
public class Carbon {

    public static final Logger LOGGER = LoggerFactory.getLogger(Carbon.class.getSimpleName());

    // carbon intensity (gCO₂/kWh)
    private static double CARBON_INTENSITY_DEFAULT = 160; // UK average

    private static double CARBON_INTENSITY_CLEAN = 40.5;
    private static double CARBON_INTENSITY_MIXED = 214;
    private static double CARBON_INTENSITY_DIRTY = 499.5;

    /**
     * convert energy (power over time) to carbon (emitted)
     * in kg
     * @param energy (kWh)
     * @return carbon (kg)
     */
    public static double energyToCarbon(double energy) {
        //double carbonG = energy * carbonIntensity;

        //double carbonG = energy * liveCarbonIntensity();

        double carbonG = energy * CARBON_INTENSITY_CLEAN;

        return Maths.scaleAndRound(carbonG/1000);
        //return carbonG/1000;
    }

    /**
     * return the current actual, via National Grid API
     * @return
     */
    public static double liveCarbonIntensity() {
        try {
            // define the API endpoint
            String apiUrl = "https://api.carbonintensity.org.uk/intensity";
            URL url = new URL(apiUrl);

            // open a connection to the API
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // check if the request was successful
            if (conn.getResponseCode() == 200) {
                // Read the response
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // parse the JSON response
                JSONObject jsonResponse = new JSONObject(response.toString());

                // "data" is an array, so get the first object from it
                JSONArray dataArray = jsonResponse.getJSONArray("data");
                JSONObject data = dataArray.getJSONObject(0);

                // now extract intensity and actual value
                JSONObject intensity = data.getJSONObject("intensity");
                int actual = intensity.getInt("actual");

                // output the actual carbon intensity
                LOGGER.info("Actual Carbon Intensity (UK): " + actual + " gCO₂/kWh");
                return actual;
            } else {
                LOGGER.error("Failed to fetch data. HTTP response code: " + conn.getResponseCode());
            }

            conn.disconnect();
        } catch (Exception e) {
            LOGGER.error("Exception while fetching Carbon Intensity : " + e.getMessage());
            //e.printStackTrace();
        }
        LOGGER.error("Failed to fetch Carbon Intensity, using default value!");
        return CARBON_INTENSITY_DEFAULT;
    }

}
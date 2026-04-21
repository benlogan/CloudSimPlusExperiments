package com.loganbe.utilities;

import java.util.List;
import java.util.Map;

public class ExportTimeSeriesCsv {

    // FIXME cumulative could just be calculated in Excel!
    public static void exportTimeSeriesData(Map<Long, ? extends List<?>> map, Map<Long, ? extends List<?>> mapCumulative, String name, String fileName) {
        // note this is not using the above averages, but the full time series!
        StringBuilder csv = new StringBuilder();

        // header
        String SEPERATOR = ";";
        csv.append("time");
        csv.append(SEPERATOR);
        csv.append(name);
        if(mapCumulative != null) {
            csv.append(SEPERATOR);
            csv.append(name + " cumulative");
        }
        /* // now just printing the first host only, to reduce processing (they are generally the same)
        hostUtil.keySet().stream()
                .sorted()
                .forEach(hostId -> csv.append(",host").append(hostId)); */
        csv.append("\n");

        // assume all hosts have the same number of samples
        int numSteps = map.values().iterator().next().size();

        // for each timestep, print values for each host (or just one of them!)
        for (int t = 0; t < numSteps; t++) {
            csv.append(t); // or actual time if you have it
            for (Long hostId : map.keySet().stream().sorted().toList()) {
                if(hostId == 0) { // always the first host - they will all be the same, normally
                    final Object value = map.get(hostId).get(t);
                    if(name.equals("sci")) {
                        csv.append(SEPERATOR).append(String.format("%.4f", ((Number) value).doubleValue()));
                        if(mapCumulative != null) {
                            csv.append(SEPERATOR).append(String.format("%.4f", ((Number) mapCumulative.get(hostId).get(t)).doubleValue()));
                        }
                    } else {
                        csv.append(SEPERATOR).append(value);
                        if(mapCumulative != null) {
                            csv.append(SEPERATOR).append(mapCumulative.get(hostId).get(t));
                        }
                    }
                } // don't bother logging all - the sim will generally distribute load evenly (so they will all be the same)
            }
            csv.append("\n");
        }

        FileUtilities.writeCsv(csv.toString(), "data/" + fileName + ".csv");
    }

}
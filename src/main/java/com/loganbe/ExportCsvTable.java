package com.loganbe;

import org.apache.commons.lang3.StringUtils;
import org.cloudsimplus.builders.tables.AbstractTable;
import org.cloudsimplus.builders.tables.CsvTableColumn;
import org.cloudsimplus.builders.tables.TableColumn;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enables the return of the actual Comma Separated Text (CSV) string, rather than simply printing it.
 *
 * @author Ben Logan
 */
public class ExportCsvTable extends AbstractTable {
    public ExportCsvTable() {
        this("");
    }

    public ExportCsvTable(final String title) {
        super(title);
        this.setColumnSeparator(",");
    }

    /**
     * CSV files doesn't have a title.
     */
    @Override
    public void printTitle() {/**/}

    /**
     * CSV files doesn't have a table opening line.
     */
    @Override
    public void printTableOpening() {/**/}

    /**
     * CSV files doesn't have a table closing line.
     */
    @Override
    public void printTableClosing() {/**/}

    /**
     * don't bother printing - we want to capture the string instead
     */
    @Override
    public void print() {/**/}

    /**
     * CSV files doesn't have a row opening line.
     * @return
     */
    @Override
    protected String rowOpening() { return ""; }

    @Override
    protected String rowClosing() {
        return "%n";
    }

    @Override
    protected String subtitleHeaderOpening() {
        return "";
    }

    /**
     * Creates a horizontal line with the same width of the table.
     * @return The string containing the horizontal line
     */
    protected String createHorizontalLine(final boolean includeColSeparator) {
        if(includeColSeparator){
            final StringBuilder sb = new StringBuilder(rowOpening());
            final String row =
                    getColumns()
                            .stream()
                            .map(col -> stringRepeat(getLineSeparator(), col.getTitle().length()))
                            .collect(Collectors.joining(getColumnSeparator()));
            return sb.append(row)
                    .append(rowClosing())
                    .toString();
        }

        return stringRepeat(getLineSeparator(), getLengthOfColumnHeadersRow()) + "%n";
    }

    /**
     * Creates a copy of the a string repeated a given number of times.
     * @param str The string to repeat
     * @param timesToRepeat The number of times to repeat the string
     * @return The string repeated the given number of times
     */
    protected final String stringRepeat(final String str, final int timesToRepeat) {
        return new String(new char[timesToRepeat]).replace("\0", str);
    }

    /**
     * Gets the number of characters of the column headers row.
     *
     * @return the number of characters of column headers row
     */
    protected final int getLengthOfColumnHeadersRow(){
        return getColumns().stream().mapToInt(col -> col.generateTitleHeader().length()).sum();
    }

    /**
     * Gets a given string and returns a formatted version of it
     * that is centralized in the table width.
     * @param str The string to be centralized
     * @return The centralized version of the string
     */
    protected String getCentralizedString(final String str) {
        final int indentationLength = (getLengthOfColumnHeadersRow() - str.length())/2;
        return "%n%s%s%n".formatted(StringUtils.repeat(" ", indentationLength), str);
    }

    public String getLineSeparator() {
        return "";
    }

    @Override
    public TableColumn newColumn(final String title, final String subtitle, final String format) {
        return new CsvTableColumn(title, subtitle, format);
    }

    /**
     * new method (hence new table class) to generate csv as a string
     * the rest of this class is unchanged (i.e. as per CsvTable)
     * does NOT scale for large tables - becomes very slow!
     * @return csv
     */
    public String getCsvString() {
        //System.out.println("getCsvString START");
        long startTime = System.currentTimeMillis();

        String csvString = "";

        for (TableColumn column : this.getColumns()) {
            csvString += column.generateTitleHeader();
        }
        csvString += '\n';

        for (TableColumn column : this.getColumns()) {
            csvString += column.generateSubtitleHeader();
        }
        csvString += '\n';

        //System.out.println("getCsvString MID"); // scaling problem with rows...
        for (List<Object> row : this.getRows()) {
            csvString += row.toString().replace('[',' ').replace(']',' ').strip().replaceAll("\\s+","") + '\n';
        }

        System.out.println("getCsvString END : " + (System.currentTimeMillis() - startTime) + "ms");
        return csvString;
    }

}
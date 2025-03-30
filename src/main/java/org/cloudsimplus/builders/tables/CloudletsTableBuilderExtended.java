package org.cloudsimplus.builders.tables;

import org.cloudsimplus.cloudlets.Cloudlet;

import java.util.List;

public class CloudletsTableBuilderExtended extends CloudletsTableBuilder {

    private String lengthFormat = DEF_FORMAT;

    private static final String MI = "MI";

    public CloudletsTableBuilderExtended(List<? extends Cloudlet> list) {
        super(list);
    }

    @Override
    protected void createTableColumns() {
        super.createTableColumns();

        addColumn(getTable().newColumn("CloudletTotalLen", MI, lengthFormat), Cloudlet::getTotalLength);
    }

}
package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;

/**
 * read the specification details from YAML (legacy template)
 */
public class SimSpecFromFileLegacy implements SimSpecInterface {

    private LegacyTemplate legacyTemplate;

    public SimSpecFromFileLegacy(String filename) {
        LoaderOptions loaderOptions = new LoaderOptions();

        Yaml yaml = new Yaml(new Constructor(LegacyTemplate.class, loaderOptions));

        InputStream inputStream = SimSpecFromFileLegacy.class
                .getClassLoader()
                .getResourceAsStream(filename);

        if (inputStream == null) {
            throw new RuntimeException(filename + " not found!");
        }

        legacyTemplate = yaml.load(inputStream);

        //System.out.println("Host Bandwidth : " + legacyTemplate.getHostSpecification().getHost_bw()); // testing numbers with _
    }

    @Override
    public CloudletScheduler getScheduler() {
        return new CustomCloudletScheduler();
    }

    @Override
    public HostSpecification getHostSpecification() {
        return legacyTemplate.getHostSpecification();
    }

    @Override
    public VmSpecification getVmSpecification() {
        return legacyTemplate.getVmSpecification();
    }

    @Override
    public CloudletSpecification getCloudletSpecification() {
        return legacyTemplate.getCloudletSpecification();
    }

}
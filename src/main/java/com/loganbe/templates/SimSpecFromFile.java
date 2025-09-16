package com.loganbe.templates;

import com.loganbe.templates.cloudlet.CloudletSpecification;
import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.List;

/**
 * read the specification details from YAML (strategic, not legacy, template)
 */
public class SimSpecFromFile implements SimSpecInterface {

    public SimSpecFromFile(String filename) {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(HostConfig.class, loaderOptions));

        InputStream inputStream = SimSpecFromFile.class
                .getClassLoader()
                .getResourceAsStream(filename);

        if (inputStream == null) {
            throw new RuntimeException(filename + " not found!");
        }

        HostConfig config = yaml.load(inputStream);

        /*
        for (ServersSpecification host : config.getServers()) {
            System.out.println("Server: " + host.getName());
        }
        System.out.println("Server Count: " + config.getServers().size());
        */

        serversSpecifications = config.getServers();

        cloudletSpecification = config.getCloudletSpecification();
    }

    private List<ServersSpecification> serversSpecifications;
    private CloudletSpecification cloudletSpecification;

    @Override
    public CloudletScheduler getScheduler() {
        return new CustomCloudletScheduler();
    }

    public List<ServersSpecification> getServerSpecifications() {
        return serversSpecifications;
    }

    @Override
    public VmSpecification getVmSpecification() {
        return null; // FIXME for now VM's will be built using the host details, they aren't specified in the config
    }

    @Override
    public CloudletSpecification getCloudletSpecification() {
        return cloudletSpecification;
    }

    public HostSpecification getHostSpecification() {
        return null; // FIXME will never be used, just to enable compilation when trying to dual run old & new template
    }
}
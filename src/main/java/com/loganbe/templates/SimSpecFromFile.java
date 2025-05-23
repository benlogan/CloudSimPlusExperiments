package com.loganbe.templates;

import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CustomCloudletScheduler;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;

/**
 * read the specification details from YAML (new template)
 * WIP
 */
public class SimSpecFromFile implements SimSpecInterface {

    private ServersSpecification serversSpecification;

    public SimSpecFromFile(String filename) {
        LoaderOptions loaderOptions = new LoaderOptions();
        //Yaml yaml = new Yaml(new Constructor(HostConfig.class, loaderOptions));
        Yaml yaml = new Yaml(new Constructor(ServersSpecification.class, loaderOptions));

        InputStream inputStream = SimSpecFromFile.class
                .getClassLoader()
                .getResourceAsStream(filename);

        if (inputStream == null) {
            throw new RuntimeException(filename + " not found!");
        }

        //HostConfig config = yaml.load(inputStream);
        serversSpecification = yaml.load(inputStream);

        /*
        for (HostSpecification host : config.getServers()) {
            System.out.println("Server: " + host.getName() + ", IP: " + host.getIp());
        }*/
        //System.out.println("Server Count: " + hostSpecification.getHosts());
    }

    @Override
    public CloudletScheduler getScheduler() {
        return new CustomCloudletScheduler();
    }

    @Override
    public HostSpecification getHostSpecification() {
        return null; //FIXME
    }

    @Override
    public VmSpecification getVmSpecification() {
        return null; //FIXME
    }

    @Override
    public CloudletSpecification getCloudletSpecification() {
        return null;
    }

}
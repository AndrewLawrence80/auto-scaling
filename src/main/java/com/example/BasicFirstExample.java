package com.example;

import java.util.ArrayList;
import java.util.List;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletExecution;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.CloudletVmEventInfo;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicFirstExample {
    private static final class Config {
        // Datacenter
        public static final class Datacenter {
            public static final double SCHEDULING_INTERVAL = 1; // Interval indicates how often the datacenter handles
                                                                // events such as Vm allocating and Cloudlet processing
        }

        // Host
        public static final class Host {
            public static final int NUM = 64; // Number of physical hosts in the datacenter
            public static final int PES = 64; // Number of CPU cores per host
            public static final long MIPS = (long) (1 * 1e3); // CPU rate, 1GHz
            public static final long RAM = Long.MAX_VALUE; // Infinite RAM (MB)
            public static final long BW = Long.MAX_VALUE; // Infinite bandwidth (Mbps)
            public static final long STORAGE = Long.MAX_VALUE;// Infinite storage (MB)
        }

        // Vm
        public static final class Vm {
            public static final int NUM = 1; // Create 1 Vm initially
            public static final int PES = 1; // 1 CPU core per Vm
            public static final long MIPS = (long) (1 * 1e3); // Same as host, assuming no performance loss
            public static final long RAM = 1024; // 1G by default
            public static final long BW = 100; // 100Mbps by default
            public static final long STORAGE = (long) (10 * 1e3); // 10G by default
            public static final int STARTUP_DELAY = 30; // Take 30s to bootup
            public static final int SHUTDOWN_DELAY = 10; // Take 10s to shutdown
        }

        // Cloudlet
        public static final class Cloudlet {
            public static final int NUM = 2; // Create 2 cloudlet to run in first example
            public static final int PES = 2; // Required CPU cores
            /**
             * ========= Important =========
             * Since the number of requested CPU cores per Cloudlet is 2,
             * while there is only 1 PE available on the vm,
             * to run a cloudlet taking 1s per CPU (in maximum utilization model) will
             * result in 2s execution time.
             * ========= Important =========
             */
            public static final long LENGTH = (long) (1 * Vm.MIPS); // 1s for 1 CPU core to run
            public static final double CPU_USAGE_FACTOR = 1; // 100% CPU usage
            public static final double RAM_USAGE_FACTOR = 1e-3; // 1MB RAM
            public static final double BW_USAGE_FACTOR = 1e-2; // 1Mbps bandwidth
        }
    }

    private CloudSimPlus simulation; // Simulation takes responsibility to run simulation globally, monitor events,
                                     // etc., it's recommended to make it a class member
    private Datacenter datacenter; // Datacenter to run simulation, must be a class member
    private DatacenterBroker broker; // Represents a broker acting on behalf of a cloud customer.
                                     // It hides VM management such as vm creation, submission of cloudlets to VMs
                                     // and destruction of VMs.
                                     // A broker implements the policies for selecting a VM to run a Cloudlet
                                     // and a Datacenter to run the submitted VMs.
    private long numVmCreated;
    private long numCloudletCreated;

    private void logVmStatus(EventInfo info) {
        final long LOG_INTERVAL = 1;
        long time = (long) info.getTime();
        if (time % LOG_INTERVAL == 0) {
            logVmStatus(broker.getVmExecList(), time);
        }
    }

    /*========= Following code can be replaced using Logutils ========= */
    public void logVmStatus(List<Vm> vmList, double time) {
        Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        for (Vm vm : vmList) {
            logger.info(String.format(
                    "%.2f:\t Vm %d, CPU usage %.2f%% (%d vCPUs), RAM usage %.2f%% (%dMB/%dMB, %d MB available), BW usage %.2f%% (%dMbps/%dMbps, %d Mbps available)",
                    time,
                    vm.getId(),
                    vm.getCpuPercentUtilization() * 100, vm.getPesNumber(),
                    vm.getRam().getPercentUtilization() * 100, vm.getRam().getAllocatedResource(),
                    vm.getRam().getCapacity(), vm.getRam().getAvailableResource(),
                    vm.getBw().getPercentUtilization() * 100, vm.getBw().getAllocatedResource(),
                    vm.getBw().getCapacity(), vm.getBw().getAvailableResource()));
            List<CloudletExecution> cloudletExecutionList = vm.getCloudletScheduler().getCloudletExecList();
            for (CloudletExecution cloudletExecution : cloudletExecutionList) {
                logger.info(String.format(
                        "|------\t Cloudlet %d, length %d, CPU utilization %.2f%%, RAM utilization %.2f%%, BW utilization %.2f%%",
                        cloudletExecution.getCloudlet().getId(),
                        cloudletExecution.getCloudlet().getLength(),
                        cloudletExecution.getCloudlet().getUtilizationOfCpu() * 100,
                        cloudletExecution.getCloudlet().getUtilizationOfRam() * 100,
                        cloudletExecution.getCloudlet().getUtilizationOfBw() * 100));
            }
        }
    }

    public void simulationSummary(DatacenterBroker broker) {
        List<Cloudlet> cloudletList = broker.getCloudletSubmittedList();
        new CloudletsTableBuilder(cloudletList).build();
    }
    /*========= Above code can be replaced using Logutils ========= */


    private void createSimulation() {
        this.simulation = new CloudSimPlus();
        this.simulation.terminateAt(70);
        this.simulation.addOnClockTickListener(new EventListener<EventInfo>() {

            @Override
            public void update(EventInfo info) {
                logVmStatus(info);
            }

        });
    }

    private void createDatacenter() {
        assert this.simulation != null : "Simulation must be initialized before creating datacenter";
        this.datacenter = new DatacenterSimple(this.simulation, this.createHostList());
        this.datacenter.setSchedulingInterval(Config.Datacenter.SCHEDULING_INTERVAL);
    }

    private void createDatacenterBroker() {
        assert this.simulation != null : "Simulation must be initialized before creating datacenterbroker";
        this.broker = new DatacenterBrokerSimple(this.simulation);
    }

    private List<Host> createHostList() {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < Config.Host.NUM; ++i) {
            // Create CPU cores for physical host
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < Config.Host.PES; ++j) {
                peList.add(new PeSimple(Config.Host.MIPS));
            }
            Host host = new HostSimple(Config.Host.RAM, Config.Host.BW, Config.Host.STORAGE, peList);
            hostList.add(host);
        }
        return hostList;
    }

    private List<Vm> createVmList() {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < Config.Vm.NUM; ++i) {
            Vm vm = new VmSimple(this.numVmCreated, Config.Vm.MIPS, Config.Vm.PES);
            vm.setRam(Config.Vm.RAM);
            vm.setBw(Config.Vm.BW);
            vm.setSize(Config.Vm.STORAGE);
            /*
             * Using anonymous inner class to implement listener,
             * which can be replaced by a lambda expression in jdk 1.8 or later
             */
            vm.addOnHostAllocationListener(new EventListener<VmHostEventInfo>() {

                @Override
                public void update(VmHostEventInfo info) {
                    /**
                     * see {@link BasicFirstExample#createEmptyCloudlet()}
                     */
                    Cloudlet emptyCloudlet = createEmptyCloudlet();
                    broker.submitCloudlet(emptyCloudlet);
                    broker.bindCloudletToVm(emptyCloudlet, info.getVm());
                }

            });
            vmList.add(vm);
            ++this.numVmCreated;
        }
        return vmList;
    }

    /**
     * Empty Cloudlet is used to solve the bug that
     * the Vm RAM won't release after a running Cloudlet is finished
     * 
     * @see <a href="https://github.com/cloudsimplus/cloudsimplus/issues/429" />
     *      It takes about 1(MI)/[1e3(MIPS)*0.01]=0.1s to finish
     */
    private Cloudlet createEmptyCloudlet() {
        Cloudlet cloudlet = new CloudletSimple(numCloudletCreated, 1, 1);
        cloudlet.setUtilizationModelCpu(new UtilizationModelDynamic(1));
        cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0));
        cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0));
        ++numCloudletCreated;
        return cloudlet;
    }

    private List<Cloudlet> createCloudletList() {
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < Config.Cloudlet.NUM; ++i) {
            Cloudlet cloudlet = new CloudletSimple(numCloudletCreated, Config.Cloudlet.LENGTH, Config.Cloudlet.PES);
            cloudlet.setUtilizationModelCpu(new UtilizationModelDynamic(Config.Cloudlet.CPU_USAGE_FACTOR));
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(Config.Cloudlet.BW_USAGE_FACTOR));
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(Config.Cloudlet.RAM_USAGE_FACTOR));
            cloudlet.addOnFinishListener(new EventListener<CloudletVmEventInfo>() {

                @Override
                public void update(CloudletVmEventInfo info) {
                    Cloudlet emptyCloudlet = createEmptyCloudlet();
                    broker.submitCloudlet(emptyCloudlet);
                    broker.bindCloudletToVm(emptyCloudlet, info.getVm());
                }

            });
            cloudletList.add(cloudlet);
            ++this.numCloudletCreated;
        }
        return cloudletList;
    }

    private BasicFirstExample() {
        createSimulation();
        createDatacenter();
        createDatacenterBroker();
        this.broker.submitVmList(createVmList());
        this.broker.submitCloudletList(createCloudletList());
        this.simulation.start();
        simulationSummary(this.broker);
    }

    public static void main(String[] args) {
        new BasicFirstExample();
    }
}

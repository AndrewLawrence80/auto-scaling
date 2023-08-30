package com.example;

import java.util.List;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletExecution;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtils {

    public static void logVmStatus(List<Vm> vmList, double time) {
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

    public static void simulationSummary(DatacenterBroker broker) {
        List<Cloudlet> cloudletList = broker.getCloudletSubmittedList();
        new CloudletsTableBuilder(cloudletList).build();
    }
}

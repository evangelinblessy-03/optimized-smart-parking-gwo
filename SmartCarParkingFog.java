package org.fog.test.perfeval;

import java.util.*;
import java.io.File;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.*;
import org.cloudbus.cloudsim.sdn.overbooking.*;

import org.fog.application.*;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.*;
import org.fog.utils.distribution.DeterministicDistribution;

public class SmartCarParkingFog {

    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();

    static int numOfAreas = 2;
    static int numOfCamerasPerArea1 = 4;

    static double CAM_TRANSMISSION_TIME = 30;

    // ✅ DATASET STORAGE
    static List<Double> datasetLatencies = new ArrayList<>();

    private static boolean CLOUD = false;

    public static void main(String[] args) {

        Log.printLine("Starting smart car parking system...");

        try {
            Log.disable();

            CloudSim.init(1, Calendar.getInstance(), false);

            String appId = "dcns";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId);

            // ✅ LOAD DATASET
            datasetLatencies = loadDataset();
            System.out.println("Dataset Loaded: " + datasetLatencies.size());

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

            // EDGE MAPPING
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("c")) {
                    moduleMapping.addModuleToDevice("picture-capture", device.getName());
                }
            }

            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("a")) {
                    moduleMapping.addModuleToDevice("slot-detector", device.getName());
                }
            }

            // CLOUD MODE
            if (CLOUD) {
                moduleMapping.addModuleToDevice("picture-capture", "cloud");
                moduleMapping.addModuleToDevice("slot-detector", "cloud");
            }

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            controller.submitApplication(application,
                    (CLOUD) ? new ModulePlacementMapping(fogDevices, application, moduleMapping)
                            : new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Simulation finished!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ✅ DATASET LOADER
    public static List<Double> loadDataset() {
        List<Double> latencies = new ArrayList<>();

        try {
            File file = new File("parking_trace_CNR.csv");

            System.out.println("Reading file from: " + file.getAbsolutePath());

            if (!file.exists()) {
                System.out.println("Dataset NOT FOUND");
                return latencies;
            }

            Scanner sc = new Scanner(file);

            if (sc.hasNextLine()) sc.nextLine();

            while (sc.hasNextLine()) {
                String[] data = sc.nextLine().split(",");

                try {
                    latencies.add(Double.parseDouble(data[3]));
                } catch (Exception ignored) {
                }
            }

            sc.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return latencies;
    }

    private static void createFogDevices(int userId, String appId) {

        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 1600, 1300);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0, 100, 80);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        for (int i = 0; i < numOfAreas; i++) {
            addArea(i + "", userId, appId, proxy.getId());
        }
    }

    private static FogDevice addArea(String id, int userId, String appId, int parentId) {

        FogDevice router = createFogDevice("a-" + id, 2800, 4000, 1000, 10000, 2, 0, 100, 80);
        router.setParentId(parentId);
        router.setUplinkLatency(2);
        fogDevices.add(router);

        for (int i = 0; i < numOfCamerasPerArea1; i++) {
            String camId = id + "-" + i;
            FogDevice cam = addCamera(camId, userId, appId, router.getId());
            cam.setUplinkLatency(2);
            fogDevices.add(cam);
        }

        return router;
    }

    private static FogDevice addCamera(String id, int userId, String appId, int parentId) {

        FogDevice camera = createFogDevice("c-" + id, 500, 1000, 10000, 10000, 3, 0, 80, 60);
        camera.setParentId(parentId);

        // ✅ USE DATASET HERE
        double interval = CAM_TRANSMISSION_TIME;

        if (!datasetLatencies.isEmpty()) {
            interval = datasetLatencies.get(new Random().nextInt(datasetLatencies.size()));
        }

        Sensor sensor = new Sensor("s-" + id, "CAMERA", userId, appId,
                new DeterministicDistribution(interval));

        sensor.setGatewayDeviceId(camera.getId());
        sensor.setLatency(40.0);
        sensors.add(sensor);

        Actuator ptz = new Actuator("ptz-" + id, userId, appId, "PTZ_CONTROL");
        ptz.setGatewayDeviceId(parentId);
        ptz.setLatency(1.0);
        actuators.add(ptz);

        return camera;
    }

    private static FogDevice createFogDevice(String name, long mips, int ram,
                                             long upBw, long downBw, int level,
                                             double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

        PowerHost host = new PowerHost(FogUtils.generateEntityId(),
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(10000),
                1000000,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower));

        FogDevice device = null;

        try {
            device = new FogDevice(name,
                    new FogDeviceCharacteristics("x86", "Linux", "Xen", host,
                            10.0, 3.0, 0.05, 0.001, 0.0),
                    new AppModuleAllocationPolicy(Collections.singletonList(host)),
                    new LinkedList<>(), 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        device.setLevel(level);
        return device;
    }

    private static Application createApplication(String appId, int userId) {

        Application app = Application.createApplication(appId, userId);

        app.addAppModule("picture-capture", 10);
        app.addAppModule("slot-detector", 10);

        app.addAppEdge("CAMERA", "picture-capture", 1000, 500, "CAMERA", Tuple.UP, AppEdge.SENSOR);
        app.addAppEdge("picture-capture", "slot-detector", 1000, 500, "slots", Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("slot-detector", "PTZ_CONTROL", 100, 50, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR);

        app.addTupleMapping("picture-capture", "CAMERA", "slots", new FractionalSelectivity(1.0));
        app.addTupleMapping("slot-detector", "slots", "PTZ_PARAMS", new FractionalSelectivity(1.0));

        return app;
    }
}
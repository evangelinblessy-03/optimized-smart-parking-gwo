package org.fog.test.perfeval;

import java.io.File;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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

class Nod {
    String name;
    int load;
    Nod(String n, int l){ name=n; load=l; }
}

public class SmartCarParkingCNR_PARK {

    // ===== Constants =====
    private static final int NUM_CAMERAS = 4;
    private static final double CAM_TRANSMISSION_TIME = 30;
    private static final int LOAD_THRESHOLD = 120; // adjustable threshold
    private static final String DEFAULT_DEVICE = "a-0";
    private static final String CLOUD_DEVICE = "cloud";
    private static final String[] DATASET_PATHS = {
            "data/CNRParkEXT.csv",
            "CNRParkEXT.csv",
            "data/CNRParkEXT (1).csv",
            "CNRParkEXT (1).csv"
    };

    // ===== Simulation Entities =====
    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();
    static List<String[]> dataset = new ArrayList<>();

    public static void main(String[] args) {
        try {
            Log.disable();
            CloudSim.init(1, Calendar.getInstance(), false);

            loadDataset();

            FogBroker broker = new FogBroker("broker");
            Application app = createApplication("smart-parking-gwo", broker.getId());
            createFogDevices(broker.getId(), "smart-parking-gwo");

            // Module placement after balancing
            ModuleMapping moduleMapping = performOptimizedPlacement();

            performLoadBalancing();

            runSimulation(app, moduleMapping);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Dataset Loader =====
    private static void loadDataset() {
        boolean loaded = false;
        for (String path : DATASET_PATHS) {
            File file = new File(path);
            if (!file.exists()) continue;

            System.out.println("📂 Loading from: " + file.getAbsolutePath());

            try {
                Files.lines(Paths.get(path))
                        .skip(1)
                        .map(line -> line.split(","))
                        .forEach(dataset::add);

                System.out.println("✅ Dataset Loaded: " + dataset.size());
                loaded = true;
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!loaded) {
            System.out.println("❌ Dataset NOT found!");
            System.out.println("📍 Working Dir: " + new File(".").getAbsolutePath());
        }
    }

    // ===== Application Creator =====
    private static Application createApplication(String appId, int userId) {
        Application app = Application.createApplication(appId, userId);

        app.addAppModule("picture-capture", 3000);
        app.addAppModule("slot-detector", 10000);

        app.addAppEdge("CAMERA", "picture-capture", 400, 120, "CAMERA", Tuple.UP, AppEdge.SENSOR);
        app.addAppEdge("picture-capture", "slot-detector", 800, 500, "SLOTS", Tuple.UP, AppEdge.MODULE);
        app.addAppEdge("slot-detector", "PTZ_CONTROL", 100, 50, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR);

        app.addTupleMapping("picture-capture", "CAMERA", "SLOTS", new FractionalSelectivity(1.0));
        app.addTupleMapping("slot-detector", "SLOTS", "PTZ_PARAMS", new FractionalSelectivity(1.0));

        AppLoop loop = new AppLoop(Arrays.asList("CAMERA", "picture-capture", "slot-detector", "PTZ_CONTROL"));
        app.setLoops(Collections.singletonList(loop));

        return app;
    }

    // ===== Fog Device Setup =====
    private static void createFogDevices(int userId, String appId) {
        // Cloud
        FogDevice cloud = createFogDevice(CLOUD_DEVICE,44800,40000,100,10000,0,0.01,16*103,16*83.25);
        cloud.setParentId(-1); fogDevices.add(cloud);

        // Proxy server
        FogDevice proxy = createFogDevice("proxy-server",2800,4000,10000,10000,1,0.0,107.339,83.4333);
        proxy.setParentId(cloud.getId()); proxy.setUplinkLatency(30); fogDevices.add(proxy);

        // Edge router
        FogDevice router = createFogDevice(DEFAULT_DEVICE,2800,4000,1000,10000,2,0.0,107.339,83.4333);
        router.setParentId(proxy.getId()); router.setUplinkLatency(15); fogDevices.add(router);

        // Cameras, sensors, actuators
        for (int i = 0; i < NUM_CAMERAS; i++) {
            FogDevice cam = createFogDevice("c-0-"+i,500,1000,10000,10000,3,0,87.53,82.44);
            cam.setParentId(router.getId()); cam.setUplinkLatency(5); fogDevices.add(cam);

            Sensor sensor = new Sensor("s-0-"+i,"CAMERA",userId,appId,new DeterministicDistribution(CAM_TRANSMISSION_TIME));
            sensor.setGatewayDeviceId(cam.getId()); sensor.setLatency(5.0); sensors.add(sensor);

            Actuator actuator = new Actuator("ptz-0-"+i,userId,appId,"PTZ_CONTROL");
            actuator.setGatewayDeviceId(router.getId()); actuator.setLatency(5.0); actuators.add(actuator);
        }
    }

    private static FogDevice createFogDevice(String name,long mips,int ram,long upBw,long downBw,int level,double ratePerMips,double busyPower,double idlePower){
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0,new PeProvisionerOverbooking(mips)));

        PowerHost host = new PowerHost(FogUtils.generateEntityId(),
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(10000),
                1000000, peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower,idlePower));

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                "x86","Linux","Xen",host,10.0,3.0,0.05,0.001,0.0);

        FogDevice device = null;
        try {
            device = new FogDevice(name, characteristics,
                    new AppModuleAllocationPolicy(Collections.singletonList(host)),
                    new LinkedList<>(),10,upBw,downBw,0,ratePerMips);
        } catch (Exception e) { e.printStackTrace(); }

        device.setLevel(level);
        return device;
    }

    // ===== Load Balancing =====
    private static void performLoadBalancing() {
        List<Nod> nodes = new ArrayList<>();
        Map<String,Integer> deviceLoad = new HashMap<>();
        fogDevices.forEach(d -> deviceLoad.put(d.getName(),0));

        // Count dataset entries for default device
        for(String[] row : dataset)
            if(row.length > 8 && row[8].equals("1"))
                deviceLoad.put(DEFAULT_DEVICE, deviceLoad.get(DEFAULT_DEVICE)+1);

        fogDevices.forEach(d -> nodes.add(new Nod(d.getName(), deviceLoad.get(d.getName()))));

        System.out.println("\n[Sender LB] Before balancing:");
        nodes.forEach(n -> System.out.println(n.name + ": " + n.load));

        nodes.stream()
             .filter(n -> n.load > LOAD_THRESHOLD)
             .forEach(s -> nodes.stream()
                     .filter(r -> r != s && r.load < LOAD_THRESHOLD)
                     .forEach(r -> {
                         int offload = (s.load - r.load)/2;
                         s.load -= offload;
                         r.load += offload;
                         System.out.println("[Sender LB] " + s.name + " offloads " + offload + " to " + r.name);
                     }));

        System.out.println("[Sender LB] After balancing:");
        nodes.forEach(n -> System.out.println(n.name + ": " + n.load));
        System.out.println();
    }

    // ===== Optimized Module Placement =====
    private static ModuleMapping performOptimizedPlacement() {
        ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

        FogDevice edgeDevice = fogDevices.stream()
                .filter(d -> d.getName().equals(DEFAULT_DEVICE))
                .findFirst().orElse(fogDevices.get(0));

        FogDevice proxyDevice = fogDevices.stream()
                .filter(d -> d.getName().equals("proxy-server"))
                .findFirst().orElse(fogDevices.get(0));

        // Place picture-capture on proxy
        moduleMapping.addModuleToDevice("picture-capture", proxyDevice.getName());
        System.out.println("[Module Placement] picture-capture on " + proxyDevice.getName());

        // Place slot-detector on edge if RAM allows, otherwise proxy
        long requiredRam = 10000;
        long availableRam = edgeDevice.getHostList().get(0).getRamProvisioner().getAvailableRam();
        String slotDeviceName = (availableRam >= requiredRam) ? edgeDevice.getName() : proxyDevice.getName();

        moduleMapping.addModuleToDevice("slot-detector", slotDeviceName);
        System.out.println("[Module Placement] slot-detector on " + slotDeviceName);

        return moduleMapping;
    }

    // ===== Simulation Runner =====
    private static void runSimulation(Application app, ModuleMapping moduleMapping) {
        ModulePlacement placement = new ModulePlacementMapping(fogDevices, app, moduleMapping);
        Controller controller = new Controller("controller", fogDevices, sensors, actuators);
        controller.submitApplication(app, placement);

        TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

        CloudSim.startSimulation();
        CloudSim.stopSimulation();

        Log.printLine("Simulation finished successfully!");
    }


    // ===== Fault-tolerant placement =====
    private static String ftPlaceModule(String moduleName, FogDevice device) {
        long requiredRam = moduleName.equals("slot-detector") ? 10000 : 3000;
        long availableRam = device.getHostList().get(0).getRamProvisioner().getAvailableRam();

        if (availableRam < requiredRam || device.getName().startsWith("c-")) {
            return CLOUD_DEVICE; // fallback to cloud
        }
        return device.getName();
    }

    // ===== Grey Wolf Optimizer =====
    public static class GreyWolfOptimizer {
        private int numAgents, maxIter, numModules, numDevices;
        private Random rand = new Random();

        public GreyWolfOptimizer(int numAgents,int maxIter,int numModules,int numDevices){
            this.numAgents=numAgents; this.maxIter=maxIter;
            this.numModules=numModules; this.numDevices=numDevices;
        }

        public int[] optimize(FitnessFunction fitness){
            int[][] wolves = new int[numAgents][numModules];
            double[] scores = new double[numAgents];

            for(int i=0;i<numAgents;i++){
                for(int j=0;j<numModules;j++)
                    wolves[i][j]=rand.nextInt(numDevices);
                scores[i]=fitness.evaluate(wolves[i]);
            }

            int[] alpha = wolves[0].clone();
            double alphaScore = scores[0];

            for(int i=1;i<numAgents;i++){
                if(scores[i]<alphaScore){
                    alphaScore=scores[i];
                    alpha=wolves[i].clone();
                }
            }

            for(int iter=0; iter<maxIter; iter++){
                for(int i=0;i<numAgents;i++){
                    for(int j=0;j<numModules;j++)
                        wolves[i][j] = Math.max(0, Math.min(numDevices-1, alpha[j]+rand.nextInt(3)-1));

                    scores[i] = fitness.evaluate(wolves[i]);
                    if(scores[i]<alphaScore){
                        alphaScore = scores[i];
                        alpha = wolves[i].clone();
                    }
                }
            }
            return alpha;
        }

        public interface FitnessFunction {
            double evaluate(int[] solution);
        }
    }
}

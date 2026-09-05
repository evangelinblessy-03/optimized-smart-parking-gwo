# Optimized Smart Car Parking Application in Fog Computing Using Grey Wolf Optimization

A fog computing-based smart car parking system that integrates Grey Wolf Optimization (GWO), Sender-Initiated Load Balancing (SILB), and fault tolerance to improve resource utilization, reduce execution delay, and enhance system reliability.

## 📌 About the Project

This project proposes an intelligent smart car parking framework for real-time parking management.

The system uses IoT devices such as cameras and sensors to collect parking information. Instead of sending all data directly to the cloud, the system processes data through nearby fog devices.

Grey Wolf Optimization is used to determine the optimal placement of application modules across fog devices. Sender-Initiated Load Balancing distributes workloads from overloaded nodes to underloaded nodes, while fault tolerance mechanisms provide retry and fallback support.

The system is designed and evaluated using the iFogSim simulation environment.

## 🎯 Problem Statement

Traditional and cloud-based parking systems may experience high latency, network congestion, inefficient resource utilization, and limited fault handling.

These limitations can affect real-time parking management and system performance.

This project addresses these challenges by combining fog computing with optimization, load balancing, and fault tolerance mechanisms.

## 🎯 Objectives

- Reduce execution delay through fog-based processing.
- Optimize application module placement using Grey Wolf Optimization.
- Improve resource utilization across fog devices.
- Reduce unnecessary communication overhead.
- Distribute workloads efficiently using Sender-Initiated Load Balancing.
- Improve system reliability through retry and fallback mechanisms.
- Develop a scalable framework for smart parking environments.

## 🛠️ Technologies Used

- Java
- iFogSim
- CloudSim
- Eclipse IDE
- Fog Computing
- Internet of Things (IoT)
- Grey Wolf Optimization (GWO)
- Sender-Initiated Load Balancing (SILB)
- Fault Tolerance
- Windows 11 Pro
- JDK 8 or above

## 🏗️ System Architecture

The proposed system follows a three-layer architecture:

### 1. IoT Layer

- Cameras and sensors monitor parking areas.
- Parking slot availability and vehicle presence are collected.
- Data is transmitted to nearby fog nodes.

### 2. Fog Layer

- Processes parking data locally.
- Performs application module placement using GWO.
- Monitors device workload.
- Performs load balancing using SILB.
- Handles failures using retry and fallback mechanisms.

### 3. Cloud Layer

- Provides backup storage and additional computational resources.
- Supports the system when fog resources are insufficient.

## ⚙️ How the System Works

1. IoT devices collect parking information.
2. Data is transmitted to nearby fog nodes.
3. Fog devices process the data locally.
4. GWO determines suitable application module placement.
5. Device capacity, workload, and network latency are evaluated.
6. Overloaded fog nodes initiate task transfer using SILB.
7. Failed tasks are retried.
8. If retries fail, tasks are moved to another fog node or the cloud.
9. Parking information is delivered to the user interface or parking display system.

## 🧠 Grey Wolf Optimization

Grey Wolf Optimization is a nature-inspired metaheuristic algorithm used to identify efficient application module placement across fog devices.

In this project, the optimization considers factors such as:

- Network latency
- Communication distance
- Processing capability
- Energy consumption
- Resource availability

The objective is to reduce execution delay and improve resource utilization.

## ⚖️ Sender-Initiated Load Balancing

The system uses Sender-Initiated Load Balancing to manage workload distribution.

When a fog device becomes overloaded, it identifies an underloaded node and transfers tasks.

This helps to:

- Balance workload distribution
- Reduce communication overhead
- Improve resource utilization
- Prevent performance degradation

## 🛡️ Fault Tolerance

The system includes retry and fallback mechanisms.

- Failed module execution is retried up to three times.
- If execution continues to fail, the task is reassigned to another fog node or the cloud.
- This helps maintain continuous system operation.

## 📊 Dataset

The project uses a smart parking simulation dataset containing parameters such as:

- Number of parking areas
- Number of cameras
- Camera transmission delay
- Number of application modules
- Fog and cloud computational capacities
- Network latency
- Optimization parameters
- Load threshold
- Retry attempts

The dataset is used to evaluate the system under different workloads and configurations.

## 📈 Performance Results

The proposed system was compared with an approach without GWO and SILB.

| Metric | Without GWO + SILB | With GWO + SILB |
|---|---:|---:|
| Execution Delay | 1200 ms | 300 ms |
| Energy Consumption | 950 J | 620 J |
| Network Usage | 520 MB | 280 MB |
| Fault Recovery | 85% | 97% |

### Key Improvements

- Execution delay reduced by approximately 75%.
- Energy consumption reduced by approximately 35%.
- Network usage reduced by approximately 46%.
- Fault recovery improved from 85% to 97%.

These results demonstrate the benefits of combining GWO, SILB, and fault tolerance in a fog-based smart parking system.

## 💻 Simulation Environment

The system is implemented and evaluated using:

- **Programming Language:** Java
- **Simulation Tool:** iFogSim
- **Underlying Framework:** CloudSim
- **IDE:** Eclipse
- **Operating System:** Windows 11 Pro
- **JDK:** JDK 8 or above

## 🚀 Future Enhancements

- Real-time deployment using physical IoT devices and fog nodes.
- Integration of machine learning and deep learning for parking prediction.
- Mobility-aware fog computing.
- Integration of 5G communication technologies.
- Enhanced security and privacy mechanisms.
- Predictive failure analysis.
- Energy-aware scheduling.
- Blockchain-based secure data management.

## 👩‍💻 Author

**Evangelin Blessy RS**

Master of Computer Applications

Hindustan Institute of Technology and Science, Chennai

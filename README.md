# VisionProject - Android Computer Vision with OpenCV

This project was developed as part of a Practical Activity for Computer Vision. It demonstrates the integration of **CameraX** and **OpenCV** in an Android environment to capture, process, and analyze digital images in real-time.

## 🚀 Features
### Activity 1: Environment & Real-time Capture
*   Integrated CameraX for a high-performance live preview.
*   Implemented a capture system to save frames as JPEG in the internal storage.
*   Configured the environment with OpenCV 4.x (AAR).

### Activity 2: Image Processing Pipeline
*   **Real-time Processing:** A toggleable pipeline that converts frames: `Original -> Grayscale -> Gaussian Blur -> Canny Edges`.
*   **Dynamic Controls:** Real-time adjustment of Canny Hysteresis thresholds via UI sliders.
*   **Pipeline Export:** A "Save All" feature that exports all four stages of the processing pipeline for comparative analysis.

## 🛠 Technical Specifications
| Field | Value |
| :--- | :--- |
| **Android Studio Version** | Ladybug 2024.2.1 (AGP 9.2.0) |
| **JDK Version** | JDK 17 |
| **OpenCV Version** | 4.5.3.0 (.aar) |
| **CameraX Version** | 1.4.1 |
| **Target SDK** | API 36 (Android 15) |
| **Language** | Kotlin / Jetpack Compose |

## 🔬 Experimental Analysis: ISO vs. Edge Detection

### Objective
To observe the impact of sensor sensitivity (ISO) and noise on the Canny Edge Detection algorithm.

### Observations
*   **Low ISO (Good Lighting):** Produced clean, well-defined edges. The Gaussian filter successfully removed minor sensor noise, leaving only structural boundaries.
*   **High ISO (Low Lighting):** Produced a significant amount of **"False Edges"**. Random speckles appeared in flat areas where no physical edges existed.

### Why does this happen? (Relationship with Quantization)
The appearance of false edges at high ISO is caused by the interaction between electronic noise and digital quantization:
1.  **Noise Amplification:** In low light, the camera increases the ISO (gain), which amplifies electronic noise.
2.  **Quantization Steps:** Digital images represent light in discrete levels (0-255). High-gain noise causes pixel values to jump randomly between these levels.
3.  **Artificial Gradients:** The Canny detector identifies edges by calculating intensity gradients. The algorithm cannot distinguish between a physical edge and a "digital edge" caused by noise jumping between quantization levels. These artificial gradients exceed the detection threshold, creating the noisy "false edges" observed in the experiment.

## 📸 Screenshots
*(All screenshots and the video demonstrating the performance of the app are documented in the official report.)*

---

### How to Run
1.  Clone the repository.
2.  Open in Android Studio Ladybug+.
3.  Sync Gradle and run on a physical Android device.
4.  Grant Camera permissions when prompted.

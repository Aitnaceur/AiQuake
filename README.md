# 📱 AI-Quake: Real-Time Earthquake Detection Mobile App

AI-Quake is a lightweight Android app that uses the phone's accelerometer and GPS to detect seismic activity in real-time. When an earthquake is detected based on motion signal patterns, the app alerts the user and sends detection data to a local web dashboard.

---

 🚀 Features

* 📡 Accelerometer-based seismic detection
* 🌺 Bandpass filtering, RMS energy, variance & peak analysis
* 🌍 GPS capture of device location
* ⚠️ Quake alert notification + vibration
* 🌐 POST detection data to backend (local server)
* 📊 Web dashboard ready to visualize map & signal data

---

 🧠 Detection Logic Overview

The app processes accelerometer data in real-time:

1. Collects a 128-sample buffer
2. Applies DC offset removal (gravity)
3. Uses a bandpass filter (1–10Hz)
4. Calculates:

   * Root Mean Square (RMS) energy
   * Variance
   * Spaced peak count
5. If thresholds are met over a minimum time, the quake is confirmed.

---

 📷 Screenshots

Coming soon...

---

 📂 Project Structure

```
ma.fst.aiquakeproject/
├── fragments/
│   └── DetectionFragment.java   # Main earthquake detection logic
├── res/
│   ├── layout/
│   │   └── fragment_detection.xml
│   └── drawable/
│       ├── quake_idle.png
│       ├── quake_verifying.png
│       └── quake_alert.png
```

---

 ⚙️ Requirements

* Android 8.0 (API 26) or higher
* Accelerometer sensor
* GPS enabled

---

 📦 Setup

1. Clone this repo

   ```bash
   git clone https://github.com/Aitnaceur/AiQuake.git
   ```

2. Open in Android Studio

3. Edit `DetectionFragment.java`

   Update the backend URL:

   ```java
   .url("http://<your-local-ip>:3000/api/detections")
   ```

4. Enable Cleartext Traffic

   Add this in `res/xml/network_security_config.xml`:

   ```xml
   <network-security-config>
       <domain-config cleartextTrafficPermitted="true">
           <domain includeSubdomains="true">192.168.x.x</domain>
       </domain-config>
   </network-security-config>
   ```

   Reference it in `AndroidManifest.xml`:

   ```xml
   <application
       android:networkSecurityConfig="@xml/network_security_config"
       ... >
   ```

---

 🛡 Permissions Used

| Permission                     | Purpose                           |
| ------------------------------ | --------------------------------- |
| `ACCESS_FINE_LOCATION`         | To get the user's GPS coordinates |
| `POST_NOTIFICATIONS` (API 33+) | To show quake alert notifications |

Handled at runtime using the modern ActivityResult API.

---

 📡 API Endpoint

POSTs detection data to a local FastAPI/Node backend:

* URL: `http://<LAN-IP>:3000/api/detections`
* Payload Example:

```json
{
  "timestamp": 1747687106457,
  "latitude": 34.0123,
  "longitude": -6.8310,
  "energy": 1.82,
  "variance": 0.67,
  "peakCount": 12,
  "deviceId": "AIQuakePhone1"
}
```

---

 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first.

---

 📄 License

MIT

---

 👨‍💻 Developed By

* Ait Naceur Rahhal
* \EMSI

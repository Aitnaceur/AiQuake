package ma.fst.aiquakeproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import ma.fst.aiquakeproject.utils.CSVLogger;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private LocationManager locationManager;
    private Location currentLocation;
    private TextView accText, gpsText, detectionStatus;
    private Button startBtn, stopBtn;

    private CSVLogger logger;
    private boolean isLogging = false;
    private boolean isDetecting = false;

    private static final int WINDOW_SIZE = 128;
    private float[] signalBuffer = new float[WINDOW_SIZE];
    private int bufferIndex = 0;

    private static final float FILTER_LOW = 1.0f;
    private static final float FILTER_HIGH = 10.0f;
    private static final float SAMPLING_RATE = 50.0f;
    private static final float ENERGY_THRESHOLD = 0.5f;
    private static final float VARIANCE_THRESHOLD = 0.07f;
    private static final int PEAK_THRESHOLD = 7;

    private int detectionStreak = 0;
    private static final int REQUIRED_STREAK = 10;
    private boolean quakeConfirmed = false;
    private long detectionStartTime = 0;
    private static final long MIN_DETECTION_TIME_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accText = findViewById(R.id.accText);
        gpsText = findViewById(R.id.gpsText);
        detectionStatus = findViewById(R.id.detectionStatus);
        startBtn = findViewById(R.id.startButton);
        stopBtn = findViewById(R.id.stopButton);
        Button startDetection = findViewById(R.id.startDetection);
        Button stopDetection = findViewById(R.id.stopDetection);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        requestPermissions();

        startBtn.setOnClickListener(v -> {
            if (!isLogging) {
                logger = new CSVLogger(this);
                isLogging = true;
                Toast.makeText(this, "Logging Started", Toast.LENGTH_SHORT).show();
            }
        });

        stopBtn.setOnClickListener(v -> {
            if (isLogging && logger != null) {
                logger.close();
                isLogging = false;
                Toast.makeText(this, "Logging Stopped", Toast.LENGTH_SHORT).show();
            }
        });

        startDetection.setOnClickListener(v -> {
            isDetecting = true;
            Toast.makeText(this, "Detection Started", Toast.LENGTH_SHORT).show();
        });

        stopDetection.setOnClickListener(v -> {
            isDetecting = false;
            detectionStatus.setText("Status: Monitoring...");
            detectionStatus.setTextColor(Color.GRAY);
            Toast.makeText(this, "Detection Stopped", Toast.LENGTH_SHORT).show();
        });
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE},
                1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(locationListener);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        accText.setText("X: " + x + "\nY: " + y + "\nZ: " + z + "\nMag: " + magnitude);

        double lat = currentLocation != null ? currentLocation.getLatitude() : 0;
        double lon = currentLocation != null ? currentLocation.getLongitude() : 0;
        gpsText.setText("Lat: " + lat + "\nLon: " + lon);

        if (isLogging && logger != null) {
            logger.log(x, y, z, lat, lon);
        }

        signalBuffer[bufferIndex % WINDOW_SIZE] = magnitude;
        bufferIndex++;

        if (isDetecting && bufferIndex >= WINDOW_SIZE) {
            float[] bufferCopy = signalBuffer.clone();
            float mean = 0f;
            for (float v : bufferCopy) mean += v;
            mean /= bufferCopy.length;
            for (int i = 0; i < bufferCopy.length; i++) {
                bufferCopy[i] -= mean;
            }

            float[] filtered = bandpassFilter(bufferCopy, FILTER_LOW, FILTER_HIGH, SAMPLING_RATE);
            float energy = computeRMS(filtered);
            float variance = computeVariance(filtered);
            int peakCount = countSpacedPeaks(filtered, 0.4f, 10);

            Log.d("QUAKE_DEBUG", "Energy = " + energy + ", Variance = " + variance + ", Peaks = " + peakCount);

            boolean currentPatternDetected = (energy > ENERGY_THRESHOLD && variance > VARIANCE_THRESHOLD && peakCount >= PEAK_THRESHOLD);

            if (currentPatternDetected) {
                if (detectionStreak == 0) {
                    detectionStartTime = System.currentTimeMillis();
                }
                detectionStreak++;

                long elapsed = System.currentTimeMillis() - detectionStartTime;

                if (elapsed >= MIN_DETECTION_TIME_MS && detectionStreak >= REQUIRED_STREAK && !quakeConfirmed) {
                    quakeConfirmed = true;
                    detectionStatus.setText("⚠️ Confirmed Quake Detected");
                    detectionStatus.setTextColor(Color.RED);
                } else if (!quakeConfirmed) {
                    detectionStatus.setText("Verifying... (" + (elapsed / 1000) + "s)");
                    detectionStatus.setTextColor(Color.YELLOW);
                }
            } else {
                detectionStreak = 0;
                detectionStartTime = 0;
                quakeConfirmed = false;
                detectionStatus.setText("Monitoring...");
                detectionStatus.setTextColor(Color.GREEN);
            }
        }
    }

    private float[] bandpassFilter(float[] signal, float low, float high, float fs) {
        return lowpass(highpass(signal, low, fs), high, fs);
    }

    private float[] highpass(float[] signal, float cutoff, float fs) {
        float RC = 1.0f / (2 * (float) Math.PI * cutoff);
        float dt = 1.0f / fs;
        float alpha = RC / (RC + dt);
        float[] output = new float[signal.length];
        output[0] = signal[0];
        for (int i = 1; i < signal.length; i++) {
            output[i] = alpha * (output[i - 1] + signal[i] - signal[i - 1]);
        }
        return output;
    }

    private float[] lowpass(float[] signal, float cutoff, float fs) {
        float RC = 1.0f / (2 * (float) Math.PI * cutoff);
        float dt = 1.0f / fs;
        float alpha = dt / (RC + dt);
        float[] output = new float[signal.length];
        output[0] = signal[0];
        for (int i = 1; i < signal.length; i++) {
            output[i] = output[i - 1] + alpha * (signal[i] - output[i - 1]);
        }
        return output;
    }

    private int countSpacedPeaks(float[] signal, float threshold, int minSpacing) {
        int count = 0;
        int lastPeakIndex = -minSpacing;
        for (int i = 1; i < signal.length - 1; i++) {
            if (signal[i] > threshold && signal[i] > signal[i - 1] && signal[i] > signal[i + 1]) {
                if (i - lastPeakIndex >= minSpacing) {
                    count++;
                    lastPeakIndex = i;
                }
            }
        }
        return count;
    }

    private float computeVariance(float[] signal) {
        float mean = 0f;
        for (float v : signal) mean += v;
        mean /= signal.length;

        float variance = 0f;
        for (float v : signal) variance += (v - mean) * (v - mean);
        return variance / signal.length;
    }

    private float computeRMS(float[] signal) {
        float sum = 0f;
        for (float v : signal) sum += v * v;
        return (float) Math.sqrt(sum / signal.length);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            currentLocation = location;
        }
    };
}

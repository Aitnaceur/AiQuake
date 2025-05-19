package ma.fst.aiquakeproject.fragments;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import ma.fst.aiquakeproject.R;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DetectionFragment extends Fragment implements SensorEventListener {

    private ImageView iconStatus;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView statusText;

    private static final int WINDOW_SIZE = 128;
    private final float[] signalBuffer = new float[WINDOW_SIZE];
    private int bufferIndex = 0;

    private static final float FILTER_LOW = 1.0f;
    private static final float FILTER_HIGH = 10.0f;
    private static final float SAMPLING_RATE = 50.0f;

    private static final float ENERGY_THRESHOLD = 0.5f;
    private static final float VARIANCE_THRESHOLD = 0.07f;
    private static final int PEAK_THRESHOLD = 7;
    private static final int REQUIRED_STREAK = 10;
    private static final long MIN_DETECTION_TIME_MS = 3000;

    private int detectionStreak = 0;
    private long detectionStartTime = 0;
    private boolean quakeConfirmed = false;

    private LocationManager locationManager;
    private Location currentLocation;


    private ActivityResultLauncher<String> notificationPermissionLauncher;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        iconStatus = view.findViewById(R.id.iconStatus);
        statusText = view.findViewById(R.id.detection_status);


        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Toast.makeText(getContext(), "Notifications enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Notifications denied", Toast.LENGTH_SHORT).show();
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown != null) {
                currentLocation = lastKnown;
                Log.i("GPS", "Using last known location: " + lastKnown.getLatitude() + ", " + lastKnown.getLongitude());
            }
        }


        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);



    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            currentLocation = location;
        }
    };

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "quake_alerts",
                    "Earthquake Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts for detected earthquakes");
            NotificationManager notificationManager = requireContext().getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        signalBuffer[bufferIndex % WINDOW_SIZE] = magnitude;
        bufferIndex++;

        if (bufferIndex >= WINDOW_SIZE) {
            float[] bufferCopy = signalBuffer.clone();

            // Remove DC offset (gravity)
            float mean = 0f;
            for (float v : bufferCopy) mean += v;
            mean /= bufferCopy.length;
            for (int i = 0; i < bufferCopy.length; i++) bufferCopy[i] -= mean;

            float[] filtered = bandpassFilter(bufferCopy, FILTER_LOW, FILTER_HIGH, SAMPLING_RATE);
            float energy = computeRMS(filtered);
            float variance = computeVariance(filtered);
            int peakCount = countSpacedPeaks(filtered, 0.4f, 10);

            boolean currentPatternDetected = (energy > ENERGY_THRESHOLD && variance > VARIANCE_THRESHOLD && peakCount >= PEAK_THRESHOLD);

            if (currentPatternDetected) {
                if (detectionStreak == 0) {
                    detectionStartTime = System.currentTimeMillis();
                }

                detectionStreak++;
                long elapsed = System.currentTimeMillis() - detectionStartTime;

                if (elapsed >= MIN_DETECTION_TIME_MS && detectionStreak >= REQUIRED_STREAK && !quakeConfirmed) {
                    quakeConfirmed = true;

                    // Trigger system notification
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), "quake_alerts")
                            .setSmallIcon(R.drawable.quake_verifying) // use an existing icon
                            .setContentTitle("QuakeAlert")
                            .setContentText("⚠️ Earthquake detected!")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true);

                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireContext());
                    notificationManager.notify(1, builder.build());

                    // Trigger vibration
                    Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(1000);
                        }
                    }


                    double lat = currentLocation != null ? currentLocation.getLatitude() : 0.0;
                    double lon = currentLocation != null ? currentLocation.getLongitude() : 0.0;

                    // Example: send quake data
                    sendDetectionToServer(
                            "AIQuakePhone1",        // deviceId
                            lat,                    // latitude
                            lon,                    // longitude
                            energy,                 // energy
                            variance,               // variance
                            peakCount               // peak count
                    );


                    statusText.setText("⚠️ Confirmed Quake Detected");
                    statusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                    iconStatus.setImageResource(R.drawable.quake_alert); // swap icon

                    // Simple bounce animation
                    Animation shake = AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_in);
                    iconStatus.startAnimation(shake);
                }
                else if (!quakeConfirmed) {
                    statusText.setText("Verifying... (" + (elapsed / 1000) + "s)");
                    statusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light));
                    iconStatus.setImageResource(R.drawable.quake_verifying); // verifying icon
                }
                } else {
                    detectionStreak = 0;
                    detectionStartTime = 0;
                    quakeConfirmed = false;
                    statusText.setText("Monitoring...");
                    statusText.setTextColor(getResources().getColor(android.R.color.darker_gray));
                    iconStatus.setImageResource(R.drawable.quake_idle); // idle icon
                }
            }
        }

    //Sending data to the web dashboard sectoion
    private void sendDetectionToServer(String deviceId, double lat, double lon, float energy, float variance, int peakCount) {
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        try {
            json.put("timestamp", System.currentTimeMillis());
            json.put("latitude", lat);
            json.put("longitude", lon);
            json.put("energy", energy);
            json.put("variance", variance);
            json.put("peakCount", peakCount);
            json.put("deviceId", deviceId);
            Log.d("SENDLAT", "Sending lat: " + lat + ", lon: " + lon);
        } catch (JSONException e) {
            e.printStackTrace();
            return; // exit the method if construction fails
        }


        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("http://192.168.100.109:3000/api/detections")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SERVER", "Failed to send detection", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("SERVER", "Detection sent successfully: " + response.code());
            }
        });
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

    private float computeRMS(float[] signal) {
        float sum = 0f;
        for (float v : signal) sum += v * v;
        return (float) Math.sqrt(sum / signal.length);
    }

    private float computeVariance(float[] signal) {
        float mean = 0f;
        for (float v : signal) mean += v;
        mean /= signal.length;

        float variance = 0f;
        for (float v : signal) variance += (v - mean) * (v - mean);
        return variance / signal.length;
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        sensorManager.unregisterListener(this);
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
    }
}

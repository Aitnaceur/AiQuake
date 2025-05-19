package ma.fst.aiquakeproject.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;

import ma.fst.aiquakeproject.R;

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
    }
}

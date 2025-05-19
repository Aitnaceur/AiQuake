package ma.fst.aiquakeproject.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ma.fst.aiquakeproject.R;

public class LoggingFragment extends Fragment {

    public LoggingFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logging, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button startButton = view.findViewById(R.id.startButton);
        Button stopButton = view.findViewById(R.id.stopButton);

        startButton.setOnClickListener(v ->
                Toast.makeText(getContext(), "Start Logging", Toast.LENGTH_SHORT).show());

        stopButton.setOnClickListener(v ->
                Toast.makeText(getContext(), "Stop Logging", Toast.LENGTH_SHORT).show());
    }
}

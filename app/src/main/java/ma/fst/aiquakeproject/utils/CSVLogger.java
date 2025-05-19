package ma.fst.aiquakeproject.utils;


import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CSVLogger {
    private FileWriter writer;

    public CSVLogger(Context context) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AIQuakeLogs");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "sensor_data.csv");
            writer = new FileWriter(file, true);
            writer.append("Timestamp,X,Y,Z,Latitude,Longitude\n");
        } catch (IOException e) {
            writer = null;  // <- This makes it explicit
            e.printStackTrace();
        }
    }


    public void log(float x, float y, float z, double lat, double lon) {
        if (writer == null) return;
        try {
            String line = System.currentTimeMillis() + "," + x + "," + y + "," + z + "," + lat + "," + lon + "\n";
            writer.append(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void close() {
        try {
            if (writer != null) writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

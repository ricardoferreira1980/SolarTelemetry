package com.example.ricardo.solartelemetry;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    Camera mCamera;
    AlarmManager alarmManager;
    PendingIntent pIntent;
    SunriseSunsetHelper sunriseSunsetHelper;

    boolean testMode = false;
    int retryCount = 0;
    int maxRetriesOnFailure = 3;
    String dataFilePath = "/sdcard/DCIM/Camera/data.csv";


    void testOCR() {
        String testFilename = "/sdcard/DCIM/Camera/2018-08-18T15-25-52|-- --- |-- ----|-- ----.jpg";
        File file = new File(testFilename);
        int size = (int) file.length();
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        OCR ocr = new OCR(bytes);
        ocr.readValue();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sunriseSunsetHelper = new SunriseSunsetHelper();

        if (testMode) {
          testOCR();
          return;
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                WakeLocker.acquire(context);
                retryCount = 0;
                takePicture();
            }
        };

        // setup alarm
        this.registerReceiver( receiver, new IntentFilter("com.example.ricardo.solartelemetry.wakeup") );
        pIntent = PendingIntent.getBroadcast( this, 0, new Intent("com.example.ricardo.solartelemetry.wakeup"), 0 );
        alarmManager = (AlarmManager)(this.getSystemService( Context.ALARM_SERVICE ));

        // schedule first alarm to take picture (in 5 seconds)
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pIntent);
    }


    private void takePicture() {
        mCamera = getCameraInstance();
        Camera.Parameters params = mCamera.getParameters();
        params.setPictureSize(640, 480);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
        mCamera.setParameters(params);
        mCamera.startPreview();
        mCamera.takePicture(null, null, mPicture);
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            throw e;
        }
        return c; // returns null if camera is unavailable
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            releaseCamera();

            OCR test = new OCR(data);
            int value = test.readValue();

            if (value == -1 && ++retryCount < maxRetriesOnFailure) {
                takePicture();
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(dataFilePath, true);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                String ts = sdf.format(java.util.Calendar.getInstance().getTime());
                bw.write("{ \"ts\": \"" + ts + "\", \"v\": " + value + "}");
                bw.newLine();

                bw.close();
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d("", "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d("", "Error accessing file: " + e.getMessage());
            }

            if (isNetworkAvailable()) {
                // try to send data to REST API
                new SendReadingTask().execute();
            }

            // schedule next picture
            Calendar now = Calendar.getInstance();
            long nextMs = 120000; // 2 minutes by default

            Calendar sunset = sunriseSunsetHelper.getTodaySunset();
            if (now.compareTo(sunset) > 0) {
                // past sunset!
                Calendar sunrise = sunriseSunsetHelper.getTomorrowSunrise();
                nextMs = (sunrise.getTimeInMillis() - System.currentTimeMillis());
            }

            //schedule next alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + nextMs, pIntent);
            WakeLocker.release();
        }
    };


    class SendReadingTask extends AsyncTask<Void, Void, Void> {

        public String convertStreamToJSON(InputStream is) throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            String line;
            boolean isFirst = true;
            while ((line = reader.readLine()) != null) {
                if (!isFirst && line.contains("ts")) {
                    sb.append(",\n");
                }
                sb.append(line);
                isFirst = false;
            }
            sb.append("]");
            reader.close();
            return sb.toString();
        }

        public String getJSONData(String filePath) throws Exception {
            File fl = new File(filePath);
            FileInputStream fin = new FileInputStream(fl);
            String ret = convertStreamToJSON(fin);
            //Make sure you close all streams.
            fin.close();
            return ret;
        }

        @Override
        protected Void doInBackground(Void... nothing) {
            DataOutputStream request = null;
            try {
                // send file contents to the REST API
                URL url = new URL("http://ricardo-ferreira1.outsystemscloud.com/Backoffice/rest/Measures/SendFile");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept","application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                String jsonData = getJSONData(dataFilePath);
                os.writeBytes(jsonData);

                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    // all good, delete the file
                    File file = new File(dataFilePath);
                    file.delete();
                } else {
                    throw new Exception("failed to send");
                }

                request.close();
            } catch (Exception e) {
                // ignore, no connection
            }
            return null;
        }
    }
}

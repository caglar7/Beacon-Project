package com.example.beacon_project;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements BeaconConsumer{
    private BeaconManager beaconManager;
    private Region beaconRegion;
    private Button startButton;
    private Button stopButton;
    private TextView beaconIDText;
    private TextView beaconDistances;
    private Boolean doMonitoring = false;
    ArrayList<Integer> detectedBeacons = new ArrayList<Integer>();
    ArrayList<String> currentIDList = new ArrayList<String>();
    Map<Integer, String> mapIndexDistance = new HashMap<Integer, String>();

    ArrayList<Double> distanceList = new ArrayList<Double>();
    int runningDistances = 8;

    // BEACON LAYOUTS
    private static final String ALTBEACON_LAYOUT = "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";
    private static final String EDDYSTONE_TLM_LAYOUT = "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15";
    private static final String EDDYSTONE_UID_LAYOUT = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";
    private static final String EDDYSTONE_URL_LAYOUT = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v";
    private static final String IBEACON_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24";


    // for calibration currently
    float totalRssi = 0f;
    float currentRssi;
    float rssiIndex = 1;

    // delete after
    int rssiValue = 0;
    int txValue = 0;

    // this call here just before the onCreate method
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, 1234);

        // for beaconManager singleton
        beaconManager = BeaconManager.getInstanceForApplication(this);

        // beacon layouts to scan
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(ALTBEACON_LAYOUT));
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_TLM_LAYOUT));
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_UID_LAYOUT));
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_URL_LAYOUT));
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(IBEACON_LAYOUT));

        // bind beaconManager to this activity
        beaconManager.bind(this);

        // set average distance measurement period
        beaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
        RunningAverageRssiFilter.setSampleExpirationMilliseconds(4000L);

        // get element from xml
        startButton = (Button) findViewById(R.id.button_Start);
        stopButton = (Button) findViewById(R.id.button_Stop);
        beaconIDText = (TextView) findViewById(R.id.textBeaconUUID);
        beaconDistances = (TextView) findViewById(R.id.text_Distances);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMonitoring();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMonitoring();
            }
        });
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    // this service is called on a period looking for beacons around
    // later change this scan period for beacon data, default 1.1ms is not enough
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if(doMonitoring)
                {
                    Log.d("beaconTag", beacons.size() + " beacons");

                    // get the prev list of beacon IDs
                    ArrayList<String> prevIDList = new ArrayList<String>();
                    for(String s: currentIDList)
                    {
                        prevIDList.add(s);
                    }

                    // clear prev lists and distance text
                    detectedBeacons.clear();
                    currentIDList.clear();
                    beaconDistances.setText("");

                    int beaconIndex = 1;
                    for(Beacon b: beacons)
                    {
                        // delete after, just some checking
                        rssiValue = b.getRssi();
                        txValue = b.getTxPower();

                        b.setHardwareEqualityEnforced(true);
                        // get IDs and put them on a list
                        String tempStringID = b.getId1().toString();
                        currentIDList.add("Beacon " + beaconIndex + ": " + tempStringID);

                        // get distances and print them out, for 1 beacon now
                        double disCalculated = measureDistance(txValue, rssiValue);
                        String bDistance = String.format("%.2f", disCalculated);

                        // get the beacon index from its bluetooth name
                        String blueName = b.getBluetoothName();
                        char lastchar = blueName.charAt(blueName.length()-1);
                        int beaconint = lastchar - '0';
                        String disString = bDistance + " meters";
                        mapIndexDistance.put(beaconint, disString);
                        detectedBeacons.add(beaconint);

                        //String disString = beaconint + ": " + bDistance +" meters";
                        //beaconDistances.setText(beaconDistances.getText() + disString + "\n");
                        //beaconDistances.setText(beaconDistances.getText() + "current rssi: " + b.getRssi());

                        // for calibration
                        //currentRssi = b.getRssi();
                        //totalRssi += currentRssi;
                        //float averageRssi = totalRssi / rssiIndex;
                        //beaconDistances.setText("total RSSI  : " + totalRssi + "\n");
                        //beaconDistances.setText(beaconDistances.getText() + "index       : " + rssiIndex + "\n");
                        //beaconDistances.setText(beaconDistances.getText() + "average RSSI: " + averageRssi + " dbm");
                        //rssiIndex+=1;
                        // for calibration

                        beaconIndex+=1;
                    }
                    // check if there is any update on beacon ID list
                    if(!currentIDList.equals(prevIDList))
                    {
                        // there is a change in beacon ID list, updating textView
                        beaconIDText.setText("");
                        for(String idString: currentIDList)
                        {
                            beaconIDText.setText(beaconIDText.getText() + idString + "\n");
                        }
                    }

                    // print the beacons and distances, hashmap format
                    for(int i=1; i<=beacons.size(); i++)
                    {
                        if(detectedBeacons.contains(i))
                        {
                            String m = mapIndexDistance.get(i);
                            beaconDistances.setText(beaconDistances.getText() + "Beacon " + i + ": " + m + "\n");
                            beaconDistances.setText(beaconDistances.getText() + "rssi: " + rssiValue + "\n");
                            beaconDistances.setText(beaconDistances.getText() + "tx  : " + txValue + "\n");
                        }
                        else
                        {
                            String s = "No Signal";
                            beaconDistances.setText(beaconDistances.getText() + "Beacon " + i + ": " + s + "\n");
                        }
                    }
                }
                else
                {
                    currentIDList.clear();
                    beaconIDText.setText("");
                    beaconDistances.setText("");
                }
            }
        });
    }

    private void startMonitoring()
    {
        doMonitoring = true;

        // for calibration
        totalRssi = 0f;
        rssiIndex = 1f;

        Log.d("beaconTag", "monitoring started");
        try{
            beaconManager.startRangingBeaconsInRegion(new Region("myRegion", null, null, null));
        }
        catch(RemoteException e) {Log.d("beaconTag", "error on start monitoring"); }
    }

    private void stopMonitoring()
    {
        doMonitoring = false;

        Log.d("beaconTag", "monitoring stopped");
        try{
            beaconManager.stopRangingBeaconsInRegion(new Region("myRegion", null, null, null));
        }
        catch(RemoteException e) {Log.d("beaconTag", "error on stop monitoring"); }
    }

    private double measureDistance(double tx, double rssi)
    {
        double distance;
        if(rssi == 0d)
            distance = 0;       // that might change if it causes any problems

        double ratio = rssi / tx;
        if(ratio < 1.0)
            distance = Math.pow(ratio, 10);
        else
        {
            distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
        }

        // apply running average
        if(distanceList.size() == runningDistances)
        {
            distanceList.remove(0);
            distanceList.add(distance);
        }
        else
        {
            distanceList.add(distance);
        }
        // calculate running average
        //double sum = 0;
        //double divider = (double) distanceList.size();
        //for(double d: distanceList)
        //    sum+=d;

        // weighted average test
        double sum = 0;
        double divider = 0;
        int index = 1;
        for(double d: distanceList)
        {
            divider+=index;
            sum += (d*index);
            index+=1;
        }
        distance = sum/divider;

        return distance;
    }
}


/*
FOR SCAN PERIODS
        beaconManager.setForegroundScanPeriod(5000l);
        beaconManager.setBackgroundScanPeriod(5000l);
        beaconManager.setForegroundBetweenScanPeriod(1100l);
        beaconManager.setBackgroundBetweenScanPeriod(1100l);
 */
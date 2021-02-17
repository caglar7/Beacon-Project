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
import org.altbeacon.beacon.service.RunningAverageRssiFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements BeaconConsumer{
    private static final String TAG = "MainActivity";

    private BeaconManager beaconManager;
    private Region beaconRegion;
    private Button startButton;
    private Button stopButton;
    private TextView beaconIDText;
    private TextView beaconDistancesText;
    private Boolean doMonitoring = false;
    ArrayList<Beacon> currentBeaconList = new ArrayList<Beacon>();
    ArrayList<Beacon> allDetectedBeaconsList = new ArrayList<Beacon>();


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

    // THIS VARIABLE BLOCK IS FOR MOVEMENT DRAWING UPDATE
    // FOR DISTANCE CALCULATION, values from power regression prediction
    private double coefficientA = 0.89d;
    private double coefficientB = 7.62d;
    private double coefficientC = 0.24d;
    // for running average code
    private int measureAmount = 10;             // increments by 10 for now
    private ArrayList<Double> distanceList = new ArrayList<Double>();


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

        // set scan period for beaconServiceConnect
        try {
            beaconManager.setForegroundScanPeriod(500l);
            beaconManager.setForegroundBetweenScanPeriod(0l);
        }catch(Exception e)
        {
            e.printStackTrace();
        }

        // bind beaconManager to this activity
        beaconManager.bind(this);

        // set average distance measurement period
        beaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
        RunningAverageRssiFilter.setSampleExpirationMilliseconds(5000L);

        // get element from xml
        startButton = (Button) findViewById(R.id.button_Start);
        stopButton = (Button) findViewById(R.id.button_Stop);
        beaconIDText = (TextView) findViewById(R.id.textBeaconUUID);
        beaconDistancesText = (TextView) findViewById(R.id.text_Distances);
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
        try {
            beaconManager.updateScanPeriods();
        }catch(Exception e)
        {
            e.printStackTrace();
        }

        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if(doMonitoring)
                {

                    int beaconIndex = 1;
                    for(Beacon b: beacons)
                    {
                        b.setHardwareEqualityEnforced(true);

                        double txValue = b.getTxPower();
                        double rssiValue = b.getRssi();
                        double distanceValue = getDistanceForDevice(txValue, rssiValue);
                        String distanceString = String.format("%.1f", distanceValue);

                        // print beacon ID and distanceValue
                        int count = 1;
                        beaconIDText.setText("");
                        for(double d: distanceList)
                        {
                            String dString = String.format("%.1f", d);
                            beaconIDText.setText(beaconIDText.getText() + "value "+count + ": " + dString + "\n");
                            count++;
                        }
                        beaconDistancesText.setText("Avg Distance: " + distanceString);

                        // for calibratio
                        //currentRssi = b.getRssi();
                        //totalRssi += currentRssi;
                        //float averageRssi = totalRssi / rssiIndex;
                        //beaconDistancesText.setText("total RSSI  : " + totalRssi + "\n");
                        //beaconDistancesText.setText(beaconDistancesText.getText() + "index       : " + rssiIndex + "\n");
                        //beaconDistancesText.setText(beaconDistancesText.getText() + "average RSSI: " + averageRssi + " dbm");
                        //rssiIndex+=1;
                        // for calibration

                        beaconIndex+=1;
                    }

                }
                else
                {
                    // clear id and distance text
                    beaconIDText.setText("");
                    beaconDistancesText.setText("");
                    allDetectedBeaconsList.clear();
                    currentBeaconList.clear();
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

    private void AddDetectedBeacon(Beacon dBeacon)
    {
        if(!allDetectedBeaconsList.contains(dBeacon))
        {
            allDetectedBeaconsList.add(dBeacon);
        }
    }

    private int getBeaconIndex(Beacon b)
    {
        return allDetectedBeaconsList.indexOf(b);
    }

    private boolean equalCheck(ArrayList prev, ArrayList current)
    {
        if(prev.size() != current.size())
            return false;

        int contains = 0;
        for(int i=0; i<current.size(); i++)
        {
            if(prev.contains(current.get(i)))
            {
                contains+=1;
            }
        }

        if(contains == current.size())
            return true;
        else
            return false;
    }

    private double getDistanceForDevice(double tx, double rssi)
    {
        // not first out last in, just weird values are out
        double ratio = rssi/tx;
        double dis = coefficientA*(Math.pow(ratio, coefficientB)) + coefficientC;

        // add distance to the list
        distanceList.add(dis);

        // running average code with 10% top and bottom are out
        int throwAmount = measureAmount/10;
        if(distanceList.size() >= measureAmount)
        {
            for(int i=0; i<throwAmount; i++)
            {
                // initialize and assign first element
                double topValue, bottomValue;
                int topIndex = 0, bottomIndex = 0;
                topValue = bottomValue = distanceList.get(0);

                // find top value and remove it
                for(double d: distanceList)
                {
                    if(d >= topValue)
                        topValue = d;
                }
                topIndex = distanceList.indexOf(topValue);
                distanceList.remove(topIndex);

                // find bottom value and remove it
                for(double d: distanceList)
                {
                    if(d<=bottomValue)
                        bottomValue = d;
                }
                bottomIndex = distanceList.indexOf(bottomValue);
                distanceList.remove(bottomIndex);
            }
        }

        double total = 0;
        double listSize = distanceList.size();
        for(double d: distanceList)
        {
            total+=d;
        }
        double runAverage = total/listSize;

        return runAverage;
    }
}



//private double secondRunningDistance(Beacon b, int index)
//{
//    int size = allBeaconDistances.get(index).size();
//    if(size == secRunningAvg)
//    {
//        // remove first and add another double
//        allBeaconDistances.get(index).remove(0);
//        allBeaconDistances.get(index).add(b.getDistance());
//    }
//    else if(size < secRunningAvg)
//    {
//        allBeaconDistances.get(index).add(b.getDistance());
//    }
//
//    double sum = 0d;
//    double divider = 0d;
//    for(double d: allBeaconDistances.get(index))
//    {
//        sum+=d;
//        divider+=1;
//    }
//
//    return sum/divider;
//}
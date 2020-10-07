package com.example.beacon_project;

import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;

public class MainActivity extends AppCompatActivity implements BeaconConsumer{
    private BeaconManager beaconManager;
    private Region beaconRegion;
    private Button startButton;
    private Button stopButton;

    // BEACON LAYOUTS
    private static final String ALTBEACON_LAYOUT = "m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25";
    private static final String EDDYSTONE_TLM_LAYOUT = "x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15";
    private static final String EDDYSTONE_UID_LAYOUT = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";
    private static final String EDDYSTONE_URL_LAYOUT = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20v";
    private static final String IBEACON_LAYOUT = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24";


    // this call here just before the onCreate method
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, 1234);

        // for beaconManager singleton
        beaconManager = BeaconManager.getInstanceForApplication(this);

        // look for altbeacons for now
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(IBEACON_LAYOUT));

        // bind beaconManager to this activity
        beaconManager.bind(this);

        // get the buttons and onClick them
        startButton = (Button) findViewById(R.id.button_Start);
        stopButton = (Button) findViewById(R.id.button_Stop);
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


    @Override
    public void onBeaconServiceConnect() {
        Log.d("beaconTag", "we are in onBeaconService");
        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for(Beacon b: beacons)
                {
                    Log.d("beaconTag", "beacon id: " + b.getId1().toString());
                }
            }
        });
    }

    private void startMonitoring()
    {
        Log.d("beaconTag", "monitoring started");
        try{
            beaconManager.startRangingBeaconsInRegion(new Region("myRegion", null, null, null));
        }
        catch(RemoteException e) {Log.d("beaconTag", "some error on start monitoring"); }
    }

    private void stopMonitoring()
    {
        Log.d("beaconTag", "monitoring stopped");
        try{
            beaconManager.stopRangingBeaconsInRegion(new Region("myRegion", null, null, null));
        }
        catch(RemoteException e) {Log.d("beaconTag", "some error on start monitoring"); }
    }
}

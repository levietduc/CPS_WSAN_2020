package no.nordicsemi.android.nrfthingy.ClusterHead;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClhScan {
    private BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothLeScanner mCLHscanner ;
    private final String LOG_TAG="CLH Scanner:";
    public static final int MIN_SCAN_RSSI_THRESHOLD=-80;    //min RSSI of receive packet from other clusterheads
    private static final long SCAN_PERIOD = 60000*10;   //scan 10 minutes
    private static final long REST_PERIOD=1000; //rest in 1 sec
    private Handler handler = new Handler();
    private Handler handler2 = new Handler();
    private boolean mScanning;
    private byte mClhID=01;
    private boolean mIsSink=false;
    private ScanSettings mScanSettings;

    private static final int SCAN_HISTORY_LIST_SIZE=512;
    private SparseArray<Integer> ClhScanHistoryArray=new SparseArray<>();

    //private static final int MAX_PROCESS_LIST_ITEM=128;
    //private ClhAdvertisedData clhAdvData=new ClhAdvertisedData();
    private ClhAdvertise mClhAdvertiser;
    private ArrayList<ClhAdvertisedData> mClhProcDataList ;
    private ClhProcessData mClhProcessData;
    private ArrayList<ClhAdvertisedData> mClhAdvDataList;
    private static final int MAX_ADVERTISE_LIST_ITEM=128;

    public ClhScan()
    {

    }

    public ClhScan(ClhAdvertise clhAdvObj,ClhProcessData clhProcDataObj)
    {
        mClhAdvertiser=clhAdvObj;
        mClhAdvDataList=mClhAdvertiser.getAdvertiseList();
        mClhProcessData=clhProcDataObj;
        mClhProcDataList=clhProcDataObj.getProcessDataList();
    }

    public void setAdvDataObject(ClhAdvertise clhAdvObj){
        mClhAdvertiser=clhAdvObj;
        mClhAdvDataList=mClhAdvertiser.getAdvertiseList();

    }
    public void setProcDataObject(ClhProcessData clhProObj){
        mClhProcessData=clhProObj;
        mClhProcDataList=mClhProcessData.getProcessDataList();
    }

    public int BLE_scan() {
        boolean result=true;
        byte[] advsettings=new byte[16];
        byte[] advData= new byte[256];
        int length;
        final List<ScanFilter> filters = new ArrayList<>();

        if (!mScanning) {
            //verify BLE available
            mCLHscanner = mAdapter.getBluetoothLeScanner();
            if (mCLHscanner == null) {
                Log.i(LOG_TAG, "BLE not supported");
                return ClhErrors.ERROR_CLH_BLE_NOT_ENABLE;
            }

            //setting
            ScanSettings ClhScanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .build();

            //set filter: filter name
            ScanFilter filter = new ScanFilter.Builder()
                    .setDeviceName(ClhAdvertise.cluster_head_name)
                    .build();
            filters.add(filter);
            Log.i(LOG_TAG, "filters"+ filters.toString());

            mScanSettings =ClhScanSettings;
// Stops scanning after 60 seconds.

            // Create a timer to stop scanning after a pre-defined scan period.
            //rest, then restart to avoid auto disable from Android
           handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mCLHscanner.stopScan(CLHScanCallback);
                    Log.i(LOG_TAG, "Stop scan");
                    //start another timer for resting in 1s
                    handler2.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mScanning = true;
                            mCLHscanner.startScan(filters, mScanSettings, CLHScanCallback);
                        }
                    },REST_PERIOD);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mCLHscanner.startScan(filters, ClhScanSettings, CLHScanCallback);
            Log.i(LOG_TAG, "Start scan");
        }
        else
        {
            return ClhErrors.ERROR_CLH_SCAN_ALREADY_START;
        }

        return ClhErrors.ERROR_CLH_NO;
    }

    public void stopScanCLH()
    {
        mScanning = false;
        mCLHscanner.stopScan(CLHScanCallback);
        Log.i(LOG_TAG, "Stop scan");
    }


    private ScanCallback CLHScanCallback = new ScanCallback() {
        @Override
        public final void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            //no need this code since already have name filter
            /*if( result == null
                    || result.getDevice() == null
                    || TextUtils.isEmpty(result.getDevice().getName()) ) {
                Log.i(LOG_TAG, "Empty name space");
                return;
                //if( result == null || result.getDevice() == null)  return;
            }*/

            if (result.getRssi()<MIN_SCAN_RSSI_THRESHOLD) {
                Log.i(LOG_TAG,"low RSSI");
                return;
            }
            SparseArray<byte[]> manufacturerData = result.getScanRecord().getManufacturerSpecificData();
            processScanData(manufacturerData);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e( "BLE", "Discovery onScanFailed: " + errorCode );
            super.onScanFailed(errorCode);
        }
    };





    /*public void setReturnAdvertiseArr(ArrayList<ClhAdvertisedData> arr)
    {
        mClhAdvDataList=arr;
    }

    public void setReturnProcessArr(ArrayList<ClhAdvertisedData> arr)
    {
        mClhProcDataList=arr;
    }*/

    /*private ClhAdvertise mAdvertiseObj;

    public  void setAdvertiseObject(ClhAdvertise obj)
    {
        mAdvertiseObj=obj;
    }*/

    public void processScanData(SparseArray<byte[]> manufacturerData) {

        /*received data of BLE Manufaturer field in "manufacturerData" include:
        - Manu Spec (in manufacturerData.key): "unique packet ID", include
                    2 bytes: 0XAABB: AA: Source Cluster Head ID: 0-127
                                    BB: Packet ID: 0-254
         - Manu Data (in manufacturerData.value): remained n data bytes (manufacturerData.size())
        -------------------*/
        if(manufacturerData==null)
        {
            Log.i(LOG_TAG, "no Data");
            return;

        }
        int receiverID=manufacturerData.keyAt(0);

        //reflected data (received cluster head ID = device Clh ID skip
        if(mClhID==(receiverID>>8))
        {
            Log.i(LOG_TAG,"reflected data, mClhID "+mClhID +", recv:" +(receiverID>>8) );
            return;
        }
        Log.i(LOG_TAG,"ID data "+ (receiverID>>8)+ "  "+(receiverID&0xFF) );

        /* check packet has been yet recieved by searching the "unique packet ID" history list
         - history list include:
                        Key: unique packet ID
                        life counter: time of the packet lived in history list
          --------------*/

        if (ClhScanHistoryArray.indexOfKey(manufacturerData.keyAt(0))<0)
        {//not yet received
            //history not yet full, update new "unique packet ID" to history list, reset life counter
            if(ClhScanHistoryArray.size()<SCAN_HISTORY_LIST_SIZE)
            {
                ClhScanHistoryArray.append(manufacturerData.keyAt(0),0);
            }
            ClhAdvertisedData clhAdvData = new ClhAdvertisedData();

            //add receive data to Advertise list or Process List
            //Log.i(LOG_TAG," add history"+ (receiverID>>8)+ "  "+(receiverID&0xFF) );
            //Log.i(LOG_TAG," manufacturer value"+ Arrays.toString(manufacturerData.valueAt(0)) );

            clhAdvData.parcelAdvData(manufacturerData,0);
            if(mIsSink)
            {//if this Cluster Head is the Sink node, add data to waiting process list
                //process data
                    mClhProcessData.addProcessPacketToBuffer(clhAdvData);
                    //mClhProcDataList.add(clhAdvData);
                    Log.i(LOG_TAG, "Add data to process list, len:" + mClhProcDataList.size());
            }
            else {// add data to advertising list to forward
                //forward data

               //vinh
                if(mClhAdvertiser==null)
                {
                    Log.i(LOG_TAG,"null adv");

                }
                else {


                    mClhAdvertiser.addAdvPacketToBuffer(clhAdvData,false);
                    Log.i(LOG_TAG, "Add data to advertised list, len:" + mClhAdvDataList.size());
                    Log.i(LOG_TAG, "Advertise list at " + (mClhAdvDataList.size() - 1) + ":"
                            + Arrays.toString(mClhAdvDataList.get(mClhAdvDataList.size() - 1).getParcelClhData()));
                }
            }

        }
    }

    public void setClhID(byte clhID, boolean isSink){
        mClhID=clhID;
        mIsSink=isSink;
    }
  /*  public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }*/

}



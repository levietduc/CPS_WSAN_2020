package no.nordicsemi.android.nrfthingy.ClusterHead;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.res.Resources;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import no.nordicsemi.android.nrfthingy.R;



public class ClhAdvertise {

    private final String LOG_TAG = "CLH Advertising"; //Tag for debug logging via USB

    public final static String cluster_head_name="CH";


    public final static int ADV_SETTING_BYTE_MODE = 0;
    public final static int ADV_SETTING_BYTE_SENDNAME = 1;
    public final static int ADV_SETTING_BYTE_SENDTXPOWER = 2;


    public final static int ADV_SETTING_MODE_LOWPOWER = 0;
    public final static int ADV_SETTING_MODE_BALANCE = 1;
    public final static int ADV_SETTING_MODE_LOWLATENCY = 2;

    public final static int ADV_SETTING_SENDNAME_NO = 0;
    public final static int ADV_SETTING_SENDNAME_YES = 1;

    public final static int ADV_SETTING_SENDTXPOWER_NO = 0;
    public final static int ADV_SETTING_SENDTXPOWER_YES = 1;



    private static final long ADVERTISE_PERIOD= 60000;


    private static String mUUId = null;
    private static byte[] mClusterHeadID= {0x12,0x34};
    private static int mUniqueManuID=1510;
    private static ParcelUuid mPUUID;
    private static boolean mIsAdvertsing=false;
    private static BluetoothLeAdvertiser mAdvertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

    private final static int BLE_CLH_ADVERTISING_STATUS_DISABLE=256;
    private final static int BLE_CLH_ADVERTISING_STATUS_STOP=0;
    private final static int BLE_CLH_ADVERTISING_STATUS_START=1;
    private final static int BLE_CLH_ADVERTISING_STATUS_NO_DATA=2;
    private final static int BLE_CLH_ADVERTISING_STATUS_STOP_WAIT=100;

    private static int mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_DISABLE;

    private CountDownTimer mAdvertisingTimer;
    private long mAdvInterval=0;
    private final static long MAX_ADVERTISING_DURATION=10*60*1000;  //max 10 minutes for an advertising slot
    private final static long MAX_ADVERTISING_INTERVAL=10000;  //max ten seconds for an advertising packet interval
    private byte[] mAdvsettings=new byte[16];
    private byte mClhID=1;
    private boolean mIsSink=false;

    /* control:
            ADV_CLH_CONTROL_STOP: stop advertising
            ADV_CLH_CONTROL_INIT: start and create a new UUID (initial)
            ADV_CLH_CONTROL_START: start advertising
            ADV_CLH_CONTROL_UPDATE: update data
       setting[]:
            [0]: ADV_SETTING_BYTE_ENERGY,Energy mode:
                    LOW_POWER_MODE ADV_SETTING_ENERGY_LOWPOWER (default)
                    BALANCE_MODE ADV_SETTING_ENERGY_BALANCE
                    HIGH_LATENCY_MODE ADV_SETTING_ENERGY_LOWLATENCY
            [1]: ADV_SETTING_BYTE_SENDNAME,send name:
                0: ADV_SETTING_SENDNAME_NO
                1: ADV_SETTING_SENDNAME_YES (default)
            [2]: ADV_SETTING_BYTE_SENDTXPOWER,send TxPower
                0: ADV_SETTING_SENDTXPOWER_NO (default)
                1: ADV_SETTING_SENDTXPOWER_YES
            [3]: ADV_SETTING_BYTE_SENDMANUFACTURER, send type
                 0:  ADV_SETTING_SENDMANUFACTURER_NO: send UUID
                1: ADV_SETTING_SENDMANUFACTURER_YES: send manufacturer type;

       data[]:
       [0]: data length
       [1..n]: data
     */
    private final static int MAX_ADVERTISE_LIST_ITEM=64;
    private int mMaxAdvAllowable=MAX_ADVERTISE_LIST_ITEM;

    private ArrayList<ClhAdvertisedData >mClhAdvDataList;

    public ClhAdvertise(){
        mClhAdvDataList= new ArrayList<ClhAdvertisedData>(MAX_ADVERTISE_LIST_ITEM);
    }
    public ClhAdvertise(ArrayList<ClhAdvertisedData> clhAdvDataList, int maxAdvAllowable){
        mMaxAdvAllowable=maxAdvAllowable;
        mClhAdvDataList=clhAdvDataList;
        //vinh
        Log.i(LOG_TAG,"size array list"+mClhAdvDataList.size() );
    }

    public void setAdvClhID(byte clhID, boolean isSink){
        mClhID=clhID;
        mIsSink=isSink;
    }

    public void setAdvSettings(byte[] settings)
    {
        Log.i(LOG_TAG, "Start Setting Advertizing params func");

        if(settings==null)
        {   // default advertising: low power, name and UUID , no TX power
            mAdvsettings[ADV_SETTING_BYTE_MODE] = ADV_SETTING_MODE_LOWPOWER;
            mAdvsettings[ADV_SETTING_BYTE_SENDNAME] = ADV_SETTING_SENDNAME_YES;
            mAdvsettings[ADV_SETTING_BYTE_SENDTXPOWER] = ADV_SETTING_SENDTXPOWER_NO;

        }
        else{
            int len;
            if(mAdvsettings.length<settings.length)
                len=mAdvsettings.length;
            else
                len=settings.length;
            mAdvsettings=Arrays.copyOfRange(settings,0,len);
        }
        Log.i(LOG_TAG, "End Setting Advertizing params func");
    }

    public void setAdvInterval(long interval)
    {
        if (interval<=0)
        {
            mAdvInterval=MAX_ADVERTISING_INTERVAL;
        }
        else
        {
            mAdvInterval=interval;
        }
    }


    public byte[] getAdvSettings()
    {
        return mAdvsettings;
    }

    public int initCLHAdvertiser()
    {
        int error;
        byte[] advsettings=new byte[16];
        Log.i(LOG_TAG, "Start Intialize func");
        if ((error=checkBLEAdvertiser())!=ClhErrors.ERROR_CLH_NO)
            return error;


        if (mUUId == null) {
            mUUId = UUID.randomUUID().toString().toUpperCase();
            mPUUID = new ParcelUuid(UUID.fromString(mUUId));

            //set name =PS + UUID octa [2,3]
            //String str2 = "PS "+ uniqueId.substring(4,8);
            String str1=cluster_head_name;
            if (BluetoothAdapter.getDefaultAdapter().setName(str1) == false) {
                Log.i(LOG_TAG, "Advertiser: set name fail" );
                return ClhErrors.ERROR_CLH_BLE_SETNAME_FAIL;
            }
            Log.i(LOG_TAG, "Name:" +BluetoothAdapter.getDefaultAdapter().getName());

            //set 2 bytes of unique ID to include to Manufaturer ID
            mUniqueManuID = Integer.parseInt(mUUId.substring(4,8), 16);
            Log.i(LOG_TAG, "UUID " +mUUId);
            Log.i(LOG_TAG, "Advertiser name "+str1 );
            Log.i(LOG_TAG, "Advertiser: ID int "+ mUniqueManuID);
        }
        //start default advertising: low power, name and UUID , no TX power
        advsettings[ADV_SETTING_BYTE_MODE] = ADV_SETTING_MODE_LOWPOWER;
        advsettings[ADV_SETTING_BYTE_SENDNAME] = ADV_SETTING_SENDNAME_YES;
        advsettings[ADV_SETTING_BYTE_SENDTXPOWER] = ADV_SETTING_SENDTXPOWER_NO;

        if(mBleClhAdvertisingStatus==BLE_CLH_ADVERTISING_STATUS_START){
            //on advertising -> stop advertiser
            stopCLHdata();
            /*mAdvertiser.stopAdvertising(advertisingCallback);
            if(mAdvertisingTimer!=null){
                mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP_WAIT;
                mAdvertisingTimer.cancel();
            }
            else{
                mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
            }*/

        }


        //set up timer for each packet advertising, and
        mAdvertisingTimer=new CountDownTimer(mAdvInterval,100) {
            @Override
            public void onTick(long millisUntilFinished) {//on timer experire event callback
            }

            @Override //not used
            public void onFinish() {
                if((mBleClhAdvertisingStatus==BLE_CLH_ADVERTISING_STATUS_STOP_WAIT)||
                        (mBleClhAdvertisingStatus==BLE_CLH_ADVERTISING_STATUS_STOP))
                {
                    mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
                }
                else
                {
                    mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
                    mAdvertiser.stopAdvertising(advertisingCallback);
                    nextAdvertisingPacket(); //advertise next packet
                }

                //stop advertising for 1sec if still running, then restart
                       /* advertiser.stopAdvertising(advertisingCallback);
                        if (bleClhAdvertisingStatus == BLE_CLH_ADVERTISING_STATUS_START) {
                            Handler handler = new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    advertiser.startAdvertisingSet(advertisingCallback);
                                    Log.i(LOG_TAG, "Stop scan");
                                }
                            }, 1000);
                        }*/
            }
        };
        Log.i(LOG_TAG,"End Initializing func");

        //start
        //byte[] data={3};
        //startAdvertiser(advsettings,data);
        return ClhErrors.ERROR_CLH_NO;
    }

    public int updateCLHdata(byte data[])
    {
        int error;
        byte[] advData= new byte[256];
        int length;

        Log.i(LOG_TAG, "Update Advertised Data func");
        if ((error=checkBLEAdvertiser())!=ClhErrors.ERROR_CLH_NO)
            return error;

        if (data==null)
        {
            advData[0]=1;
        }
        else
        {
            if(advData.length>=data.length){
                length=data.length;
            }
            else{
                length=advData.length;
            }
            advData= Arrays.copyOfRange(data,0, length);
        }

        if(mAdvsettings==null)
        {
            return ClhErrors.ERROR_CLH_ADV_NOT_YET_SETTING;
        }

        Log.i(LOG_TAG, "Advertiser: update data");
        stopCLHdata();
        error=startAdvertiser(mAdvsettings,advData);

        Log.i(LOG_TAG, "End update data func");
        return error;

    }

    public void stopCLHdata()
    {

        Log.i(LOG_TAG, "Stop CLH Advertiser func");
        mAdvertiser.stopAdvertising(advertisingCallback);
        if(mAdvertisingTimer!=null){
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP_WAIT;
            mAdvertisingTimer.cancel();//stop timer
        }
        else{
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
        }
        Log.i(LOG_TAG, "End Stop CLH Advertizer func");
    }


    private int checkBLEAdvertiser()
    {
        //verify BLE available
        if (!BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()) {
            Log.i(LOG_TAG, "Multiple advertisement not supported");
            return ClhErrors.ERROR_CLH_ADV_MULTI_ADVERTISER;
        }
        if ((mAdvertiser=BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser()) == null) {
            Log.i(LOG_TAG, "BLE not supported");
            return ClhErrors.ERROR_CLH_BLE_NOT_ENABLE;
        }
        return ClhErrors.ERROR_CLH_NO;
    }



    private int startAdvertiser(byte[] settings, byte[] data) {
        //setting and start advertiser
        //@param: settings: configuration
        //data: input data [0]: length
        //  if lenght =0: send UUID only

        Log.i(LOG_TAG,"Start Start Advertizer func");

        AdvertiseSettings.Builder advSettingsBuilder = new AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false);
        //set operation mode: low energy to latency
        switch (settings[ADV_SETTING_BYTE_MODE]) {
            case ADV_SETTING_MODE_LOWLATENCY:
                advSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
                break;
            case ADV_SETTING_MODE_BALANCE:
                advSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
                break;
            default:
                advSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
                break;
        }
        AdvertiseSettings advSettings = advSettingsBuilder.build();

        //enable/disable send device name and txpower
        int advDatalen=3; //count the length of advertise data
        AdvertiseData.Builder advDataBuilder = new AdvertiseData.Builder();
        if (settings[ADV_SETTING_BYTE_SENDNAME] == ADV_SETTING_SENDNAME_NO){
            advDataBuilder.setIncludeDeviceName(false);
        }
        else{
            advDataBuilder.setIncludeDeviceName(true);
            advDatalen=BluetoothAdapter.getDefaultAdapter().getName().length()+2;
        }

        if (settings[ADV_SETTING_BYTE_SENDTXPOWER] == ADV_SETTING_SENDTXPOWER_YES) {
            advDataBuilder.setIncludeTxPowerLevel(true);
            advDatalen+=3;
        } else {
            advDataBuilder.setIncludeTxPowerLevel(false);
        }

        if(data.length<3)
        {
            Log.i(LOG_TAG, "send UUID only: "+mPUUID);
            advDataBuilder.addServiceUuid(mPUUID);
        }
        else
        {
            Log.i(LOG_TAG, "current length: "+ advDatalen);
            advDatalen=data.length+advDatalen+3 + 2 ; // include: 3(default) + name (vary:option) + txpower (3:option)
                                            // + 2(setting for manufacturer) + data
            if(advDatalen>31)
            {//if data length too long, send UUID
                Log.i(LOG_TAG, "Too long advertise data:" + advDatalen);
                return ClhErrors.ERROR_CLH_ADV_TOO_LONG_DATA;
            }
            else
            {
                int len=data.length;
                int manuSpec=(((int)data[0]<<8)&0x7F00)+ ((int)(data[1])&0x00FF);
                byte[] advData= Arrays.copyOfRange(data,2, len);
                advDataBuilder.addManufacturerData(manuSpec,advData);
                Log.i(LOG_TAG, "send manufature data, total length:" +advDatalen);
                Log.i(LOG_TAG, "send data length:" +len);
                Log.i(LOG_TAG, "Manu Spec: 0x" + data[0] + ","+data[1]);
                Log.i(LOG_TAG, "Manu Data: "+ Arrays.toString(advData));
                //advDataBuilder.addManufacturerData(mUniqueManuID,advData);

                /*String str1=mUUId.substring(4,8);
                Log.i(LOG_TAG, "UUID(16 bits) :" + str1);
                Log.i(LOG_TAG, "send manufature data, total length:" +advDatalen);
                String s = new String(advData, StandardCharsets.UTF_8);
                Log.i(LOG_TAG, "send manufature data, data:" + Arrays.toString(advData));*/
            }
        }
        AdvertiseData sendData = advDataBuilder.build();
        mAdvertiser.startAdvertising(advSettings, sendData, null, advertisingCallback);


        Log.i(LOG_TAG,"End Start advertizer func");

        return ClhErrors.ERROR_CLH_NO;
    }

    private AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.i(LOG_TAG, "Start Advertising Success "+ settingsInEffect.describeContents());
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_START;
            mAdvertisingTimer.start();//start timer for next packet
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.i(LOG_TAG, "Advertising onStartFailure: " + errorCode);
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_STOP;
        }
    };

/*
broadcast packet:
destination: 2 bytes
Packet ID: 1 byte
Thingy: 1 byte
data type: 1 byte
data[]


block:


 */
    //public static final int MAX_ADVERTISE_LIST_ITEM=128;
    //private ArrayList<ClhAdvertisedData> mClhAdvDataList=super.mClhAdvDataList; //=new ArrayList<>(MAX_ADVERTISE_LIST_ITEM);
    private byte[] mAdvData=new byte[64];
    private byte mCurrentPacketID= (byte) 1;
    /*public void setAdvertiseList(ArrayList<ClhAdvertisedData> arrList)
    {
         mClhAdvDataList=arrList;
    }*/

    public ArrayList<ClhAdvertisedData> getAdvertiseList()
    {
        return mClhAdvDataList;
    }

    public void addAdvPacketToBuffer(ClhAdvertisedData data,boolean isOrginal)
    {
        if(mClhAdvDataList.size()<mMaxAdvAllowable) {
            if(isOrginal) {//this packet come from this device-> increase PacketID
                mCurrentPacketID++;
                data.setPacketID(mCurrentPacketID);
            }
            else
            {//received packet over BLE scan, from other cluster head -> increase hopscount
                byte hopcounts=data.getHopCounts();
                hopcounts++;
                data.setHopCount(hopcounts);
            }
            mClhAdvDataList.add(data);
            Log.i(LOG_TAG,"add Adv packet, size:"+mClhAdvDataList.size());
        }
    }

    public void nextAdvertisingPacket(){//advertising next data
        //Log.i(LOG_TAG,"next packet");

        if (mClhAdvDataList.size()>0)
        {//list not empty, advertise item 0 in the list
            mAdvData=mClhAdvDataList.get(0).getParcelClhData();
            Log.i(LOG_TAG,"new data"+Arrays.toString(mAdvData));
            mClhAdvDataList.remove(0);
            Log.i(LOG_TAG,"array size:" + mClhAdvDataList.size());
            if(mClhAdvDataList.size()>0) {
                Log.i(LOG_TAG, "next data" + Arrays.toString(mClhAdvDataList.get(0).getParcelClhData()));
            }
            updateCLHdata(mAdvData);
        }
        else
        {//empty list
            mBleClhAdvertisingStatus=BLE_CLH_ADVERTISING_STATUS_NO_DATA;
            mAdvertisingTimer.start(); // just start timer to check the list later
        }
    }

    private static int mSoundcount=0;
    private static int mAmp=0;
    public void addAdvSoundData(byte[]data)
    {

        if((data!=null) && data.length>0) {
            //if(mSoundcount++>=10) return;

            byte[] arr=new byte[4];
            int len=0;
            arr[3]=data[0];
            arr[2]=data[1];
            arr[1]=0;
            arr[0]=0;
            int sounddata;
            sounddata=ByteBuffer.wrap(arr).getInt();
/*            int min;
            int max;
            min=max=sounddata=ByteBuffer.wrap(arr).getInt();
            Log.i(LOG_TAG,"sound data:"+sounddata);
            Log.i(LOG_TAG,"sound data: "+Arrays.toString(data));

            if(data.length%2==0)
                len=data.length;
            else
                len=data.length-1;
            for (int i = 0; i < len; i+=2) {
                arr[0]=0;
                arr[1]=0;
                arr[2]=data[i+1];
                arr[3]=data[i];
                sounddata=ByteBuffer.wrap(arr).getInt();
                //Log.i(LOG_TAG,"sound data " +i + ":"+sounddata);

                if (sounddata < min) min = sounddata;
                if (sounddata> max) max = sounddata;
            }
            if(mAmp < max - min) mAmp= max - min;
            Log.i(LOG_TAG,"sound data: max amp:"+mAmp);*/


            if(mSoundcount++==100)
            {//filter 1000 dataset

                Log.i(LOG_TAG,"sound data: max amp:"+sounddata);
                Log.i(LOG_TAG,"sound data: "+Arrays.toString(data));

                ClhAdvertisedData advData = new ClhAdvertisedData();
                advData.setSourceID(mClhID);
                advData.setDestId((byte) 0);
                advData.setThingyDataType((byte) 1);
                advData.setThingyId((byte) 1);
                advData.setHopCount((byte) 0);
                //advData.setSoundPower(mAmp);sounddata
                advData.setSoundPower(sounddata);
                addAdvPacketToBuffer(advData,true);
                ClhAdvertisedData temp=mClhAdvDataList.get(mClhAdvDataList.size()-1);
                Log.i(LOG_TAG,"add new sound data:"+ Arrays.toString( temp.getParcelClhData()));
                mSoundcount=0;
                mAmp=0;
            }
        }
    }

    public void clearAdvList()
    {
        mClhAdvDataList.clear();
    }

}

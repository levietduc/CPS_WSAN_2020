/*
 * Copyright (c) 2010 - 2017, Nordic Semiconductor ASA
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form, except as embedded into a Nordic
 *    Semiconductor ASA integrated circuit in a product or a software update for
 *    such product, must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. Neither the name of Nordic Semiconductor ASA nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * 4. This software, with or without modification, must only be used with a
 *    Nordic Semiconductor ASA integrated circuit.
 *
 * 5. Any software provided in binary form under this license must not be reverse
 *    engineered, decompiled, modified and/or disassembled.
 *
 * THIS SOFTWARE IS PROVIDED BY NORDIC SEMICONDUCTOR ASA "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY, NONINFRINGEMENT, AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrfthingy;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.tabs.TabLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.Toolbar;

import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import no.nordicsemi.android.nrfthingy.ClusterHead.ClhAdvertise;
import no.nordicsemi.android.nrfthingy.ClusterHead.ClhAdvertisedData;
import no.nordicsemi.android.nrfthingy.ClusterHead.ClhErrors;
import no.nordicsemi.android.nrfthingy.ClusterHead.ClhProcessData;
import no.nordicsemi.android.nrfthingy.ClusterHead.ClhScan;
import no.nordicsemi.android.nrfthingy.ClusterHead.ClusterHead;
import no.nordicsemi.android.nrfthingy.common.MessageDialogFragment;
import no.nordicsemi.android.nrfthingy.common.PermissionRationaleDialogFragment;
import no.nordicsemi.android.nrfthingy.common.Utils;
import no.nordicsemi.android.nrfthingy.sound.FrequencyModeFragment;
import no.nordicsemi.android.nrfthingy.sound.PcmModeFragment;
import no.nordicsemi.android.nrfthingy.sound.SampleModeFragment;
import no.nordicsemi.android.nrfthingy.sound.ThingyMicrophoneService;
import no.nordicsemi.android.nrfthingy.widgets.VoiceVisualizer;
import no.nordicsemi.android.thingylib.ThingyListener;
import no.nordicsemi.android.thingylib.ThingyListenerHelper;
import no.nordicsemi.android.thingylib.ThingySdkManager;
import no.nordicsemi.android.thingylib.utils.ThingyUtils;

public class SoundFragment extends Fragment implements PermissionRationaleDialogFragment.PermissionDialogListener {
    private static final String AUDIO_PLAYING_STATE = "AUDIO_PLAYING_STATE";
    private static final String AUDIO_RECORDING_STATE = "AUDIO_RECORDING_STATE";
    private static final float ALPHA_MAX = 0.60f;
    private static final float ALPHA_MIN = 0.0f;
    private static final int DURATION = 800;

    private ImageView mMicrophone;
    private ImageView mMicrophoneOverlay;
    private ImageView mThingyOverlay;
    private ImageView mThingy;
    private VoiceVisualizer mVoiceVisualizer;

    private BluetoothDevice mDevice;
    private FragmentAdapter mFragmentAdapter;
    private ThingySdkManager mThingySdkManager;
    private boolean mStartRecordingAudio = false;
    private boolean mStartPlayingAudio = false;

    private ThingyListener mThingyListener = new ThingyListener() {
        private Handler mHandler = new Handler();

        @Override
        public void onDeviceConnected(BluetoothDevice device, int connectionState) {
        }

        @Override
        public void onDeviceDisconnected(BluetoothDevice device, int connectionState) {
            if (device.equals(mDevice)) {
                stopRecording();
                stopMicrophoneOverlayAnimation();
                stopThingyOverlayAnimation();
                mStartPlayingAudio = false;
            }
        }

        @Override
        public void onServiceDiscoveryCompleted(BluetoothDevice device) {
        }

        @Override
        public void onBatteryLevelChanged(final BluetoothDevice bluetoothDevice, final int batteryLevel) {

        }

        @Override
        public void onTemperatureValueChangedEvent(BluetoothDevice bluetoothDevice, String temperature) {
        }

        @Override
        public void onPressureValueChangedEvent(BluetoothDevice bluetoothDevice, final String pressure) {
        }

        @Override
        public void onHumidityValueChangedEvent(BluetoothDevice bluetoothDevice, final String humidity) {
        }

        @Override
        public void onAirQualityValueChangedEvent(BluetoothDevice bluetoothDevice, final int eco2, final int tvoc) {
        }

        @Override
        public void onColorIntensityValueChangedEvent(BluetoothDevice bluetoothDevice, final float red, final float green, final float blue, final float alpha) {
        }

        @Override
        public void onButtonStateChangedEvent(BluetoothDevice bluetoothDevice, int buttonState) {

        }

        @Override
        public void onTapValueChangedEvent(BluetoothDevice bluetoothDevice, int direction, int count) {

        }

        @Override
        public void onOrientationValueChangedEvent(BluetoothDevice bluetoothDevice, int orientation) {

        }

        @Override
        public void onQuaternionValueChangedEvent(BluetoothDevice bluetoothDevice, float w, float x, float y, float z) {

        }

        @Override
        public void onPedometerValueChangedEvent(BluetoothDevice bluetoothDevice, int steps, long duration) {

        }

        @Override
        public void onAccelerometerValueChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onGyroscopeValueChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onCompassValueChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onEulerAngleChangedEvent(BluetoothDevice bluetoothDevice, float roll, float pitch, float yaw) {

        }

        @Override
        public void onRotationMatrixValueChangedEvent(BluetoothDevice bluetoothDevice, byte[] matrix) {

        }

        @Override
        public void onHeadingValueChangedEvent(BluetoothDevice bluetoothDevice, float heading) {

        }

        @Override
        public void onGravityVectorChangedEvent(BluetoothDevice bluetoothDevice, float x, float y, float z) {

        }

        @Override
        public void onSpeakerStatusValueChangedEvent(BluetoothDevice bluetoothDevice, int status) {

        }

        @Override
        public void onMicrophoneValueChangedEvent(BluetoothDevice bluetoothDevice, final byte[] data) {
            if (data != null) {
                if (data.length != 0) {


                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mVoiceVisualizer.draw(data);

                        }
                    });

                    //PSG edit
                    if( mStartPlayingAudio = true)
                         mClhAdvertiser.addAdvSoundData(data);

                }
            }
        }
    };

    private BroadcastReceiver mAudioRecordBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.startsWith(Utils.EXTRA_DATA_AUDIO_RECORD)) {
                final byte[] tempPcmData = intent.getExtras().getByteArray(ThingyUtils.EXTRA_DATA_PCM);
                final int length = intent.getExtras().getInt(ThingyUtils.EXTRA_DATA);
                if (tempPcmData != null) {
                    if (length != 0) {
                        mVoiceVisualizer.draw(tempPcmData);
                    }
                }
            } else if (action.equals(Utils.ERROR_AUDIO_RECORD)) {
                final String error = intent.getExtras().getString(Utils.EXTRA_DATA);
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            }
        }
    };

    public static SoundFragment newInstance(final BluetoothDevice device) {
        SoundFragment fragment = new SoundFragment();
        final Bundle args = new Bundle();
        args.putParcelable(Utils.CURRENT_DEVICE, device);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mDevice = getArguments().getParcelable(Utils.CURRENT_DEVICE);
        }
        mThingySdkManager = ThingySdkManager.getInstance();
    }


    //PSG edit

    private Button mAdvertiseButton;
    private EditText mClhIDInput;
    private TextView mClhLog;
    private final String LOG_TAG="CLH Sound";

    private byte[] mAdvData= {10,1,2,3,4,5,6,7,8,9,10};
    byte[] mAdvSettings={ClhAdvertise.ADV_SETTING_MODE_LOWLATENCY,
            ClhAdvertise.ADV_SETTING_SENDNAME_YES,
            ClhAdvertise.ADV_SETTING_SENDTXPOWER_NO};
    private ArrayList<ClhAdvertisedData> mAdvDataArr;
    private ArrayList<ClhAdvertisedData> mProcDataArr=new ArrayList<ClhAdvertisedData>(64);
    private ClhAdvertisedData mClhData=new ClhAdvertisedData();
    private boolean mIsSink=false;
    private byte mClhID=2;
    private byte mClhPacketID=1;
    private byte mClhDestID=0;
    private byte mClhHops=2;
    private byte mClhThingyID=1;
    private byte mClhThingyType=1;
    private int mClhThingySoundPower=100;


    ClusterHead mClh;
    ClhAdvertise mClhAdvertiser;
    ClhScan mClhScanner;
    ClhProcessData mClhProcessor;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_sound, container, false);

        final Toolbar speakerToolbar = rootView.findViewById(R.id.speaker_toolbar);
        speakerToolbar.inflateMenu(R.menu.audio_warning);
        speakerToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final int id = item.getItemId();
                switch (id) {
                    case R.id.action_audio_warning:
                        MessageDialogFragment fragment = MessageDialogFragment.newInstance(getString(R.string.info), getString(R.string.mtu_warning));
                        fragment.show(getChildFragmentManager(), null);
                        break;
                }
                return false;
            }
        });

        final Toolbar microphoneToolbar = rootView.findViewById(R.id.microphone_toolbar);
        microphoneToolbar.inflateMenu(R.menu.audio_warning);
        microphoneToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final int id = item.getItemId();
                switch (id) {
                    case R.id.action_audio_warning:
                        MessageDialogFragment fragment = MessageDialogFragment.newInstance(getString(R.string.info), getString(R.string.mtu_warning));
                        fragment.show(getChildFragmentManager(), null);
                        break;
                }
                return false;
            }
        });

        mMicrophone = rootView.findViewById(R.id.microphone);
        mMicrophoneOverlay = rootView.findViewById(R.id.microphoneOverlay);
        mThingy = rootView.findViewById(R.id.thingy);
        mThingyOverlay = rootView.findViewById(R.id.thingyOverlay);
        mVoiceVisualizer = rootView.findViewById(R.id.voice_visualizer);

        // Prepare the sliding tab layout and the view pager
        final TabLayout mTabLayout = rootView.findViewById(R.id.sliding_tabs);
        final ViewPager pager = rootView.findViewById(R.id.view_pager);
        mFragmentAdapter = new FragmentAdapter(getChildFragmentManager());
        pager.setAdapter(mFragmentAdapter);
        mTabLayout.setupWithViewPager(pager);
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(final int position) {
                switch (position) {
                    case 1:
                        mFragmentAdapter.setSelectedFragment(position);
                        break;
                    default:
                        mFragmentAdapter.setSelectedFragment(position);
                        break;
                }
            }

            @Override
            public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
            }

            @Override
            public void onPageScrollStateChanged(final int state) {
            }
        });

        mMicrophone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mThingySdkManager.isConnected(mDevice)) {
                    if (!mStartRecordingAudio) {
                        checkMicrophonePermissions();
                    } else {
                        stopRecording();
                    }
                }
            }
        });


         mThingy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mThingySdkManager.isConnected(mDevice)) {
                    if (!mStartPlayingAudio) {
                        mStartPlayingAudio = true;
                        startThingyOverlayAnimation();

                        mThingySdkManager.enableThingyMicrophone(mDevice, true);
                    } else {
                        mThingySdkManager.enableThingyMicrophone(mDevice, false);
                        stopThingyOverlayAnimation();
                        mStartPlayingAudio = false;
                    }
                }
            }
        });

        if (savedInstanceState != null) {
            mStartPlayingAudio = savedInstanceState.getBoolean(AUDIO_PLAYING_STATE);
            mStartRecordingAudio = savedInstanceState.getBoolean(AUDIO_RECORDING_STATE);

            if (mStartPlayingAudio) {
                startThingyOverlayAnimation();
            }

            if (mStartRecordingAudio) {
                if (mThingySdkManager.isConnected(mDevice)) {
                    startMicrophoneOverlayAnimation();
                    sendAudiRecordingBroadcast();
                }
            }
        }

        loadFeatureDiscoverySequence();


        //PSG edit

        mAdvertiseButton = (Button) rootView.findViewById(R.id.startClh_btn);
        mClhIDInput=(EditText) rootView.findViewById(R.id.clhIDInput_text);
        mClhLog=(TextView) rootView.findViewById(R.id.logClh_text);


        /*mAdvertiser.setAdvInterval(1000);
        mAdvertiser.setAdvSettings(new byte[] {ClhAdvertise.ADV_SETTING_MODE_LOWLATENCY,
                ClhAdvertise.ADV_SETTING_SENDNAME_YES,
                ClhAdvertise.ADV_SETTING_SENDTXPOWER_NO});
        mAdvDataArr=mAdvertiser.getAdvertiseList();
        mAdvertiser.setClhID(mClhID);  //set ID to advertiser
        int error=mAdvertiser.initCLHAdvertiser();



        if(error!= ClhErrors.ERROR_CLH_NO)
        {
            mAdvertiseButton.setEnabled(false);
        }


        mScanner.setCLH_ID(mAdvertiser.getCLH_ID()); //sync ID of advertiser and scanner
        mScanner.setAdvertiseObject(mAdvertiser);
        //mScanner.setReturnAdvertiseArr(mAdvDataArr);
        mScanner.setReturnProcessArr(mProcDataArr);
        mScanner.setCLH_ID(mClhID);
        mScanner.BLE_scan();*/

        //mClhIDInput.setText(mClhID); //set text on Input text box
        mClh=new ClusterHead(mClhID);
        mClh.initClhBLE(200);
        mClhAdvertiser=mClh.getClhAdvertiser();
        mClhScanner=mClh.getClhScanner();
        mClhProcessor=mClh.getClhProcessor();

        final Handler handler=new Handler();
        handler. postDelayed(new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(this, 1000); //loop every cycle
                ArrayList<ClhAdvertisedData> procList=mClhProcessor.getProcessDataList();
                for(int i=0; i<procList.size();i++)
                {
                    if(i==10) break; //just display 10 line in one cycle
                    byte[] data=procList.get(0).getParcelClhData();
                    mClhLog.append(Arrays.toString(data));
                    mClhLog.append("\r\n");
                    procList.remove(0);
                }
            }
        }, 1000); //the time you want to delay in milliseconds

        mAdvertiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Resources res = getResources();

                Log.i(LOG_TAG, mAdvertiseButton.getText().toString());
                if (mAdvertiseButton.getText().toString().equals("Start")) {
                    mAdvertiseButton.setText("Stop");
                    mClhIDInput.setEnabled(false);

                    mClh.clearClhAdvList(); //empty list before starting

                    String strEnteredVal = mClhIDInput.getText().toString();
                    if ((strEnteredVal.compareTo("") == 0) || (strEnteredVal == null)) {


                        mClhIDInput.setText(Integer.toString((int) mClhID));
                        Log.i(LOG_TAG, "error: ClhID must be in 0-127");
                        Log.i(LOG_TAG, "set ClhID default:"+mClhID);

                    } else {
                        int num = Integer.valueOf(strEnteredVal);
                        mClhID = (byte) num;
                        mIsSink = mClh.setClhID(mClhID);
                        Log.i(LOG_TAG, "set ClhID:"+mClhID);
                    }
                    if(mClhID==127) {
                        //mClhID = 1;
                        mClhPacketID = 1;
                        mClhThingySoundPower = 100;
                        mClhData.setSourceID(mClhID);
                        mClhData.setPacketID(mClhPacketID);
                        mClhData.setDestId(mClhDestID);
                        mClhData.setHopCount(mClhHops);
                        mClhData.setThingyId(mClhThingyID);
                        mClhData.setThingyDataType(mClhThingyType);
                        mClhData.setSoundPower(mClhThingySoundPower);
                        //vinh,mAdvDataArr.clear();
                        //mAdvDataArr.add(mClhData);
                        mClhAdvertiser.addAdvPacketToBuffer(mClhData,true);
                        for (int i = 0; i < 100; i++) {
                            ClhAdvertisedData clh = new ClhAdvertisedData();
                            clh.Copy(mClhData);
                            //Log.i(LOG_TAG, "Array old:" + Arrays.toString(clh.getParcelClhData()));

                            mClhThingySoundPower += 10;
                            clh.setSoundPower(mClhThingySoundPower);
                            //mClhPacketID++;
                            //clh.setPacketID(mClhPacketID);
                            //vinh, mAdvDataArr.add(clh);
                            mClhAdvertiser.addAdvPacketToBuffer(clh,true);

                            Log.i(LOG_TAG, "Array new:" + Arrays.toString(clh.getParcelClhData()));
                            //Log.i(LOG_TAG, "Array list size:" + mAdvDataArr.size());
                            Log.i(LOG_TAG, "Array list size:" + mClhAdvertiser.getAdvertiseList().size());
                        }
                        Log.i(LOG_TAG, "mClhData:" + Arrays.toString(mAdvData));
                        ClhAdvertisedData temp;
                        ArrayList<ClhAdvertisedData> temp1=mClhAdvertiser.getAdvertiseList();
                        for (int i = 0; i < 20; i++) {
                            //vinh, temp = mAdvDataArr.get(j);
                            //vinh
                            if((temp1==null)||(temp1.size() <20)) {
                                Log.i(LOG_TAG, "temp1 null, size"+ temp1.size());
                            }
                            else{
                                temp = temp1.get(i);
                                Log.i(LOG_TAG, "Array final:" + Arrays.toString(temp.getParcelClhData()));

                            }
                        }
                    }

                    mClhAdvertiser.nextAdvertisingPacket();
                    //mAdvData=mClhData.getParcelClhData();
                    //mAdvertiser.updateCLHdata(mAdvData);
                }
                else
                {
                    mAdvertiseButton.setText("Start");
                    mClhIDInput.setEnabled(true);

                    mClhAdvertiser.stopCLHdata();
                }
            }
        });
        mClhIDInput.setText(Integer.toString((int)mClhID));



       /* mClhIDInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {

                    //if (mClhIDInput.getText() != null) {
                        String strEnteredVal = mClhIDInput.getText().toString();

                        Log.i(LOG_TAG, "error1:" + strEnteredVal.length());
                        if ((strEnteredVal.compareTo("") == 0) || (strEnteredVal != null)) {
                            Log.i(LOG_TAG, "error2:");

                            mClhIDInput.setText(Integer.toString((int) mClhID));

                        } else {
                            int num = Integer.valueOf(strEnteredVal);
                            mClhID = (byte) num;
                            mIsSink = mClh.setClhID(mClhID);
                        }

                    /*} else {
                        Log.i(LOG_TAG, "error3:" + strEnteredVal.length());

                        mClhIDInput.setText(Integer.toString((int) mClhID));
                    }                //SAVE THE DATA
                }
            }

                public void afterTextChanged(Editable s) {

            }


        });*/

        return rootView;
    }

    private void sendAudiRecordingBroadcast() {
        Intent startAudioRecording = new Intent(getActivity(), ThingyMicrophoneService.class);
        startAudioRecording.setAction(Utils.START_RECORDING);
        startAudioRecording.putExtra(Utils.EXTRA_DEVICE, mDevice);
        getActivity().startService(startAudioRecording);
    }

    private void stop() {
        final Intent s = new Intent(Utils.STOP_RECORDING);
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(s);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUDIO_PLAYING_STATE, mStartPlayingAudio);
        outState.putBoolean(AUDIO_RECORDING_STATE, mStartRecordingAudio);
    }

    @Override
    public void onResume() {
        super.onResume();
        ThingyListenerHelper.registerThingyListener(getContext(), mThingyListener, mDevice);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mAudioRecordBroadcastReceiver, createAudioRecordIntentFilter(mDevice.getAddress()));
    }

    @Override
    public void onPause() {
        super.onPause();
        ThingyListenerHelper.unregisterThingyListener(getContext(), mThingyListener);
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mAudioRecordBroadcastReceiver);
        mVoiceVisualizer.stopDrawing();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopRecording();
        stopThingyOverlayAnimation();
    }

    @Override
    public void onRequestPermission(final String permission, final int requestCode) {
        // Since the nested child fragment (activity > fragment > fragment) wasn't getting called
        // the exact fragment index has to be used to get the fragment.
        // Also super.onRequestPermissionResult had to be used in both the main activity, fragment
        // in order to propagate the request permission callback to the nested fragment
        requestPermissions(new String[]{permission}, requestCode);
    }

    @Override
    public void onCancellingPermissionRationale() {
        Utils.showToast(getActivity(), getString(R.string.requested_permission_not_granted_rationale));
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Utils.REQ_PERMISSION_RECORD_AUDIO:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Utils.showToast(getActivity(), getString(R.string.rationale_permission_denied));
                } else {
                    startRecording();
                }
        }
    }

    private void checkMicrophonePermissions() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            final PermissionRationaleDialogFragment dialog = PermissionRationaleDialogFragment.getInstance(Manifest.permission.RECORD_AUDIO,
                    Utils.REQ_PERMISSION_RECORD_AUDIO, getString(R.string.microphone_permission_text));
            dialog.show(getChildFragmentManager(), null);
        }
    }

    private void startRecording() {
        startMicrophoneOverlayAnimation();
        sendAudiRecordingBroadcast();
        mStartRecordingAudio = true;
    }

    private void stopRecording() {
        stopMicrophoneOverlayAnimation();
        stop();
        mStartRecordingAudio = false;
    }

    private void startMicrophoneOverlayAnimation() {
        mThingy.setEnabled(false);
        mMicrophone.setImageResource(R.drawable.ic_mic_white_off);
        mMicrophone.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_red));
        mMicrophoneOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (mMicrophoneOverlay.getAlpha() == ALPHA_MAX) {
                    mMicrophoneOverlay.animate().alpha(ALPHA_MIN).setDuration(DURATION).withEndAction(this).start();
                } else {
                    mMicrophoneOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(this).start();
                }
            }
        }).start();
    }

    private void stopMicrophoneOverlayAnimation() {
        mThingy.setEnabled(true);
        mStartRecordingAudio = false;
        mMicrophoneOverlay.animate().cancel();
        mMicrophoneOverlay.setAlpha(ALPHA_MIN);
        mMicrophone.setImageResource(R.drawable.ic_mic_white);
        mMicrophone.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_blue));
    }

    private void startThingyOverlayAnimation() {
        mMicrophone.setEnabled(false);
        mThingy.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_red));
        mThingyOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (mThingyOverlay.getAlpha() == ALPHA_MAX) {
                    mThingyOverlay.animate().alpha(ALPHA_MIN).setDuration(DURATION).withEndAction(this).start();
                } else {
                    mThingyOverlay.animate().alpha(ALPHA_MAX).setDuration(DURATION).withEndAction(this).start();
                }
            }
        }).start();
    }

    private void stopThingyOverlayAnimation() {
        mMicrophone.setEnabled(true);
        mThingyOverlay.animate().cancel();
        mThingyOverlay.setAlpha(ALPHA_MIN);
        mThingy.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_device_bg_blue));
        mStartPlayingAudio = false;
    }

    private class FragmentAdapter extends FragmentPagerAdapter {
        private int mSelectedFragmentTab = 0;

        FragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return FrequencyModeFragment.newInstance(mDevice);
                case 1:
                    return PcmModeFragment.newInstance(mDevice);
                default:
                case 2:
                    return SampleModeFragment.newInstance(mDevice);
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getResources().getStringArray(R.array.sound_tab_title)[position];
        }

        void setSelectedFragment(final int selectedTab) {
            mSelectedFragmentTab = selectedTab;
        }

        public int getSelectedFragment() {
            return mSelectedFragmentTab;
        }
    }

    private static IntentFilter createAudioRecordIntentFilter(final String address) {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(Utils.EXTRA_DATA_AUDIO_RECORD + address);
        intentFilter.addAction(Utils.ERROR_AUDIO_RECORD);
        return intentFilter;
    }

    private void displayStreamingInformationDialog() {
        final SharedPreferences sp = requireActivity().getSharedPreferences(Utils.PREFS_INITIAL_SETUP, Context.MODE_PRIVATE);
        final boolean showStreamingDialog = sp.getBoolean(Utils.INITIAL_AUDIO_STREAMING_INFO, true);
        if (showStreamingDialog) {
            MessageDialogFragment fragment = MessageDialogFragment.newInstance(getString(R.string.info), getString(R.string.mtu_warning));
            fragment.show(getChildFragmentManager(), null);

            final SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(Utils.INITIAL_AUDIO_STREAMING_INFO, false);
            editor.apply();
        }
    }

    private void loadFeatureDiscoverySequence() {
        if (!Utils.checkIfSequenceIsCompleted(requireContext(), Utils.INITIAL_SOUND_TUTORIAL)) {

            final SpannableString microphone = new SpannableString(getString(R.string.start_talking_to_thingy));
            final SpannableString thingy = new SpannableString(getString(R.string.start_talking_from_thingy));

            final TapTargetSequence sequence = new TapTargetSequence(requireActivity());
            sequence.continueOnCancel(true);
            sequence.targets(
                    TapTarget.forView(mMicrophone, microphone).
                            transparentTarget(true).
                            dimColor(R.color.grey).
                            outerCircleColor(R.color.accent).id(0),
                    TapTarget.forView(mThingy, thingy).
                            transparentTarget(true).
                            dimColor(R.color.grey).
                            outerCircleColor(R.color.accent).id(1)
            ).listener(new TapTargetSequence.Listener() {
                @Override
                public void onSequenceFinish() {
                    Utils.saveSequenceCompletion(requireContext(), Utils.INITIAL_SOUND_TUTORIAL);
                    displayStreamingInformationDialog();
                }

                @Override
                public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {

                }

                @Override
                public void onSequenceCanceled(TapTarget lastTarget) {

                }
            }).start();
        }
    }
}
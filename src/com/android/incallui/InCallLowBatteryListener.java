
/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.view.WindowManager;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallUiListener;
import org.codeaurora.ims.QtiCallConstants;

public class InCallLowBatteryListener implements CallList.Listener, InCallDetailsListener,
        InCallUiListener {

    private static InCallLowBatteryListener sInCallLowBatteryListener;
    private PrimaryCallTracker mPrimaryCallTracker;
    private CallList mCallList = null;
    private AlertDialog mAlert = null;
    private List <Call> mLowBatteryCalls = new CopyOnWriteArrayList<>();

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallLowBatteryListener() {
    }

    /**
     * Handles set up of the {@class InCallLowBatteryListener}.
     */
    public void setUp(Context context) {
        mPrimaryCallTracker = new PrimaryCallTracker();
        mCallList = CallList.getInstance();
        mCallList.addListener(this);
        InCallPresenter.getInstance().addListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().addIncomingCallListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addInCallUiListener(this);
    }

    /**
     * Handles tear down of the {@class InCallLowBatteryListener}.
     */
    public void tearDown() {
        if (mCallList != null) {
            mCallList.removeListener(this);
            mCallList = null;
        }
        InCallPresenter.getInstance().removeListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().removeIncomingCallListener(mPrimaryCallTracker);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallUiListener(this);
        mPrimaryCallTracker = null;
    }

     /**
     * This method returns a singleton instance of {@class InCallLowBatteryListener}
     */
    public static synchronized InCallLowBatteryListener getInstance() {
        if (sInCallLowBatteryListener == null) {
            sInCallLowBatteryListener = new InCallLowBatteryListener();
        }
        return sInCallLowBatteryListener;
    }

    /**
     * This method overrides onIncomingCall method of {@interface CallList.Listener}
     */
    @Override
    public void onIncomingCall(Call call) {
        //if low battery dialog is already visible to user, dismiss it
        dismissPendingDialogs();
    }

    /**
     * This method overrides onCallListChange method of {@interface CallList.Listener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onCallListChange(CallList list) {
        // no-op
    }

    /**
     * This method overrides onUpgradeToVideo method of {@interface CallList.Listener}
     */
    @Override
    public void onUpgradeToVideo(Call call) {
        //if low battery dialog is visible to user, dismiss it
        dismissPendingDialogs();
    }

    /**
     * This method overrides onDisconnect method of {@interface CallList.Listener}
     */
    @Override
    public void onDisconnect(Call call) {
        Log.i(this, "onDisconnect call: " + call);
        updateCallInMap(call);

        //if low battery dialog is visible to user, dismiss it
        if (mPrimaryCallTracker.isPrimaryCall(call)) {
            dismissPendingDialogs();
        }
    }

    /**
     * This API conveys if incall experience is showing or not.
     *
     * @param showing TRUE if incall experience is showing else FALSE
     */
    @Override
    public void onUiShowing(boolean showing) {
        Call call = mPrimaryCallTracker.getPrimaryCall();
        Log.i(this, "onUiShowing showing: " + showing + "call = " + call);

        if (!showing || call == null) {
            return;
        }

        /*
         * There can be chances to miss display of low battery alert dialog
         * to user since incallactivity may be null. Eg of such a use-case is
         * accepting Video call from heads-up notification. So, when incall
         * experience is showing, handle missed low battery alert indications (if any)
         */
        maybeProcessLowBatteryIndication(call, call.getTelecommCall().getDetails());
    }

    /**
     * Handles changes to the details of the call.
     *
     * @param call The call for which the details changed.
     * @param details The new call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        Log.d(this, " onDetailsChanged call=" + call + " details=" + details);

        if (call == null || !mPrimaryCallTracker.isPrimaryCall(call)) {
            Log.d(this," onDetailsChanged: call is null/Details not for primary call");
            return;
        }

        maybeProcessLowBatteryIndication(call, details);
    }

    private void maybeProcessLowBatteryIndication(Call call,
            android.telecom.Call.Details details) {

        final Bundle extras =  (details != null) ? details.getExtras() : null;
        final boolean isLowBattery = (extras != null) ? extras.getBoolean(
                QtiCallConstants.LOW_BATTERY_EXTRA_KEY, false) : false;
        Log.i(this, "maybeProcessLowBatteryIndication: isLowBattery : " + isLowBattery);

        if (isLowBattery && updateCallInMap(call)) {
            processLowBatteryIndication(call);
        }
    }

    /*
     * processes the low battery indication for an
     * unpaused active video call
     */
    private void processLowBatteryIndication(Call call) {
        Log.i(this, "processLowBatteryIndication call: " + call);
        if (CallUtils.isActiveUnPausedVideoCall(call)) {
            Log.i(this, "is an active unpaused video call");
            //if low battery dialog is already visible to user, dismiss it
            dismissPendingDialogs();
            displayLowBatteryAlert(call);
        }
    }

    /*
     * Adds/Removes the call to mLowBatteryCalls
     * Returns TRUE if call is added to mLowBatteryCalls else FALSE
     */
    private boolean updateCallInMap(Call call) {
        if (call == null) {
            Log.e(this, "call is null");
            return false;
        }

        final boolean isPresent = mLowBatteryCalls.contains(call);
        if (!Call.State.isConnectingOrConnected(call.getState())) {
            if (isPresent) {
                //we are done with the call so remove from callmap
                mLowBatteryCalls.remove(call);
                return false;
            }
        } else if (InCallPresenter.getInstance().getActivity() == null) {
            /*
             * Displaying Low Battery alert dialog requires incallactivity context
             * so return false if there is no incallactivity context
             */
            Log.i(this, "incallactivity is null");
            return false;
        } else if (CallUtils.isActiveUnPausedVideoCall(call) && !isPresent
                && call.getParentId() == null) {
            /*
             * call will be added to call map only if below conditions are satisfied:
             * 1. call is not a child call
             * 2. call is a unpaused active video call
             * 3. low battery indication for that call is not yet processed
             */
            mLowBatteryCalls.add(call);
            return true;
        }
        return false;
    }

    /*
     * This method displays either of below alert dialog when UE is in low battery
     * 1. hangup alert dialog in absence of voice capabilities
     * 2. downgrade to voice call alert dialog in the presence of voice
     *    capabilities
     */
    private void displayLowBatteryAlert(final Call call) {
        final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
        if (inCallActivity == null) {
            Log.e(this, "displayLowBatteryAlert inCallActivity is NULL");
            return;
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(inCallActivity);
        alertDialog.setTitle(R.string.low_battery);
        alertDialog.setNegativeButton(R.string.low_battery_no, null);
        alertDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(final DialogInterface dialog) {
                Log.d(this, "displayLowBatteryAlert onDismiss");
                mAlert = null;
            }
        });

        if (QtiCallUtils.hasVoiceCapabilities(call)) {
            //active video call can be downgraded to voice
            alertDialog.setMessage(R.string.low_battery_downgrade_to_voice_msg);
            alertDialog.setPositiveButton(R.string.low_battery_yes, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                     Log.i(this, "displayLowBatteryAlert downgrading to voice call");
                     QtiCallUtils.downgradeToVoiceCall(call);
                }
            });
        } else {
            /* video call doesn't have downgrade capabilities, so alert the user
               with a hangup dialog*/
            alertDialog.setMessage(R.string.low_battery_hangup_msg);
            alertDialog.setPositiveButton(R.string.low_battery_yes, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                     Log.i(this, "displayLowBatteryAlert hanging up the call: " + call);
                     final String callId = call.getId();
                     call.setState(Call.State.DISCONNECTING);
                     CallList.getInstance().onUpdate(call);
                     TelecomAdapter.getInstance().disconnectCall(callId);
                }
            });
        }

        mAlert = alertDialog.create();
        mAlert.setCanceledOnTouchOutside(false);
        mAlert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mAlert.show();
    }

    /*
     * This method dismisses the low battery dialog
     */
    private void dismissPendingDialogs() {
        if (mAlert != null && mAlert.isShowing()) {
            mAlert.dismiss();
            mAlert = null;
        }
    }
}

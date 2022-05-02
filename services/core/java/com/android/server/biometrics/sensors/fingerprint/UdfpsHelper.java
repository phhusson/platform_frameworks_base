/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.biometrics.sensors.fingerprint;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.os.RemoteException;
import android.util.Slog;

import vendor.goodix.hardware.biometrics.fingerprint.V2_1.IGoodixFingerprintDaemon;
import vendor.samsung.hardware.biometrics.fingerprint.V3_0.ISehBiometricsFingerprint;

import java.io.PrintWriter;

/**
 * Contains helper methods for under-display fingerprint HIDL.
 */
public class UdfpsHelper {

    private static final String TAG = "UdfpsHelper";

    private static void writeFile(String path, String value) {
        try {
            PrintWriter writer = new PrintWriter(path, "UTF-8");
            writer.println(value);
            writer.close();
        } catch(Exception e) {
            android.util.Log.d("PHH", "Failed writing to " + path + ": " + value);
        }
    }


    public static void onFingerDown(IBiometricsFingerprint daemon, int x, int y, float minor,
            float major) {
        android.hardware.biometrics.fingerprint.V2_3.IBiometricsFingerprint extension =
            android.hardware.biometrics.fingerprint.V2_3.IBiometricsFingerprint.castFrom(
                    daemon);
        if (extension != null) {
            try {
                extension.onFingerDown(x, y, minor, major);
                return;
            } catch (RemoteException e) {
                Slog.e(TAG, "onFingerDown | RemoteException: ", e);
            }
        }

        try {
            if("true".equals(android.os.SystemProperties.get("persist.sys.phh.ultrasonic_udfps"))) {
                Slog.e(TAG, "trying ultrasonic samsung pressed");
                ISehBiometricsFingerprint fp = ISehBiometricsFingerprint.getService();
                fp.sehRequest(22 /* SEM_FINGER_STATE */, 2 /* finger pressed */, new java.util.ArrayList<Byte>(),
                        (int retval, java.util.ArrayList<Byte> out) -> {} );
            }
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending Samsung command failed");
        }

        try {
            // Asus goodix commands
            IGoodixFingerprintDaemon goodixDaemon = IGoodixFingerprintDaemon.getService();
            if(android.os.SystemProperties.get("ro.vendor.build.fingerprint").contains("ASUS")) {
                goodixDaemon.sendCommand(200001, new java.util.ArrayList<Byte>(), (returnCode, resultData) -> {
                    Slog.e(TAG, "Goodix send command returned code "+ returnCode);
                });
            } else {
                //UI READY
                goodixDaemon.sendCommand(0x600, new java.util.ArrayList<Byte>(), (returnCode, resultData) -> {
                    Slog.e(TAG, "Goodix send command returned code "+ returnCode);
                });
                goodixDaemon.sendCommand(1607, new java.util.ArrayList<Byte>(), (returnCode, resultData) -> {
                    Slog.e(TAG, "Goodix send command returned code "+ returnCode);
                });
            }
            return;
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending goodix daemon cmd failed", t);
        }

        try {
            vendor.oplus.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint fp = vendor.oplus.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint.getService();
            writeFile("/sys/kernel/oppo_display/notify_fppress", "1");
            writeFile("/sys/kernel/oplus_display/oplus_notify_fppress", "1");
            fp.touchDown();
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending oplus daemon cmd failed", t);
        }

        try {
            vendor.oppo.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint fp = vendor.oppo.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint.getService();
            writeFile("/sys/kernel/oppo_display/notify_fppress", "1");
            writeFile("/sys/kernel/oplus_display/oplus_notify_fppress", "1");
            fp.touchDown();
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending oplus daemon cmd failed", t);
        }

        Slog.v(TAG, "onFingerDown | failed to cast the HIDL to V2_3");
    }

    public static void onFingerUp(IBiometricsFingerprint daemon) {
        android.hardware.biometrics.fingerprint.V2_3.IBiometricsFingerprint extension =
            android.hardware.biometrics.fingerprint.V2_3.IBiometricsFingerprint.castFrom(
                    daemon);
        if (extension != null) {
            try {
                extension.onFingerUp();
                return;
            } catch (RemoteException e) {
                Slog.e(TAG, "onFingerUp | RemoteException: ", e);
            }
        }

        try {
            if("true".equals(android.os.SystemProperties.get("persist.sys.phh.ultrasonic_udfps"))) {
                Slog.e(TAG, "trying ultrasonic samsung released");
                ISehBiometricsFingerprint fp = ISehBiometricsFingerprint.getService();
                fp.sehRequest(22 /* SEM_FINGER_STATE */, 1 /* finger pressed */, new java.util.ArrayList<Byte>(),
                        (int retval, java.util.ArrayList<Byte> out) -> {} );
            }
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending Samsung command failed");
        }

        try {
            IGoodixFingerprintDaemon goodixDaemon = IGoodixFingerprintDaemon.getService();
            if(android.os.SystemProperties.get("ro.vendor.build.fingerprint").contains("ASUS")) {
                goodixDaemon.sendCommand(200003, new java.util.ArrayList<Byte>(), (returnCode, resultData) -> {
                    Slog.e(TAG, "Goodix send command returned code " + returnCode);
                });
            } else {
                goodixDaemon.sendCommand(0x601, new java.util.ArrayList<Byte>(), (returnCode, resultData) -> {
                    Slog.e(TAG, "Goodix send command returned code "+ returnCode);
                });
            }
            return;
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending goodix daemon cmd failed", t);
        }

        try {
            vendor.oplus.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint fp = vendor.oplus.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint.getService();
            writeFile("/sys/kernel/oppo_display/notify_fppress", "0");
            writeFile("/sys/kernel/oplus_display/oplus_notify_fppress", "0");
            fp.touchUp();
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending oplus daemon cmd failed", t);
        }

        try {
            vendor.oppo.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint fp = vendor.oppo.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint.getService();
            writeFile("/sys/kernel/oppo_display/notify_fppress", "0");
            writeFile("/sys/kernel/oplus_display/oplus_notify_fppress", "0");
            fp.touchUp();
        } catch(Throwable t) {
            Slog.e(TAG, "Tried sending oplus daemon cmd failed", t);
        }
        Slog.v(TAG, "onFingerUp | failed to cast the HIDL to V2_3");
    }

    public static boolean isValidAcquisitionMessage(@NonNull Context context,
            int acquireInfo, int vendorCode) {
        return FingerprintManager.getAcquiredString(context, acquireInfo, vendorCode) != null;
    }
}

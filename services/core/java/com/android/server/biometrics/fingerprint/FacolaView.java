/**
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.biometrics.fingerprint;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.content.Context;
import android.view.View.OnTouchListener;
import android.view.View;
import android.widget.ImageView;
import android.view.MotionEvent;
import android.util.Slog;

import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.view.Gravity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;

import vendor.xiaomi.hardware.fingerprintextension.V1_0.IXiaomiFingerprint;
import vendor.goodix.extend.service.V2_0.IGoodixFPExtendService;
import vendor.samsung.hardware.biometrics.fingerprint.V2_1.ISecBiometricsFingerprint;

import android.hardware.display.DisplayManager;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.ServiceManager;

public class FacolaView extends ImageView implements OnTouchListener {
    private int mX, mY, mW, mH;
    private final Paint mPaintFingerprint = new Paint();
    private final Paint mPaintShow = new Paint();
    private IXiaomiFingerprint mXiaomiFingerprint = null;
    private IGoodixFPExtendService mGoodixFingerprint = null;
    private ISecBiometricsFingerprint mSamsungFingerprint = null;
    private vendor.oppo.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint mOppoFingerprint = null;
    private boolean mInsideCircle = false;
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private final WindowManager.LayoutParams mParamsTouched = new WindowManager.LayoutParams();

    private final static float UNTOUCHED_DIM = .1f;
    private final static float TOUCHED_DIM = .9f;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private final WindowManager mWM;
    private final DisplayManager mDM;
    private final boolean samsungFod = samsungHasCmd("fod_enable");
    private boolean noDim;

    private boolean mFullGreenDisplayed = false;
    private final View mFullGreen;
    private boolean mHidden = true;
    FacolaView(Context context) {
        super(context);

        mFullGreen = new ImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawCircle(mW/2, mH/2, (float) (mW/2.0f), mPaintFingerprint);
            };
        };

        mWM = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        mDM = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);

        android.util.Log.d("PHH", "Samsung FOD " + samsungFod);

        mHandlerThread = new HandlerThread("FacolaThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        noDim = android.os.SystemProperties.getBoolean("persist.sys.phh.nodim", true);
        String[] location = android.os.SystemProperties.get("persist.vendor.sys.fp.fod.location.X_Y", "").split(",");
        if(location.length != 2)
            location = android.os.SystemProperties.get("persist.sys.fp.fod.location.X_Y", "").split(",");
        String[] size = android.os.SystemProperties.get("persist.vendor.sys.fp.fod.size.width_height", "").split(",");
        if(size.length != 2)
            size = android.os.SystemProperties.get("persist.sys.fp.fod.size.width_height", "").split(",");
        Slog.d("PHH-Enroll", "FacolaView hello");
        if(size.length == 2 && location.length == 2) {
            Slog.d("PHH-Enroll", "Got real values");
            mX = Integer.parseInt(location[0]);
            mY = Integer.parseInt(location[1]);
            mW = Integer.parseInt(size[0]);
            mH = Integer.parseInt(size[1]);
        } else {
            mX = -1;
            mY = -1;
            mW = -1;
            mH = -1;
        }

        int oppoSize = android.os.SystemProperties.getInt("persist.vendor.fingerprint.optical.iconsize", 0);
        if(oppoSize > 0) {
            mW = oppoSize;
            mH = oppoSize;
        }
        int oppoLocation = android.os.SystemProperties.getInt("persist.vendor.fingerprint.optical.iconlocation", 0);
        if(oppoLocation > 0) {
            Slog.d("PHH-Enroll", "Got Oppo icon location " + oppoLocation);
            Point p = new Point();
            mDM.getDisplay(0).getRealSize(p);
            Slog.d("PHH-Enroll", "\tscreen size " + p.x + ", " + p.y);
            mX = p.x/2 - mW/2;
            mY = p.y - mH/2 - oppoLocation;
            Slog.d("PHH-Enroll", "\tfacola at  " + mX + ", " + mY);
            noDim = true;
        }

        mPaintFingerprint.setAntiAlias(true);
        mPaintFingerprint.setColor(Color.GREEN);

        mPaintShow.setAntiAlias(true);
        mPaintShow.setColor(Color.argb(0x18, 0x00, 0xff, 0x00));
        setOnTouchListener(this);
        Slog.d("PHH-Enroll", "Created facola...");
        if(mW != -1) {
            try {
                mXiaomiFingerprint = IXiaomiFingerprint.getService();
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed getting xiaomi fingerprint service", e);
            }
            try {
                mGoodixFingerprint = IGoodixFPExtendService.getService();
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed getting goodix fingerprint service", e);
            }
            try {
                mSamsungFingerprint = ISecBiometricsFingerprint.getService();
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed getting samsung fingerprint service", e);
            }
            try {
                mOppoFingerprint = vendor.oppo.hardware.biometrics.fingerprint.V2_1.IBiometricsFingerprint.getService();
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed getting oppo fingerprint service", e);
            }
        }

        if(mX != -1) {
            android.os.SystemProperties.set("persist.sys.phh.has_fod", "true");
        } else {
            android.os.SystemProperties.set("persist.sys.phh.has_fod", "false");
        }
    }

    private final File oppoFod = new File("/sys/kernel/oppo_display/notify_fppress");
    private void oppoPress(boolean pressed) {
        if(!oppoFod.exists()) return;
        try {
            String v = "0";
            if(pressed) v = "1";
            PrintWriter writer = new PrintWriter(oppoFod, "UTF-8");
            writer.println(v);
            writer.close();
        } catch(Exception e) {
            Slog.d("PHH", "Failed to notify oppo fp press", e);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Slog.d("PHH-Enroll", "Drawing at " + mX + ", " + mY + ", " + mW + ", " + mH);
        //TODO w!=h?
        if(mInsideCircle) {
            try {
                mParamsTouched.x = mX;
                mParamsTouched.y = mY;

                mParamsTouched.height = mW;
                mParamsTouched.width = mH;
                mParamsTouched.format = PixelFormat.TRANSLUCENT;

                mParamsTouched.type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY;
                mParamsTouched.setTitle("Fingerprint on display.touched");
                mParamsTouched.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                mParamsTouched.dimAmount = UNTOUCHED_DIM;
                if(!noDim) {
                    mParamsTouched.dimAmount = TOUCHED_DIM;
                    mParamsTouched.screenBrightness = 1.0f;
                }

                mParamsTouched.packageName = "android";

                mParamsTouched.gravity = Gravity.TOP | Gravity.LEFT;
                if(!mFullGreenDisplayed && !mHidden) {
                    mHandler.post( () -> {
                        Slog.d("PHH-Enroll", "Adding full green because of finger pressed");
                        mFullGreenDisplayed = true;
                        mWM.addView(mFullGreen, mParamsTouched);
                    });
                }

                int nitValue = 2;
                mHandler.postDelayed( () -> {
                    try {
                        if(mXiaomiFingerprint != null) {
                            mXiaomiFingerprint.extCmd(0xa, nitValue);
                        } else if(mGoodixFingerprint != null) {
                            mGoodixFingerprint.goodixExtendCommand(10, 1);
                        } else if(mSamsungFingerprint != null) {
                            mSamsungFingerprint.request(22 /* SEM_FINGER_STATE */, 0, 2 /* pressed */, new java.util.ArrayList<Byte>(),
                                    (int retval, java.util.ArrayList<Byte> out) -> {} );
                        }
                    } catch(Exception e) {
                        Slog.d("PHH-Enroll", "Failed calling late fp extcmd", e);
                    }
               }, 200);
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed calling fp extcmd", e);
            }
            oppoPress(true);
        } else {
            oppoPress(false);
            try {
                if(mXiaomiFingerprint != null) {
                    mXiaomiFingerprint.extCmd(0xa, 0);
                } else if(mGoodixFingerprint != null) {
                    mGoodixFingerprint.goodixExtendCommand(10, 0);
                } else if(mSamsungFingerprint != null) {
                    mSamsungFingerprint.request(22 /* SEM_FINGER_STATE */, 0, 1 /* released */, new java.util.ArrayList<Byte>(),
                            (int retval, java.util.ArrayList<Byte> out) -> {} );
                }
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed calling fp extcmd", e);
            }
            if(mFullGreenDisplayed) {
                mHandler.post( () -> {
                    Slog.d("PHH-Enroll", "Removing full green because of finger released");
                    mFullGreenDisplayed = false;
                    mWM.removeView(mFullGreen);
                });
            }
        }
        canvas.drawCircle(mW/2, mH/2, (float) (mW/2.0f), this.mPaintShow);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        boolean newInside = (x > 0 && x < mW) && (y > 0 && y < mW);
        if(event.getAction() == MotionEvent.ACTION_UP)
            newInside = false;

        Slog.d("PHH-Enroll", "Got action " + event.getAction() + ", x = " + x + ", y = " + y + ", inside = " + mInsideCircle + "/" + newInside);
        if(newInside == mInsideCircle) return mInsideCircle;
        mInsideCircle = newInside;

        invalidate();

        if(!mInsideCircle) {
            mParams.screenBrightness = .0f;
            mParams.dimAmount = UNTOUCHED_DIM;
            mWM.updateViewLayout(this, mParams);
            return false;
        }

        if(!noDim) {
            mParams.dimAmount = TOUCHED_DIM;
            mParams.screenBrightness = 1.0f;
        }
        mWM.updateViewLayout(this, mParams);

        return true;
    }

    public void show() {
        Slog.d("PHH-Enroll", "Show", new Exception());
        if(!mHidden) return;
        mHidden = false;
        if(mOppoFingerprint != null) {
            try {
                mOppoFingerprint.setScreenState(vendor.oppo.hardware.biometrics.fingerprint.V2_1.FingerprintScreenState.FINGERPRINT_SCREEN_ON);
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed setting oppo screen state", e);
            }
        }
        mInsideCircle = false;
        writeFile("/sys/kernel/oppo_display/dimlayer_hbm", "1");
        if(samsungFod) {
            samsungCmd("fod_enable,1,1");
            samsungCmd("fod_enable,1,1,0");
        }
        if(mX == -1 || mY == -1 || mW == -1 || mH == -1) return;

        try {
            PrintWriter writer = new PrintWriter("/sys/devices/virtual/touch/tp_dev/fod_status", "UTF-8");
            writer.println("1");
            writer.close();
        } catch(Exception e) {
            Slog.d("PHH-Enroll", "Failed setting fod status for touchscreen");
        }

        mParams.x = mX;
        mParams.y = mY;

        mParams.height = mW;
        mParams.width = mH;
        mParams.format = PixelFormat.TRANSLUCENT;

        mParams.type = WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY;
        mParams.setTitle("Fingerprint on display");
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
            WindowManager.LayoutParams.FLAG_DIM_BEHIND |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mParams.dimAmount = UNTOUCHED_DIM;
        mParams.screenBrightness = .0f;

        mParams.packageName = "android";

        mParams.gravity = Gravity.TOP | Gravity.LEFT;
        mHandler.post( () -> {
            mWM.addView(this, mParams);
        });

    }

    public void hide() {
        mInsideCircle = false;
        Slog.d("PHH-Enroll", "Hide", new Exception());
        if(mHidden) return;
        if(mOppoFingerprint != null) {
            try {
                mOppoFingerprint.setScreenState(vendor.oppo.hardware.biometrics.fingerprint.V2_1.FingerprintScreenState.FINGERPRINT_SCREEN_ON);
            } catch(Exception e) {
                Slog.d("PHH-Enroll", "Failed setting oppo screen state", e);
            }
        }
        writeFile("/sys/kernel/oppo_display/dimlayer_hbm", "0");
        mHidden = true;
        if(mFullGreenDisplayed) {
            Slog.d("PHH-Enroll", "Removing full green because of hide");
            mFullGreenDisplayed = false;
            mWM.removeView(mFullGreen);
        }
        if(samsungFod) {
            samsungCmd("fod_enable,0");
        }
        if(mX == -1 || mY == -1 || mW == -1 || mH == -1) return;

        try {
            if(mXiaomiFingerprint != null) {
                mXiaomiFingerprint.extCmd(0xa, 0);
            } else if(mGoodixFingerprint != null) {
                mXiaomiFingerprint.extCmd(10, 0);
            }
        } catch(Exception e) {
            Slog.d("PHH-Enroll", "Failed calling xiaomi fp extcmd");
        }
        try {
            PrintWriter writer = new PrintWriter("/sys/devices/virtual/touch/tp_dev/fod_status", "UTF-8");
            writer.println("0");
            writer.close();
        } catch(Exception e) {
            Slog.d("PHH-Enroll", "Failed setting fod status for touchscreen");
        }

        Slog.d("PHH-Enroll", "Removed facola");
        mHandler.post( () -> {
            mWM.removeView(this);
        });
    }

    private static boolean samsungHasCmd(String cmd) {
        try {
            File f = new File("/sys/devices/virtual/sec/tsp/cmd_list");
            if(!f.exists()) return false;

            BufferedReader b = new BufferedReader(new FileReader(f));
            String line = null;
            while( (line = b.readLine()) != null) {
                if(line.equals(cmd)) return true;
            }
            return false;
        } catch(Exception e) {
            return false;
        }
    }

    private static String readFile(String path) {
        try {
            File f = new File(path);

            BufferedReader b = new BufferedReader(new FileReader(f));
            return b.readLine();
        } catch(Exception e) {
            return null;
        }
    }

    private static void samsungCmd(String cmd) {
        try {
            PrintWriter writer = new PrintWriter("/sys/devices/virtual/sec/tsp/cmd", "UTF-8");
            writer.println(cmd);
            writer.close();

            String status = readFile("/sys/devices/virtual/sec/tsp/cmd_status");
            String ret = readFile("/sys/devices/virtual/sec/tsp/cmd_result");

            android.util.Log.d("PHH", "Sending command " + cmd + " returned " + ret + ":" + status);
        } catch(Exception e) {
            android.util.Log.d("PHH", "Failed sending command " + cmd, e);
        }
    }

    private static void writeFile(String path, String value) {
        try {
            PrintWriter writer = new PrintWriter(path, "UTF-8");
            writer.println(value);
            writer.close();
        } catch(Exception e) {
            android.util.Log.d("PHH", "Failed writing to " + path + ": " + value);
        }
    }

}

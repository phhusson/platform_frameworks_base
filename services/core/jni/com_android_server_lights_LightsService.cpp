/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "LightsService"

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include "android_runtime/AndroidRuntime.h"

#include <android/hardware/light/2.0/ILight.h>
#include <android/hardware/light/2.0/types.h>
#include <vendor/samsung/hardware/light/2.0/ISecLight.h>
#include <vendor/samsung/hardware/light/2.0/types.h>
#include <vendor/samsung/hardware/light/3.0/ISehLight.h>
#include <vendor/samsung/hardware/light/3.0/types.h>
#include <vendor/huawei/hardware/light/2.0/ILight.h>
#include <android-base/chrono_utils.h>
#include <utils/misc.h>
#include <utils/Log.h>
#include <map>
#include <stdio.h>

namespace android {

using Brightness = ::android::hardware::light::V2_0::Brightness;
using Flash      = ::android::hardware::light::V2_0::Flash;
using ILight     = ::android::hardware::light::V2_0::ILight;
using LightState = ::android::hardware::light::V2_0::LightState;
using Status     = ::android::hardware::light::V2_0::Status;
using Type       = ::android::hardware::light::V2_0::Type;
template<typename T>
using Return     = ::android::hardware::Return<T>;

using ISecLight  = ::vendor::samsung::hardware::light::V2_0::ISecLight;
using SecType    = ::vendor::samsung::hardware::light::V2_0::SecType;
using ISehLight  = ::vendor::samsung::hardware::light::V3_0::ISehLight;
using SehType    = ::vendor::samsung::hardware::light::V3_0::SehType;
using SehLightState = ::vendor::samsung::hardware::light::V3_0::SehLightState;
using ILightHw   = ::vendor::huawei::hardware::light::V2_0::ILight;
using LightStateHw = ::android::hardware::light::V2_0::LightState;
static bool sLightSupported = true;

static sp<ISecLight> sSecHal;
static sp<ISehLight> sSehHal;
static bool sSecTried = false;
static sp<ILightHw> sHwHal;
static bool sHwTried = false;

static bool validate(jint light, jint flash, jint brightness) {
    bool valid = true;

    if (light < 0 || light >= static_cast<jint>(Type::COUNT)) {
        ALOGE("Invalid light parameter %d.", light);
        valid = false;
    }

    if (flash != static_cast<jint>(Flash::NONE) &&
        flash != static_cast<jint>(Flash::TIMED) &&
        flash != static_cast<jint>(Flash::HARDWARE)) {
        ALOGE("Invalid flash parameter %d.", flash);
        valid = false;
    }

    if (brightness != static_cast<jint>(Brightness::USER) &&
        brightness != static_cast<jint>(Brightness::SENSOR) &&
        brightness != static_cast<jint>(Brightness::LOW_PERSISTENCE)) {
        ALOGE("Invalid brightness parameter %d.", brightness);
        valid = false;
    }

    if (brightness == static_cast<jint>(Brightness::LOW_PERSISTENCE) &&
        light != static_cast<jint>(Type::BACKLIGHT)) {
        ALOGE("Cannot set low-persistence mode for non-backlight device.");
        valid = false;
    }

    return valid;
}

static LightState constructState(
        jint colorARGB,
        jint flashMode,
        jint onMS,
        jint offMS,
        jint brightnessMode){
    Flash flash = static_cast<Flash>(flashMode);
    Brightness brightness = static_cast<Brightness>(brightnessMode);

    LightState state{};

    if (brightness == Brightness::LOW_PERSISTENCE) {
        state.flashMode = Flash::NONE;
    } else {
        // Only set non-brightness settings when not in low-persistence mode
        state.flashMode = flash;
        state.flashOnMs = onMS;
        state.flashOffMs = offMS;
    }

    state.color = colorARGB;
    state.brightnessMode = brightness;

    return state;
}

static void processReturn(
        const Return<Status> &ret,
        Type type,
        const LightState &state) {
    if (!ret.isOk()) {
        ALOGE("Failed to issue set light command.");
        return;
    }

    switch (static_cast<Status>(ret)) {
        case Status::SUCCESS:
            break;
        case Status::LIGHT_NOT_SUPPORTED:
            ALOGE("Light requested not available on this device. %d", type);
            break;
        case Status::BRIGHTNESS_NOT_SUPPORTED:
            ALOGE("Brightness parameter not supported on this device: %d",
                state.brightnessMode);
            break;
        case Status::UNKNOWN:
        default:
            ALOGE("Unknown error setting light.");
    }
}

static void setLight_native(
        JNIEnv* /* env */,
        jobject /* clazz */,
        jint light,
        jint colorARGB,
        jint flashMode,
        jint onMS,
        jint offMS,
        jint brightnessMode) {

    if (!sLightSupported) {
        return;
    }

    if (!validate(light, flashMode, brightnessMode)) {
        return;
    }

    if(!sSecTried) {
        sSecHal = ISecLight::getService();
        sSehHal = ISehLight::getService();
        //sSecTried = true;
    }

    if(sSecHal != nullptr) {
        SecType type = static_cast<SecType>(light);
        LightState state = constructState(
                colorARGB, flashMode, onMS, offMS, brightnessMode);

        {
            android::base::Timer t;
            Return<Status> ret = sSecHal->setLightSec(type, state);
            processReturn(ret, static_cast<Type>(light), state);
            if (t.duration() > 50ms) ALOGD("Excessive delay setting light");
        }
	return;
    }

    if(sSehHal != nullptr && light == 0 && flashMode == static_cast<jint>(Flash::HARDWARE)) {
        SehType type = static_cast<SehType>(light);
        SehLightState state {};
        state.flashMode = Flash::NONE;
	Brightness brightness = static_cast<Brightness>(brightnessMode);
	state.brightnessMode = brightness;
	state.extendedBrightness = colorARGB;

        {
            android::base::Timer t;
            Return<Status> ret = sSehHal->sehSetLight(type, state);
	    if(!ret.isOk()) {
		    ALOGE("Failed to issue set light command.");
	    }
            if (t.duration() > 50ms) ALOGD("Excessive delay setting light");
        }
	return;
    }

    if (!sHwTried) {
        sHwHal = ILightHw::getService();
        //sHwTried = true;
    }

    if (sHwHal != nullptr && light == 0) {
        ALOGE("sHwHal triggered!");
        int brightness = colorARGB & 0xff;
        int hwBrightness = brightness << 4;
        LightState state = constructState(hwBrightness, flashMode, onMS, offMS, brightnessMode);
        bool got260 = false;
        sHwHal->HWgetSupportedTypes([&](auto types) {
            for (const auto& type: types) {
                if (type == 260) {
                    ALOGE("sHwHal reports 260 as a supported type");
                    got260 = true;
                }
            }
        });
        if (got260) {
            sHwHal->HWsetLight(260, state);
            return;
        }
    }

    Type type = static_cast<Type>(light);
    LightState state = constructState(
        colorARGB, flashMode, onMS, offMS, brightnessMode);

    {
        android::base::Timer t;
        sp<ILight> hal = ILight::getService();
        if (hal == nullptr) {
            sLightSupported = false;
            return;
        }
        Return<Status> ret = hal->setLight(type, state);
        processReturn(ret, type, state);
        if (t.duration() > 50ms) ALOGD("Excessive delay setting light");
    }
}

static const JNINativeMethod method_table[] = {
    { "setLight_native", "(IIIIII)V", (void*)setLight_native },
};

int register_android_server_LightsService(JNIEnv *env) {
    return jniRegisterNativeMethods(env, "com/android/server/lights/LightsService",
            method_table, NELEM(method_table));
}

};

/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.Rlog;

/**
 * Immutable cell information from a point in time.
 */
public final class CellInfoGsm extends CellInfo implements Parcelable {

    private static final String LOG_TAG = "CellInfoGsm";
    private static final boolean DBG = false;

    private CellIdentityGsm mCellIdentityGsm;
    private CellSignalStrengthGsm mCellSignalStrengthGsm;
    private String mOriginalOperatorNumeric;

    /** @hide */
    public CellInfoGsm() {
        super();
        mCellIdentityGsm = new CellIdentityGsm();
        mCellSignalStrengthGsm = new CellSignalStrengthGsm();
        mOriginalOperatorNumeric = "";
    }

    /** @hide */
    public CellInfoGsm(CellInfoGsm ci) {
        super(ci);
        this.mCellIdentityGsm = ci.mCellIdentityGsm.copy();
        this.mCellSignalStrengthGsm = ci.mCellSignalStrengthGsm.copy();
        this.mOriginalOperatorNumeric = ci.mOriginalOperatorNumeric;
    }

    public CellIdentityGsm getCellIdentity() {
        return mCellIdentityGsm;
    }
    /** @hide */
    public void setCellIdentity(CellIdentityGsm cid) {
        mCellIdentityGsm = cid;
    }

    public CellSignalStrengthGsm getCellSignalStrength() {
        return mCellSignalStrengthGsm;
    }
    /** @hide */
    public void setCellSignalStrength(CellSignalStrengthGsm css) {
        mCellSignalStrengthGsm = css;
    }

    /** @hide */
    public String getOriginalOperatorNumeric() {
        return mOriginalOperatorNumeric;
    }
    /** @hide */
    public void setOriginalOperatorNumeric(String operatorNumeric) {
        mOriginalOperatorNumeric = operatorNumeric;
    }

    /**
     * @return hash code
     */
    @Override
    public int hashCode() {
        return super.hashCode() + mCellIdentityGsm.hashCode() + mCellSignalStrengthGsm.hashCode() + mOriginalOperatorNumeric.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }
        try {
            CellInfoGsm o = (CellInfoGsm) other;
            return mCellIdentityGsm.equals(o.mCellIdentityGsm)
                    && mCellSignalStrengthGsm.equals(o.mCellSignalStrengthGsm)
                    && mOriginalOperatorNumeric.equals(o.mOriginalOperatorNumeric);
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("CellInfoGsm:{");
        sb.append(super.toString());
        sb.append(" ").append(mCellIdentityGsm);
        sb.append(" ").append(mCellSignalStrengthGsm);
        sb.append(" mOriginalOperatorNumeric=").append(mOriginalOperatorNumeric);
        sb.append("}");

        return sb.toString();
    }

    /** Implement the Parcelable interface */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags, TYPE_GSM);
        mCellIdentityGsm.writeToParcel(dest, flags);
        mCellSignalStrengthGsm.writeToParcel(dest, flags);
        dest.writeString(mOriginalOperatorNumeric);
    }

    /**
     * Construct a CellInfoGsm object from the given parcel
     * where the token is already been processed.
     */
    private CellInfoGsm(Parcel in) {
        super(in);
        mCellIdentityGsm = CellIdentityGsm.CREATOR.createFromParcel(in);
        mCellSignalStrengthGsm = CellSignalStrengthGsm.CREATOR.createFromParcel(in);
        mOriginalOperatorNumeric = in.readString();
    }

    /** Implement the Parcelable interface */
    public static final Creator<CellInfoGsm> CREATOR = new Creator<CellInfoGsm>() {
        @Override
        public CellInfoGsm createFromParcel(Parcel in) {
            in.readInt(); // Skip past token, we know what it is
            return createFromParcelBody(in);
        }

        @Override
        public CellInfoGsm[] newArray(int size) {
            return new CellInfoGsm[size];
        }
    };

    /** @hide */
    protected static CellInfoGsm createFromParcelBody(Parcel in) {
        return new CellInfoGsm(in);
    }

    /**
     * log
     */
    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}

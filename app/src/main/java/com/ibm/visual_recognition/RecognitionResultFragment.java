package com.ibm.visual_recognition;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.flexbox.FlexboxLayout;

public class RecognitionResultFragment extends Fragment {
//Sonucu göstermek için fragment
    private LinearLayout retainedLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (retainedLayout == null) {
            LinearLayout resultView = (LinearLayout)inflater.inflate(R.layout.recognition_result_view, container, false);
            return resultView;
        }

        else {
            return retainedLayout;
        }
    }

    public void saveData() {
        retainedLayout = (LinearLayout) getView();
    }
}
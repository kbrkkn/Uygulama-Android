package com.ibm.visual_recognition;

import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;

//Visual Recognition Service'inden dönen sonucu tutar ve yüzdesi en yüksek olanı bulmak için
// bu nesnenin getter metotlarıyla RecognitionResultBuilder'a gönderilir.
public class ClassifyTaskResult {
    private final VisualClassification visualClassification;
    private final VisualClassification colorClassification;

    ClassifyTaskResult (VisualClassification vcIn, VisualClassification colorClassification) {
        visualClassification = vcIn;
        this.colorClassification=colorClassification;        }

    VisualClassification getVisualClassification() { return visualClassification;}
    VisualClassification getColorClassification() { return colorClassification;}

}
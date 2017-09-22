package com.ibm.visual_recognition;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectedFaces;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.Face;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ImageClassification;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ImageFace;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


//Bu sınıf Visual Recognition'dan aldığı sonuçları çevirilerinin yapılması ve seslendirilmesi için MainActivity'ye gönderir.

class RecognitionResultBuilder {
    private String renk,kiyafet;
//Bu setter ve getter'lar sayesinde dönen sonuçlar MainAct'a gönderilir.
    public String getRenk() {
        return renk;
    }

    public void setRenk(String renk) {
        this.renk = renk;
    }

    public String getKiyafet() {
        return kiyafet;
    }

    public void setKiyafet(String kiyafet) {
        this.kiyafet = kiyafet;
    }

    private final MainActivity context;

    //Constructor
    RecognitionResultBuilder(MainActivity context) {
        this.context = context;
    }


    LinearLayout buildRecognitionResultView(VisualClassification visualClassification, VisualClassification colorClassification) {
        LinearLayout recognitionLayout = new LinearLayout(context);
        recognitionLayout.setOrientation(LinearLayout.VERTICAL);

//Kıyafet sonuçlarının hangi tür olduğu sonuçları burada elde edilir.
        List<ImageClassification> classifications = visualClassification.getImages();
        ArrayList<Model> kiyafetler=new ArrayList<Model>();

        for (int i = 0; i < classifications.size(); i++) {
            List<VisualClassifier> classifiers = classifications.get(i).getClassifiers();
            if (classifiers == null) break;
            for (int j = 0; j < classifiers.size(); j++) {
                List<VisualClassifier.VisualClass> visualClasses = classifiers.get(j).getClasses();
                if (visualClasses == null) break;
                for (VisualClassifier.VisualClass visualClass : visualClasses) {
                    kiyafetler.add(new Model(visualClass.getName(),visualClass.getScore()));
                    Log.i("kıyafetler",visualClass.getName()+" "+visualClass.getScore());

                }
            }
        }
//En yüksek ihtimali olan kıyafet seçilir.
        Model secilenKiyafet=kiyafetler.get(0);
        for(int a=0;a<kiyafetler.size()-1;a++)
        {
            if(kiyafetler.size()>1){
                if(kiyafetler.get(a+1).getScore()>secilenKiyafet.getScore())
                {
                    secilenKiyafet=kiyafetler.get(a+1);
                }

            }}
       setKiyafet(secilenKiyafet.getModelName());

//Kıyafetin rengi buradan alınır.
        List<ImageClassification> classificationsColor = colorClassification.getImages();
        ArrayList<Model>colors=new ArrayList<Model>();
        for (int i = 0; i < classificationsColor.size(); i++) {
            List<VisualClassifier> classifiers = classificationsColor.get(i).getClassifiers();
            if (classifiers == null) break;
            for (int j = 0; j < classifiers.size(); j++) {
                List<VisualClassifier.VisualClass> visualClasses = classifiers.get(j).getClasses();
                if (visualClasses == null) break;
                for (VisualClassifier.VisualClass visualClass : visualClasses) {

                    // String formattedScore = String.format(Locale.US, "%.0f", visualClass.getScore() * 100) + "%";
                    if(visualClass.getName().contains("color")){
                        colors.add(new Model(visualClass.getName(),visualClass.getScore()));
                        Log.i("renkler",visualClass.getName()+" "+visualClass.getScore()+" ");
                    }

                }
            }
        }
        //En yüksek ihtimali olan renk seçilir.
        Model selectedModel=colors.get(0);
        for(int i=0;i<colors.size()-1;i++){
            if(colors.size()>1){
                if(colors.get(i+1).getScore()>selectedModel.getScore()){
                    selectedModel=colors.get(i+1);
                }

            }
        }
//IBM'in var olan renkleri kullandı.
// Bunlar bir veye birden fazla kelimeden oluşuyor ve sonuna color kelimesi alıyor.Burada color'dan bir önceki kelimeyi seçtik.
       String[]colorArr= selectedModel.getModelName().split(" ");
       setRenk(colorArr[colorArr.length-2]);

        return recognitionLayout;
    }


}
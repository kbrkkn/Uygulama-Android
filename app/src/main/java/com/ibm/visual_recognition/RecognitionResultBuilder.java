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

/**
 * Class used to construct a UI to deliver information received from Visual Recognition to the user.
 */
class RecognitionResultBuilder {
    private String renk,kiyafet;

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

    RecognitionResultBuilder(MainActivity context) {
        this.context = context;
    }

    /**
     * Dynamically constructs a LinearLayout with information from Visual Recognition.
     * @return A LinearLayout with a dynamic number image_tag.xml
     */
    LinearLayout buildRecognitionResultView(VisualClassification visualClassification, VisualClassification colorClassification) {
        LinearLayout recognitionLayout = new LinearLayout(context);
        recognitionLayout.setOrientation(LinearLayout.VERTICAL);

        FlexboxLayout imageTagContainer = (FlexboxLayout)context.getLayoutInflater().inflate(R.layout.tag_box, null);

        // Next process general classification data from Visual Recognition and create image tags for each visual class.
        List<ImageClassification> classifications = visualClassification.getImages();
        ArrayList<Model> kiyafetler=new ArrayList<Model>();

        for (int i = 0; i < classifications.size(); i++) {
            List<VisualClassifier> classifiers = classifications.get(i).getClassifiers();
            if (classifiers == null) break;
            for (int j = 0; j < classifiers.size(); j++) {
                List<VisualClassifier.VisualClass> visualClasses = classifiers.get(j).getClasses();
                if (visualClasses == null) break;
                for (VisualClassifier.VisualClass visualClass : visualClasses) {
                    //  String formattedScore = String.format(Locale.US, "%.0f", visualClass.getScore() * 100) + "%";
                    kiyafetler.add(new Model(visualClass.getName(),visualClass.getScore()));
                 //   speechTex+=visualClass.getName()+" ";
                    Log.i("kıyafetler",visualClass.getName()+" "+visualClass.getScore());

                    //      imageTagContainer.addView(constructImageTag(context.getLayoutInflater(), visualClass.getName(), formattedScore));
                }
            }
        }

        Model secilenKiyafet=kiyafetler.get(0);
        for(int a=0;a<kiyafetler.size()-1;a++)
        {
            if(kiyafetler.size()>1){
                if(kiyafetler.get(a+1).getScore()>secilenKiyafet.getScore())
                {

                    secilenKiyafet=kiyafetler.get(a+1);
                }

            }}
      //  imageTagContainer.addView(constructImageTag(context.getLayoutInflater(), secilenKiyafet.getModelName(), secilenKiyafet.getScore()+""));
       setKiyafet(secilenKiyafet.getModelName());
        // Next process general classification data from Visual Recognition and create image tags for each visual class.
        List<ImageClassification> classificationsColor = colorClassification.getImages();
        ArrayList<Model>colors=new ArrayList<Model>();
        String color=" ";
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

        Model selectedModel=colors.get(0);

        for(int i=0;i<colors.size()-1;i++){
            if(colors.size()>1){
                if(colors.get(i+1).getScore()>selectedModel.getScore()){
                    selectedModel=colors.get(i+1);
                }

            }
        }
    //    imageTagContainer.addView(constructImageTag(context.getLayoutInflater(),selectedModel.getModelName(),selectedModel.getScore()+""));

       String[]colorArr= selectedModel.getModelName().split(" ");
       setRenk(colorArr[colorArr.length-2]);
        // If parsing through Visual Recognition's return has resulted in no image tags, create an "Unknown" tag.
     /*   if (imageTagContainer.getChildCount() <= 0) {
            imageTagContainer.addView(constructImageTag(context.getLayoutInflater(), "Unknown", "N/A"));
        }
*/
       // recognitionLayout.addView(imageTagContainer);

        return recognitionLayout;
    }

    /**
     * Creates a TextView image tag with a name and score to be displayed to the user.
     * @param inflater Layout inflater to access R.layout.image_tag.
     * @param tagName Name of the tag to be displayed.
     * @param tagScore Certainty score of the tag, to be displayed when the user clicks the tag.
     * @return A TextView representation of the image tag.
     */
    static TextView constructImageTag(LayoutInflater inflater, final String tagName, final String tagScore) {
        TextView imageTagView = (TextView)inflater.inflate(R.layout.image_tag, null);
        imageTagView.setText(tagName);

        // Set an onclick listener that gives each image tag a toggle between its name and its score.
        imageTagView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView label = (TextView)v;
                String currentText = label.getText().toString();

                if (currentText.equals(tagName)) {
                    label.setMinWidth(label.getWidth());
                    label.setText(tagScore);
                } else {
                    label.setText(tagName);
                }
            }
        });

        return imageTagView;
    }


}
package com.ibm.visual_recognition;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.Voice;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.watson.developer_cloud.service.exception.ForbiddenException;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyImagesOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectedFaces;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualRecognitionOptions;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;

import org.apache.commons.io.IOUtils;

public class MainActivity extends AppCompatActivity implements OnInitListener {
    RecognitionResultBuilder resultBuilder;
    private static final String STATE_IMAGE = "image";
    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_GALLERY = 2;

    // Visual Recognition Service has a maximum file size limit that we control by limiting the size of the image.
    private static final float MAX_IMAGE_DIMENSION = 1200;
    private VisualRecognition visualService;
    private RecognitionResultFragment resultFragment;

    private String mSelectedImageUri = null;
    private File output = null;
    private TextToSpeech mTts;
    private boolean ready=false;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, 100);


        ImageView selectedImageView = (ImageView) findViewById(R.id.selectedImageView);

        // Set and create temp storage for camera to utilize when taking a picture
        if (savedInstanceState == null) {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
            dir.mkdirs();
            output = new File(dir, "mCameraContent.jpeg");
        } else {
            output = (File)savedInstanceState.getSerializable("com.ibm.visual_recognition.EXTRA_FILENAME");
        }

        // Using a retained fragment to hold our result from the Recognition Service, create if it doesn't exist.
        resultFragment = (RecognitionResultFragment)getSupportFragmentManager().findFragmentByTag("result");
        if (resultFragment == null) {
            resultFragment = new RecognitionResultFragment();
            resultFragment.setRetainInstance(true);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, resultFragment, "result").commit();
        }
        // While the fragment retains the result from Recognition, we need to handle the selected image ourselves.
        if (savedInstanceState != null) {
            mSelectedImageUri = savedInstanceState.getString(STATE_IMAGE);

            // Re-fetch the selected Bitmap from its Uri, or if null, restore the default image.
            if (mSelectedImageUri != null) {
                Uri imageUri = Uri.parse(mSelectedImageUri);
                Bitmap selectedImage = fetchBitmapFromUri(imageUri);

                selectedImageView.setImageBitmap(selectedImage);

            } else {
                selectedImageView.setImageDrawable(ContextCompat.getDrawable(this, R.mipmap.bend));
            }
        }


       // ImageButton cameraButton = (ImageButton) findViewById(R.id.cameraButton);
        selectedImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, REQUEST_CAMERA);
            }
        });

        ImageButton galleryButton = (ImageButton) findViewById(R.id.galleryButton);
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("onclick","basıldı");
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, REQUEST_GALLERY);
            }
        });

        // Core SDK must be initialized to interact with Bluemix Mobile services.
        BMSClient.getInstance().initialize(getApplicationContext(), BMSClient.REGION_UK);



        visualService = new VisualRecognition(VisualRecognition.VERSION_DATE_2016_05_20,
                getString(R.string.visualrecognitionApi_key));


        // Immediately on start attempt to validate the user's credentials from credentials.xml.
        ValidateCredentialsTask vct = new ValidateCredentialsTask();
        vct.execute();
    }

    @Override
    public void onResume() {
        super.onResume();



    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onDestroy() {
        // Have the fragment save its state for recreation on orientation changes.
        resultFragment.saveData();
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the URI of the currently selected image for recreation.
        savedInstanceState.putString(STATE_IMAGE, mSelectedImageUri);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                //   Toast.makeText(this,"success, create the TTS instance",Toast.LENGTH_SHORT).show();

                // success, create the TTS instance
                mTts = new TextToSpeech(this,this);

            } else {
                //   Toast.makeText(this," missing data, install it",Toast.LENGTH_SHORT).show();
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            if(requestCode == REQUEST_CAMERA){
                ImageView imageView = (ImageView) findViewById(R.id.selectedImageView);

                Bitmap photo = (Bitmap) data.getExtras().get("data");
                imageView.setImageBitmap(photo);

                // Resize the Bitmap to constrain within Watson Image Recognition's Size Limit.
                photo = resizeBitmapForWatson(photo, MAX_IMAGE_DIMENSION);


                //ONINIT İ BEKLE.
                // Send the resized, rotated, bitmap to the Classify Task for Classification.
                ClassifyTask ct = new ClassifyTask();


                synchronized(ct){
                    while (!ready){
                        try {
                            ct.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    ct.notifyAll();
                    ct.execute(photo);

                }
            }
            if (requestCode == REQUEST_GALLERY ) {
                Uri uri = data.getData();

                // Create uri from temp storage if returned intent could not gather Uri
                if (uri == null) {
                  //  uri = Uri.fromFile(output);
                    uri = FileProvider.getUriForFile(
                            this,
                            getApplicationContext()
                                    .getPackageName() + ".provider", output);
                }

                mSelectedImageUri = uri.toString();

                // Fetch the Bitmap from the Uri.
                Bitmap selectedImage = fetchBitmapFromUri(uri);

                // Set the UI's Bitmap with the full-sized, rotated Bitmap.
                ImageView resultImage = (ImageView) findViewById(R.id.selectedImageView);
                resultImage.setImageBitmap(selectedImage);

                // Resize the Bitmap to constrain within Watson Image Recognition's Size Limit.
                selectedImage = resizeBitmapForWatson(selectedImage, MAX_IMAGE_DIMENSION);


                //ONINIT İ BEKLE.
                // Send the resized, rotated, bitmap to the Classify Task for Classification.
                ClassifyTask ct = new ClassifyTask();


                synchronized(ct){
                    while (!ready){
                        try {
                            ct.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    ct.notifyAll();
                    ct.execute(selectedImage);

                }


            }
        }
    }

    /**
     * Displays an AlertDialogFragment with the given parameters.
     * @param errorTitle Error Title from values/strings.xml.
     * @param errorMessage Error Message either from values/strings.xml or response from server.
     * @param canContinue Whether the application can continue without needing to be rebuilt.
     */
    private void showDialog(int errorTitle, String errorMessage, boolean canContinue) {
        DialogFragment newFragment = AlertDialogFragment.newInstance(errorTitle, errorMessage, canContinue);
        newFragment.show(getFragmentManager(), "dialog");
    }

    @Override
    public void onInit(int status) {
        if(status==TextToSpeech.SUCCESS){
            ready=true;
            mTts.setLanguage(Locale.getDefault());//telefonun dili neyse o
            //  Toast.makeText(this,"başarılı tts",Toast.LENGTH_SHORT).show();
            Log.i("tts","tts success");


        }

        else if(status==TextToSpeech.ERROR){
            ready=false;
            //   Toast.makeText(this,"error tts",Toast.LENGTH_SHORT).show();
            Log.e("tts","tts error");

        }
    }

    /**
     * Asynchronously contacts the Visual Recognition Service to see if provided Credentials are valid.
     */
    private class ValidateCredentialsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            // Check to see if the user's credentials are valid or not along with other errors.

            try {
                visualService.getClassifiers().execute();

            }
            catch (Exception ex) {
                if (ex.getClass().equals(ForbiddenException.class))  {

                    showDialog(R.string.error_title_invalid_credentials,
                            "ForbiddenException", false);
                }
                else if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                }
                else if(ex.getClass().equals(IllegalArgumentException.class)){
                    showDialog(R.string.error_title_invalid_credentials,
                            "IllegalArgumentException", false);
                }
                else {
                    Log.e("hata",ex.getMessage());
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                    ex.printStackTrace();
                }
            }
            return null;
        }
    }

    /**
     * Asynchronously sends the selected image to Visual Recognition for Classification then passes the
     * result to our RecognitionResultBuilder to display to the user.
     */
    private class ClassifyTask extends AsyncTask<Bitmap, Void, ClassifyTaskResult> {

        @Override
        protected void onPreExecute() {
            ProgressBar progressSpinner = (ProgressBar)findViewById(R.id.loadingSpinner);
            progressSpinner.setVisibility(View.VISIBLE);

            // Clear the current image tags from our result layout.
            LinearLayout resultLayout = (LinearLayout) findViewById(R.id.recognitionResultLayout);
            resultLayout.removeAllViews();
        }

        @Override
        protected ClassifyTaskResult doInBackground(Bitmap... params) {
            Bitmap createdPhoto = params[0];

            // Reformat Bitmap into a .jpg and save as file to input to Watson.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            createdPhoto.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

            try {
                File tempPhoto = File.createTempFile("photo", ".jpg", getCacheDir());
                FileOutputStream out = new FileOutputStream(tempPhoto);
                out.write(bytes.toByteArray());
                out.close();

                // Two different service calls for objects and for faces.

                //CURL'DE EĞİTTİM CUSTOM CLASSIFIER ID Yİ AŞAĞIYA YAZDIM.

                ClassifyImagesOptions classifyImagesOptions = new ClassifyImagesOptions.Builder().classifierIds("giysiler_1890457300").images(tempPhoto).build();

                ClassifyImagesOptions colorOptions = new ClassifyImagesOptions.Builder().images(tempPhoto).build();

                VisualClassification classification = visualService.classify(classifyImagesOptions).execute();
                VisualClassification colorClassification = visualService.classify(colorOptions).execute();

                ClassifyTaskResult result = new ClassifyTaskResult(classification, colorClassification);

                tempPhoto.delete();

                return result;
            } catch (Exception ex) {
                if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                    showDialog(R.string.error_title_bluemix_connection,
                            getString(R.string.error_message_bluemix_connection), true);
                } else {
                    showDialog(R.string.error_title_default, ex.getMessage(), true);
                    ex.printStackTrace();
                }
                return null;
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected void onPostExecute(ClassifyTaskResult result) {
            ProgressBar progressSpinner = (ProgressBar)findViewById(R.id.loadingSpinner);
            progressSpinner.setVisibility(View.GONE);

            if (result != null) {
                // If not null send the full result from ToneAnalyzer to our UI Builder class.
                resultBuilder = new RecognitionResultBuilder(MainActivity.this);
                LinearLayout resultLayout = (LinearLayout) findViewById(R.id.recognitionResultLayout);

                if(resultLayout != null){
                    resultLayout.removeAllViews();
                }
                LinearLayout recognitionView = resultBuilder.buildRecognitionResultView(result.getVisualClassification(), result.getColorClassification());
                new TranslatorBackgroundTask(MainActivity.this,"trnsl.1.1.20170826T124332Z.c7f36074597a666f.f831f314a08423422cd841afca69af8e4a869564").execute(resultBuilder.getRenk(),"en-tr");
                resultLayout.addView(recognitionView);





            }
        }
    }

    /**
     * Holds our output data from the Visual Recognition Service Calls to be passed to onPostExecute.
     */
    private class ClassifyTaskResult {
        private final VisualClassification visualClassification;
        private final VisualClassification colorClassification;

        ClassifyTaskResult (VisualClassification vcIn, VisualClassification colorClassification) {
            visualClassification = vcIn;
            this.colorClassification=colorClassification;        }

        VisualClassification getVisualClassification() { return visualClassification;}
        VisualClassification getColorClassification() { return colorClassification;}
    }

    /**
     * Fetches a bitmap image from the device given the image's uri.
     * @param imageUri Uri of the image on the device (either in the gallery or from the camera).
     * @return A Bitmap representation of the image on the device, correctly orientated.
     */
    private Bitmap fetchBitmapFromUri(Uri imageUri) {
        try {
            // Fetch the Bitmap from the Uri.
            Bitmap selectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

            // Fetch the orientation of the Bitmap in storage.
            String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
            Cursor cursor = getContentResolver().query(imageUri, orientationColumn, null, null, null);
            int orientation = 0;
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(cursor.getColumnIndex(orientationColumn[0]));
            }
            if(cursor != null) {
                cursor.close();
            }

            // Rotate the bitmap with the found orientation.
            Matrix matrix = new Matrix();
            matrix.setRotate(orientation);
            selectedImage = Bitmap.createBitmap(selectedImage, 0, 0, selectedImage.getWidth(), selectedImage.getHeight(), matrix, true);

            return selectedImage;

        } catch (IOException e) {
            showDialog(R.string.error_title_default, e.getMessage(), true);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Scales the given image to an image that fits within the size constraints placed by Visual Recognition.
     * @param originalImage Full-sized Bitmap to be scaled.
     * @param maxSize The maximum allowed dimension of the image.
     * @return The original image rescaled so that it's largest dimension is equal to maxSize
     */
    private Bitmap resizeBitmapForWatson(Bitmap originalImage, float maxSize) {

        int originalHeight = originalImage.getHeight();
        int originalWidth = originalImage.getWidth();

        int boundingDimension = (originalHeight > originalWidth) ? originalHeight : originalWidth;

        float scale = maxSize / boundingDimension;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        originalImage = Bitmap.createBitmap(originalImage, 0, 0, originalWidth, originalHeight, matrix, true);

        return originalImage;
    }

    class TranslatorBackgroundTask extends AsyncTask<String, Void, String> {
        private String yandexKey;

        //Declare Context
        Context ctx;
        //Set Context
        TranslatorBackgroundTask(Context ctx,String yandexKey){
            this.ctx = ctx;
            this.yandexKey=yandexKey;
        }

        @Override
        protected String doInBackground(String... params) {
            //String variables
            String textToBeTranslated = params[0];

            String languagePair = params[1];

            String jsonString;

            try {
                //Set up the translation call URL
                String yandexUrl = "https://translate.yandex.net/api/v1.5/tr.json/translate?key=" + yandexKey
                        + "&text=" + textToBeTranslated + "&lang=" + languagePair;
                Log.i("url",yandexUrl);
                URL yandexTranslateURL = new URL(yandexUrl);

                //Set Http Conncection, Input Stream, and Buffered Reader
                HttpURLConnection httpJsonConnection = (HttpURLConnection) yandexTranslateURL.openConnection();
                Log.i("status",httpJsonConnection.getResponseMessage());

                InputStream inputStream = httpJsonConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                //Set string builder and insert retrieved JSON result into it
                StringBuilder jsonStringBuilder = new StringBuilder();
                while ((jsonString = bufferedReader.readLine()) != null) {
                    jsonStringBuilder.append(jsonString + "\n");
                }

                //Close and disconnect
                bufferedReader.close();
                inputStream.close();
                httpJsonConnection.disconnect();

                //Making result human readable
                String resultString = jsonStringBuilder.toString().trim();
                //Getting the characters between [ and ]
                resultString = resultString.substring(resultString.indexOf('[')+1);
                resultString = resultString.substring(0,resultString.indexOf("]"));
                //Getting the characters between " and "
                resultString = resultString.substring(resultString.indexOf("\"")+1);
                resultString = resultString.substring(0,resultString.indexOf("\""));

                return jsonStringBuilder.toString().trim();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(String result) {
            JsonObject jo=new JsonParser().parse(result).getAsJsonObject();
            String renk=jo.get("text").getAsString();
            String kiyafet=resultBuilder.getKiyafet();
            if(mTts.isSpeaking()){
                mTts.stop();
                Log.i("tts","konuşuyordu,durduruldu");
            }
            else{
                Log.i("tts","önce konuşuyor mu : "+mTts.isSpeaking());
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){
                    Log.i("tts","Lollipop üstü version");

                    mTts.speak(renk+" "+kiyafet, TextToSpeech.QUEUE_ADD, null,null);

                }
                else{
                    Log.i("tts","Lollipop altı version");

                    mTts.speak(renk+" "+kiyafet, TextToSpeech.QUEUE_ADD, null);

                }
                Log.i("tts","sonra konuşuyor mu : "+mTts.isSpeaking());

            }


            Log.i("tts","result : "+renk+" "+kiyafet);

        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }
    }
}
package com.ibm.visual_recognition;

import android.app.Activity;
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
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.watson.developer_cloud.service.exception.ForbiddenException;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyImagesOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;

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

import java.util.Locale;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;

public class MainActivity extends AppCompatActivity implements OnInitListener {

    RecognitionResultBuilder resultBuilder;//Sonuçlarımızı almamıza yardımcı olan sınıf
    private RecognitionResultFragment resultFragment;//Sonucu göstereceğimiz fragment

    private static final int REQUEST_CAMERA = 1;//Kamera için istek numarası
    private static final int REQUEST_TTS = 2;//Kamera için istek numarası
    private static final String STATE_IMAGE = "image";

    private static final float MAX_IMAGE_DIMENSION = 1200;//Watson en fazla 1200 pixellik fotoları kabul ediyor
    private VisualRecognition visualService;//Görüntü tanıma servisi

    private String mSelectedImageUri = null;//Çekilen fotoğrafın telefonda kaydedilen yerin uri'ı
    private File output = null;//fotonun kaydedileceği dosya

    private TextToSpeech mTts;//Yazıyı sese çeviren kütüphane
    private boolean ready=false;//tts'in hazır olup olmadığını anlamak için boolean değer

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//Uygulama açılır açılmaz Text to Speech'in başlatılmasını sağlıyoruz
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, REQUEST_TTS);

//Çekilen fotoğrafı koyacağımız ImageView
        ImageView selectedImageView = (ImageView) findViewById(R.id.selectedImageView);

        //Fotoğraf çekerken fotoğraf makinesinin kullanacağı geçici saklama yerini ayarlıyoruz
        if (savedInstanceState == null) {//eğer uygulama ilk defa oluşturulmuşsa(telefon pozisyonu değişmemişse)
            // Uygulamanın dosyaları kaydedeceği yerin yolunu alır be buradan bir file nesnesi döndürür.
            File dir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
            dir.mkdirs();//File nesnesi için directory oluşturur
            output = new File(dir, "mCameraContent.jpeg");
            Log.i("output","savedInstanceState == null: "+output.getPath());
            // /storage/sdcard/Android/data/com.ibm.visual_recognition/files/DCIM/mCameraContent.jpeg
        } else {
            output = (File)savedInstanceState.getSerializable("com.ibm.visual_recognition.EXTRA_FILENAME");
            Log.i("output","savedInstanceState != null: "+output.getPath());
        }

//Sonucu tutmak için fragment oluşturulması
        resultFragment = (RecognitionResultFragment)getSupportFragmentManager().findFragmentByTag("result");
        if (resultFragment == null) {//yoksa oluştur
            resultFragment = new RecognitionResultFragment();
            resultFragment.setRetainInstance(true);
            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, resultFragment, "result").commit();
        }
        if (savedInstanceState != null) {
            //Telefonun pozisyonu değiştiyse çekilen fotoğrafın yeniden ekrana basılması için
            mSelectedImageUri = savedInstanceState.getString(STATE_IMAGE);
            if (mSelectedImageUri != null) {
                //uri'ı bundle nesnesinden alıyoruz.
                //uri'den bitmap oluşturup,imageview'e basıyoruz
                Uri imageUri = Uri.parse(mSelectedImageUri);
                Bitmap selectedImage = HelperMethods.fetchBitmapFromUri(imageUri,getContentResolver(),getApplicationContext());
                selectedImageView.setImageBitmap(selectedImage);
            }
        }

//Ekrana dokunulduğunda kamerayı açar
        selectedImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, REQUEST_CAMERA);
            }
        });



        // Bluemix Mobil servislerle bağlantı kurabilmek için SDK'yı tanımlıyoruz
        BMSClient.getInstance().initialize(getApplicationContext(), BMSClient.REGION_UK);
        //Görsel Tanıma servisinden api key ve versiyon tarihiyle nesne oluşturuyoruz
        visualService = new VisualRecognition(VisualRecognition.VERSION_DATE_2016_05_20,
                getString(R.string.visualrecognitionApi_key));

//IBM Watson'dan anahtarımız var mı diye kontrolleri yapıyor
        ValidateCredentialsTask vct = new ValidateCredentialsTask(visualService,getApplicationContext());
        vct.execute();
    }

    @Override
    public void onDestroy() {
//Yeniden oluşturmada ,ya da ekran pozisyonunun değişmesi durumunda fragmentin durumunu kaydeder
        resultFragment.saveData();
        if (mTts != null) {//tts'i durdurur.
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        super.onDestroy();
    }


    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
//Yeniden oluşturulma durumunda fotoğrafın uri'ına yeniden ulaşabilmek için bundle nesnesinde bu bilgiyi saklıyoruz.
        savedInstanceState.putString(STATE_IMAGE, mSelectedImageUri);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TTS) {
            //Text to Speech tarafından kullanılan kaynaklar varsa,tts nesnesini oluşturur.
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                mTts = new TextToSpeech(this,this);
            } else {
                //Text to Speech tarafından kullanılan kaynaklar yoksa,bunları indirir
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
        if (resultCode == Activity.RESULT_OK && data != null) {
             if(requestCode == REQUEST_CAMERA){
                //Çekilen fotoyu alır ve ekrana basar.
                //Daha sonra bu fotoyu tanıması ve bize sonuç döndürmesi için yeniden boyutlandırıp,IBM Watson'a gönderir
                ImageView imageView = (ImageView) findViewById(R.id.selectedImageView);

                Bitmap photo = (Bitmap) data.getExtras().get("data");
                imageView.setImageBitmap(photo);

                // Resize the Bitmap to constrain within Watson Image Recognition's Size Limit.
                photo = HelperMethods.resizeBitmapForWatson(photo, MAX_IMAGE_DIMENSION);

                ClassifyTask ct = new ClassifyTask();
                //TTS hazır olana kadar,bekletir.O hazır olduğunda tanıma işlemini başlatır.
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

        }
    }


    @Override
    public void onInit(int status) {//tts kurulduğunda
        if(status==TextToSpeech.SUCCESS){//dilini ve hazır olduğunu belirler
            ready=true;
            mTts.setLanguage(Locale.getDefault());//telefonun dili neyse o
            Log.i("tts","tts success");
        }

        else if(status==TextToSpeech.ERROR){//tts'te sorun varsa hazır olmadığını belirler
            ready=false;
            Log.e("tts","tts error");
        }
    }



    private class ClassifyTask extends AsyncTask<Bitmap, Void, ClassifyTaskResult> {

        @Override
        protected void onPreExecute() {//Sonuç dönene kadar,yükleniyor spinner animasyonun gösterilmesini sağlar
            ProgressBar progressSpinner = (ProgressBar)findViewById(R.id.loadingSpinner);
            progressSpinner.setVisibility(View.VISIBLE);

            //Resmi göstereceğimiz layout üzerinde yeni sonuç için temizleme yapılır.
            LinearLayout resultLayout = (LinearLayout) findViewById(R.id.recognitionResultLayout);
            resultLayout.removeAllViews();
        }

        //Visual Recognition servisine çekilen fotonun gönderilmesi ve sonuçlar arasından en yüksek yüzdeli olanların seçilmesi için
        //RecognitionResultBuilder sınıfına gönderilmesi yapılıyor.
        @Override
        protected ClassifyTaskResult doInBackground(Bitmap... params) {
            Bitmap createdPhoto = params[0];//fotoğraf
//Bitmap dosyasının jpg formatına dönüştürülüp,Watson'a dosya olarak kaydedilmesi
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            createdPhoto.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

            try {
                File tempPhoto = File.createTempFile("photo", ".jpg", getCacheDir());
                FileOutputStream out = new FileOutputStream(tempPhoto);
                out.write(bytes.toByteArray());
                out.close();

//Curl'de kıyafet için eğitip,aldığımız id ile kıyafet sınıflarını alıyoruz.
                ClassifyImagesOptions classifyImagesOptions = new ClassifyImagesOptions.Builder().classifierIds("giysiler_1890457300").images(tempPhoto).build();
//Burada watson'da hazır eğitilmiş sınıfları alıyoruz.
                ClassifyImagesOptions colorOptions = new ClassifyImagesOptions.Builder().images(tempPhoto).build();

                VisualClassification classification = visualService.classify(classifyImagesOptions).execute();
                VisualClassification colorClassification = visualService.classify(colorOptions).execute();

                ClassifyTaskResult result = new ClassifyTaskResult(classification, colorClassification);

                tempPhoto.delete();

                return result;
            } catch (Exception ex) {
                if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                    Toast.makeText(getApplicationContext(),"Internete Bağlantısında Sorun Var",Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(getApplicationContext(),ex.getMessage(),Toast.LENGTH_SHORT).show();

                }
                return null;
            }
        }

        @Override
        protected void onPostExecute(ClassifyTaskResult result) {
            //Sonuç döndüğünde yükleniyor spinnerını yok ediyoruz.
            ProgressBar progressSpinner = (ProgressBar)findViewById(R.id.loadingSpinner);
            progressSpinner.setVisibility(View.GONE);

            if (result != null) {

                resultBuilder = new RecognitionResultBuilder(MainActivity.this);
                LinearLayout resultLayout = (LinearLayout) findViewById(R.id.recognitionResultLayout);

                if(resultLayout != null){
                    resultLayout.removeAllViews();
                }
                //sonuçlar arasından en yüksek yüzdeli olanları seçmek için RecognitionResultBuilder'a gönderiyoruz
                LinearLayout recognitionView = resultBuilder.buildRecognitionResultView(result.getVisualClassification(), result.getColorClassification());
                //Renk sonucunu ingilizceden türkçeye çeviriyoruz.
                new TranslatorBackgroundTask("trnsl.1.1.20170826T124332Z.c7f36074597a666f.f831f314a08423422cd841afca69af8e4a869564").execute(resultBuilder.getRenk(),"en-tr");
                resultLayout.addView(recognitionView);

            }
        }
    }

    //Yandex Trasnlate api ile iletişimi sağlayan sınıf
    public class TranslatorBackgroundTask extends AsyncTask<String, Void, String> {
        private String yandexKey;
        TranslatorBackgroundTask(String yandexKey){this.yandexKey=yandexKey;}

        @Override
        protected String doInBackground(String... params) {
            //String variables
            String textToBeTranslated = params[0];
            String languagePair = params[1];
            String jsonString;
            try {
                String yandexUrl = "https://translate.yandex.net/api/v1.5/tr.json/translate?key=" + yandexKey
                        + "&text=" + textToBeTranslated + "&lang=" + languagePair;
                Log.i("url",yandexUrl);
                URL yandexTranslateURL = new URL(yandexUrl);
                HttpURLConnection httpJsonConnection = (HttpURLConnection) yandexTranslateURL.openConnection();
                Log.i("status",httpJsonConnection.getResponseMessage());

                InputStream inputStream = httpJsonConnection.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                StringBuilder jsonStringBuilder = new StringBuilder();
                while ((jsonString = bufferedReader.readLine()) != null) {
                    jsonStringBuilder.append(jsonString + "\n");}

                bufferedReader.close();
                inputStream.close();
                httpJsonConnection.disconnect();

                String resultString = jsonStringBuilder.toString().trim();
                resultString = resultString.substring(resultString.indexOf('[')+1);
                resultString = resultString.substring(0,resultString.indexOf("]"));
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
            //Dönen json stringi parçalanır ve renk bilgisinin türkçesi alınır.
            JsonObject jo=new JsonParser().parse(result).getAsJsonObject();
            String renk=jo.get("text").getAsString();
            //Kıyafetler türkçe olduğu için çevrilmeden doğrudan alınır
            String kiyafet=resultBuilder.getKiyafet();
            if(mTts.isSpeaking()){//tts daha önceden konuşuyor gözüküyorsa,yeni texti konuşabilmesi için susturulur.
                mTts.stop();
                Log.i("tts","konuşuyordu,durduruldu");
            }
            else{
                Log.i("tts","önce konuşuyor mu : "+mTts.isSpeaking());
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP){//Lollipop versiyonu ve üzeri için speak metotu ile konuşturulur
                    Log.i("tts","Lollipop üstü version");
                    mTts.speak(renk+" "+kiyafet, TextToSpeech.QUEUE_ADD, null,null);

                }
                else{//diğer versiyonlar için speak metotu
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
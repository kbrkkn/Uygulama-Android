package com.ibm.visual_recognition;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.service.exception.ForbiddenException;
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;

import java.net.UnknownHostException;

/**
 * Created by kubra on 22.09.2017.
 */


//Gönerilen kriterlerin(api key ve version tarihi) geçerli olup olmadığının kontrol edildiği sınıf
public class ValidateCredentialsTask extends AsyncTask<Void, Void, Void> {
    private VisualRecognition visualService;
    private Context context;
    public  ValidateCredentialsTask(VisualRecognition visualService,Context context){
        this.visualService=visualService;
this.context=context;
    }
    @Override
    protected Void doInBackground(Void... params) {
        try {
            //Kriterler geçerliyse görsel tanıma servisinin sınıflarına bir arama başlatır.
            visualService.getClassifiers().execute();
        }
        catch (Exception ex) {//kriterler geçersizse toast mesajı gösterir
            if (ex.getClass().equals(ForbiddenException.class))  {
                Toast.makeText(context,"Visual Recognition Servisi için geçersiz kriterler var",Toast.LENGTH_SHORT).show();
            }
            else if (ex.getCause() != null && ex.getCause().getClass().equals(UnknownHostException.class)) {
                Toast.makeText(context,"Internet Bağlantısında Sorun Var",Toast.LENGTH_SHORT).show();

            }
            else {
                Toast.makeText(context,ex.getMessage(),Toast.LENGTH_SHORT).show();
            }
        }
        return null;
    }
}
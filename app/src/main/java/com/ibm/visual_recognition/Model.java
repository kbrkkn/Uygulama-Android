package com.ibm.visual_recognition;

//Renk ve kıyafet için isim ve yüzdelik skorun alındığı sınıf
public class Model {
    private String modelName;
    private Double score;

    public Model(String modelName, Double score) {
        this.modelName = modelName;
        this.score = score;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}

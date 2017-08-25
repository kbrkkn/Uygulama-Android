package com.ibm.visual_recognition;


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

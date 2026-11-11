package com.aurora.client.ui.util;

public class Animation {
    private float currentValue;
    private float targetValue;
    private float speed;

    public Animation(float startValue, float speed) {
        this.currentValue = startValue;
        this.targetValue = startValue;
        this.speed = speed;
    }

    public float getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(float currentValue) {
        this.currentValue = currentValue;
    }

    public float getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(float targetValue) {
        this.targetValue = targetValue;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void update(float delta) {
        float diff = targetValue - currentValue;
        if (Math.abs(diff) < 0.01f) {
            currentValue = targetValue;
        } else {
            // Clamp the step calculation to ensure it cannot overshoot between 0.0 and 1.0
            float step = diff * speed * Math.min(delta, 0.05f);
            currentValue += step;
        }
    }
}

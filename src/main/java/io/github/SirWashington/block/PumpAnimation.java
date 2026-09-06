package io.github.SirWashington.block;

/** Client-only visual state; blade pitch and rotor movement never change together. */
public final class PumpAnimation {
    private int bladeStep, speedStep;
    public float angle, previousAngle, pitch, previousPitch;

    public void tick(boolean powered) {
        previousAngle = angle;
        previousPitch = pitch;
        if (powered) {
            if (bladeStep < 10) bladeStep++;
            else if (speedStep < 10) speedStep++;
        } else {
            if (speedStep > 0) speedStep--;
            else if (bladeStep > 0) bladeStep--;
        }
        pitch = bladeStep / 10F;
        angle -= speedStep * 1.8F;
        if (angle < 0) { angle += 360; previousAngle += 360; }
    }
}

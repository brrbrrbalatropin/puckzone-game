package com.puckzone.game.bot;

/**
 * Predicción de la trayectoria del disco: en qué Y cruzará una línea
 * vertical dada, contando los rebotes en las paredes superior e inferior.
 * Matemática pura sin estado — el rebote perfecto convierte la Y "cruda"
 * (como si no hubiera paredes) en una onda triangular, así que basta
 * plegarla al rango válido en vez de simular tick a tick.
 */
public final class PuckPredictor {

    private PuckPredictor() {
    }

    /**
     * Y del centro del disco cuando cruce {@code targetX}, o {@code NaN}
     * si nunca va a cruzarla (se aleja o va vertical). {@code minY/maxY}
     * son los límites del CENTRO del disco (radio ya descontado).
     */
    public static double yAtX(double x, double y, double vx, double vy,
                              double targetX, double minY, double maxY) {
        if (vx == 0 || (targetX - x) * vx < 0) {
            return Double.NaN;
        }
        double time = (targetX - x) / vx;
        return fold(y + vy * time, minY, maxY);
    }

    /** Pliega una Y sin paredes al rango [minY, maxY] espejando en los bordes. */
    public static double fold(double y, double minY, double maxY) {
        double range = maxY - minY;
        if (range <= 0) {
            return minY;
        }
        double period = 2 * range;
        double offset = ((y - minY) % period + period) % period;
        return offset <= range ? minY + offset : minY + (period - offset);
    }
}

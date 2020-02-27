/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.util;

import org.pepsoft.bukkit.blockysmoke.IntLocation;

/**
 * Some math related utility methods.
 * 
 * @author Pepijn Schmitz
 */
public class MathUtils {
    private MathUtils() {
        // Prevent instantiation
    }
    
    public static float getDistance(IntLocation loc1, IntLocation loc2) {
        return getDistance(loc2.x - loc1.x, loc2.y - loc1.y, loc2.z - loc1.z);
    }

    public static float getDistance(int dx, int dy, int dz) {
        if ((dx >= -25) && (dx <= 25) && (dy >= -25) && (dy <= 25) && (dz >= -25) && (dz <= 25)) {
            return DISTANCE_TABLE[Math.abs(dx)][Math.abs(dy)][Math.abs(dz)];
        } else {
            return (float) Math.sqrt(dx * dz + dy * dy + dz * dz);
        }
    }
    
    private static final float[][][] DISTANCE_TABLE = new float[26][26][26];
    
    static {
        for (int dx = 0; dx < 26; dx++) {
            for (int dy = 0; dy < 26; dy++) {
                for (int dz = 0; dz < 26; dz++) {
                    DISTANCE_TABLE[dx][dy][dz] = (float) Math.sqrt(dx * dz + dy * dy + dz * dz);
                }
            }
        }
    }
}
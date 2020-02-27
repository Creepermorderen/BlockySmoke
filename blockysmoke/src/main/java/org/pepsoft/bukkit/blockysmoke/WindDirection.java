/*
 This file is part of BlockySmokePlugin

 BlockySmokePlugin is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 BlockySmokePlugin is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pepsoft.bukkit.blockysmoke;

import java.util.Random;

/**
 * A wind direction, with methods for rotation and variation.
 * 
 * @author Pepijn Schmitz
 */
public enum WindDirection {
    N(0, -1), NE(1, -1), E(1, 0), SE(1, 1), S(0, 1), SW(-1, 1), W(-1, 0), NW(-1, -1);
    
    private WindDirection(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public WindDirection clockwise() {
        return values()[(ordinal() + 1) & 0x7];
    }

    public WindDirection counterClockwise() {
        return values()[(ordinal() - 1) & 0x7];
    }
    
    public WindDirection random(int steps) {
        return values()[(ordinal() + random.nextInt(steps * 2 + 1) - steps) & 0x7];
    }
    
    public WindDirection constrain(WindDirection from, WindDirection to) {
        if (from == to) {
            return from;
        }
        int myOrdinal = ordinal(), fromOrdinal = from.ordinal(), toOrdinal = to.ordinal();
        if (toOrdinal > fromOrdinal) {
            if ((myOrdinal >= fromOrdinal) && (myOrdinal <= toOrdinal)) {
                return this;
            } else if (myOrdinal < fromOrdinal) {
                return values()[fromOrdinal + triangle(fromOrdinal - myOrdinal, toOrdinal - fromOrdinal)];
            } else {
                return values()[fromOrdinal + triangle(myOrdinal - fromOrdinal, toOrdinal - fromOrdinal)];
            }
        } else {
            if ((myOrdinal <= toOrdinal) || (myOrdinal >= fromOrdinal)) {
                return this;
            } else {
                return values()[(toOrdinal - triangle(myOrdinal - toOrdinal, toOrdinal - (fromOrdinal - 8))) & 0x7];
            }
        }
    }
    
    /**
     * Creates a triangle wave. <strong>Only works for positive x!</strong>
     * 
     * @param x Phase. <strong>Must be positive!</strong>
     * @param y Triangle height
     * @return Triangle wave value
     */
    private static int triangle(int x, int y) {
        return ((x % (y << 1)) < y) ? x % y : y - (x % y);
    }
    
    public static void main(String[] args) {
        System.out.println("Constrain between N and S:");
        for (WindDirection direction: values()) {
            System.out.println("  " + direction.constrain(N, S));
        }
        System.out.println("Constrain between E and W:");
        for (WindDirection direction: values()) {
            System.out.println("  " + direction.constrain(E, W));
        }
        System.out.println("Constrain between S and N:");
        for (WindDirection direction: values()) {
            System.out.println("  " + direction.constrain(S, N));
        }
        System.out.println("Constrain between W and E:");
        for (WindDirection direction: values()) {
            System.out.println("  " + direction.constrain(W, E));
        }
    }
    
    public final int dx, dy;
    
    private static final Random random = new Random();
}
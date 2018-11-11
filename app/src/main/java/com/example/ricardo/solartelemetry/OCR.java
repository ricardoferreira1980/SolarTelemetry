package com.example.ricardo.solartelemetry;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

public class OCR {

    private Bitmap bitmap;
    int startY = -1, endY = -1;
    int digit3startX = - 1, digit3endX = -1;
    int digit2startX = - 1, digit2endX = -1;
    int digit1startX = - 1, digit1endX = -1;

    public OCR(byte[] data) {
        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        findBoundaries();
    }

    public int readValue() {
        int digit3 = readDigit(digit3startX, startY, digit3endX, endY);
        int digit2 = readDigit(digit2startX, startY, digit2endX, endY);
        int digit1 = readDigit(digit1startX, startY, digit1endX, endY);

        if (digit3 == -1 || digit2 == -1 || digit1 == -1) {
            return -1;
        }
        return digit3 * 100 + digit2 * 10 + digit1;
    }

    private double getPixelLuminance(int x, int y) {
        if (x < 0 || y < 0 || x >= bitmap.getWidth() || y >= bitmap.getHeight()) {
            return 0;
        }
        int pixel = bitmap.getPixel(x,y);
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        return (r * 0.3) + (g * 0.59) + (b * 0.11);
    }

    private double get4xPixelLuminance(int x, int y) {
        return (getPixelLuminance(x, y) +
                getPixelLuminance(x +1, y) +
                getPixelLuminance(x, y+ 1) +
                getPixelLuminance(x+1, y+1)) / 4;
    }

    private int readDigit(int startX, int startY, int endX, int endY) {
        if (startY == 0 && endY == 0) {
            return -1;
        }

        int margin = 0;
        startX -= margin;
        startY -= margin;
        endX += margin;
        endY += margin;
        startY -= 10;
        endY -= 10;
        int w = endX - startX;
        int h = endY - startY;

        /*
               1
               _
            4 | | 5
               -  2
            6 | | 7
               -
               3
         */

        double luminanceDecrease = 0.80;

        // find middle active vertical points
        boolean seg1 = false;
        boolean seg2 = false;
        boolean seg3 = false;

        int x = startX + w / 2;
        double lastLum = get4xPixelLuminance(x, startY - 5);
        for (int y = startY - 5; y < endY; y += 3) {
            double lum = get4xPixelLuminance(x, y);
            if (lum < lastLum * luminanceDecrease) {
                // drop in luminance
                if (y < startY + h / 6) {
                    seg1 = true;
                }
                if (y > startY + h / 3 && y < endY - h / 3) {
                    seg2 = true;
                }
                if (y > endY - h / 6) {
                    seg3 = true;
                }
            }
            lastLum = lum;
        }

        // top horizontal, left to right
        boolean seg4 = false;
        boolean seg5 = false;

        int y = startY + h / 4;
        lastLum = get4xPixelLuminance(startX - 5, y);
        for (x = startX - 5; x < endX; x += 3) {
            double lum = get4xPixelLuminance(x, y);
            if (lum < lastLum * luminanceDecrease) {
                // drop in luminance
                if (x < startX + w / 2) {
                    seg4 = true;
                } else {
                    seg5 = true;
                }
            }
            lastLum = lum;
        }

        // top horizontal, left to right
        boolean seg6 = false;
        boolean seg7 = false;

        y = endY - h / 4;
        lastLum = get4xPixelLuminance(startX - 5, y);
        for (x = startX - 5; x < endX; x += 3) {
            double lum = get4xPixelLuminance(x, y);
            if (lum < lastLum * luminanceDecrease) {
                // drop in luminance
                if (x < startX + w / 2) {
                    seg6 = true;
                } else {
                    seg7 = true;
                }
            }
            lastLum = lum;
        }

        if (seg1 && seg2 && seg3) {
            if (!seg4 && seg5 && seg6 && !seg7)
                return 2;
            if (!seg4 && seg5 && !seg6 && seg7)
                return 3;
            if (seg4 && !seg5 && !seg6 && seg7)
                return 5;
            if (seg4 && !seg5 && seg6 && seg7)
                return 6;
            if (seg4 && seg5 && seg6 && seg7)
                return 8;
            if (seg4 && seg5 && !seg6 && seg7)
                return 9;
        } else if ((seg1 && !seg2 && seg3 && seg4 && seg5 && seg6 && seg7) ||
                   (!seg1 && !seg2 && !seg3 && !seg4 && !seg5 && !seg6 && !seg7)) {
            return 0;
        } else if (!seg1 && seg2 && !seg3 && seg4 && seg5 && !seg6 && seg7) {
            return 4;
        } else if (seg1 && !seg2 && !seg3 && !seg4 && seg5 && !seg6 && seg7) {
            return 7;
        } else if (!seg1 && !seg2 && !seg3 && !seg4 && seg5 && !seg6 && seg7) {
            return 1;
        }

        /*
        return (seg1 ? "-" : " ") + (seg2 ? "-" : " ") + (seg3 ? "-" : " ") + (seg4 ? "-" : " ") +
                (seg5 ? "-" : " ") + (seg6 ? "-" : " ") + (seg7 ? "-" : " "); */
        return -1;
    }


    private void findBoundaries() {
        int h = bitmap.getHeight();
        int w = bitmap.getWidth();

        // find startY, endY
        for (int x = 0; x < w; x+=5) {
            boolean foundRed = false;
            for (int y = 0; y < h; y++) {
                int pixel = bitmap.getPixel(x,y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                if (r >= 25 && r > g + b) {
                    foundRed = true;
                    if (startY == -1) {
                        startY = y;
                    } else {
                        startY = Math.min(startY, y);
                    }
                    endY = Math.max(endY, y);
                }
            }
            if (startY != -1 && !foundRed) {
                break; // finished left red segment
            }
        }

        if (endY - startY < 30 || endY - startY > 120) {
            // no reds found
            startY = 0;
            endY = 0;
            return;
        } else {
            // workaround!!
            endY = startY + 90;
        }
        // find horizontal digit positions
        for (int y = startY - (endY-startY) / 2; y < startY; y++) {
            boolean finished = false;
            for (int x = 0; x < w; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                if (r >= 25 && r > g + b) {
                    if (digit3startX == -1) {
                        digit3startX = x;
                    }
                    digit3endX = Math.max(digit3endX, x);
                } else if (digit3endX > digit3startX + 10) {
                    finished = true; // finished first digit red segment
                    break;
                }
            }
            if (finished) {
                break;
            }
        }

        // find horizontal digit positions
        for (int y = startY - (endY-startY) / 2; y < startY; y++) {
            boolean finished = false;
            for (int x = digit3endX + 1; x < w; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                if (r >= 25 && r > g + b) {
                    if (digit2startX == -1) {
                        digit2startX = x;
                    }
                    digit2endX = Math.max(digit2endX, x);
                } else if (digit2endX > digit2startX + 10) {
                    finished = true; // finished second digit red segment
                    break;
                }
            }
            if (finished) {
                break;
            }
        }

        // find horizontal digit positions
        for (int y = startY - (endY-startY) / 2; y < startY; y++) {
            boolean finished = false;
            for (int x = digit2endX + 1; x < w; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                if (r >= 25 && r > g + b) {
                    if (digit1startX == -1) {
                        digit1startX = x;
                    }
                    digit1endX = Math.max(digit1endX, x);
                } else if (digit1endX > digit1startX + 10) {
                    finished = true; // finished second digit red segment
                    break;
                }
            }
            if (finished) {
                break;
            }
        }
    }
}

package com.example.ricardo.solartelemetry;

import java.util.Calendar;

public class SunriseSunsetHelper {

    private double latitude = 38.7223;
    private double longitude = -9.1393;

    private Calendar[] sunriseSunset;

    public SunriseSunsetHelper() {
        // get today's sunrise
        sunriseSunset = SunriseSunset.getSunriseSunset(Calendar.getInstance(), latitude, longitude);
    }

    public Calendar getTomorrowSunrise() {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        sunriseSunset = SunriseSunset.getSunriseSunset(tomorrow, latitude, longitude);
        return sunriseSunset[0];
    }

    public Calendar getTodaySunset() {
        return sunriseSunset[1];
    }

}

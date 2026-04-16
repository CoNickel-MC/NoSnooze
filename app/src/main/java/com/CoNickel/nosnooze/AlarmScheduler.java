package com.CoNickel.nosnooze;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class AlarmScheduler {
    private final Context context;
    private static final String PREFS_NAME = "NoSnoozePrefs";
    private static final String KEY_STREAK = "streak";
    private static final String KEY_DEADLINE = "deadline";
    private static final String KEY_LAST_ANSWER = "last_answer";

    public AlarmScheduler(Context context) {
        this.context = context;
    }

    public void scheduleAlarm(int hour1, int minute1, int hour2, int minute2) {
        if (isTimeLater(hour1, minute1, hour2, minute2)) {
            long startTime = getMillis(hour1, minute1);
            long endTime = getMillis(hour2, minute2);
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_DEADLINE, endTime).apply();

            setAlarm(startTime);
            Toast.makeText(context, "Alarm set!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Second time must be later than the first time", Toast.LENGTH_SHORT).show();
        }
    }

    private long getMillis(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }

    private boolean isTimeLater(int h1, int m1, int h2, int m2) {
        if (h2 > h1) return true;
        if (h2 == h1) return m2 > m1;
        return false;
    }

    private void setAlarm(long timeMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                    timeMillis,
                    pendingIntent
            );
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
        }
    }

    public int getStreak() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_STREAK, 0);
    }

    public void processChallengeResult(boolean solvedOnTime) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (solvedOnTime) {
            int current = prefs.getInt(KEY_STREAK, 0);
            prefs.edit().putInt(KEY_STREAK, current + 1).apply();
        } else {
            prefs.edit().putInt(KEY_STREAK, 0).apply();
        }
        prefs.edit().putLong(KEY_DEADLINE, 0).apply();
    }

    public void checkMissedDeadline() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long deadline = prefs.getLong(KEY_DEADLINE, 0);
        if (deadline != 0 && System.currentTimeMillis() > deadline) {
            prefs.edit().putInt(KEY_STREAK, 0).putLong(KEY_DEADLINE, 0).apply();
        }
    }

    public String generateProblem() {
        Random rand = new Random();
        int a = rand.nextInt(41) + 10;
        int b = rand.nextInt(41) + 10;
        boolean isAdd = rand.nextBoolean();
        int result = isAdd ? a + b : a - b;
        
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_LAST_ANSWER, result).apply();
        
        return isAdd ? a + " + " + b : a + " - " + b;
    }

    public boolean verifyAnswer(String answerStr) {
        try {
            int answer = Integer.parseInt(answerStr);
            int correctAnswer = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_LAST_ANSWER, -9999);
            return answer == correctAnswer;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public boolean isBeforeDeadline() {
        long deadline = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_DEADLINE, 0);
        return System.currentTimeMillis() <= deadline;
    }
}

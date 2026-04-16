package com.CoNickel.nosnooze;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

public class AlarmScheduler {
    private final Context context;

    public AlarmScheduler(Context context) {
        this.context = context;
    }

    public void scheduleAlarm(int hour1, int minute1, int hour2, int minute2) {
        if (isTimeLater(hour1, minute1, hour2, minute2)) {
            setAlarm(hour1, minute1);
            Toast.makeText(context, "Alarm set for " + hour1 + ":" + String.format(Locale.getDefault(), "%02d", minute1), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Second time must be later than the first time", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isTimeLater(int h1, int m1, int h2, int m2) {
        if (h2 > h1) return true;
        if (h2 == h1) return m2 > m1;
        return false;
    }

    private void setAlarm(int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        // If the time is in the past, set it for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (alarmManager != null) {
            // setAlarmClock is the best for actual alarms as it shows up in the system UI
            // and is very reliable for waking up the device.
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
        }
    }
}

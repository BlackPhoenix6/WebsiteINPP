package de.uni.getraenke;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Holt regelmäßig (alle paar Stunden, nur mit Internet) die Speisepläne von heute. */
public class MensaRefreshJob extends JobService {

    private static final int JOB_ID = 4711;

    public static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, MensaRefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(4L * 60 * 60 * 1000)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            long now = System.currentTimeMillis();
            MensaApi.menu(this, MensaApi.TU, now, true);
            MensaApi.menu(this, MensaApi.VEGGIE, now, true);
            MensaWidget.updateAll(this);
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}

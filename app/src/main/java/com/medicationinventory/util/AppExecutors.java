package com.medicationinventory.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;




/**
 * Centralizes the executors used to keep Room work off the main thread and
 * deliver repository results back to Android UI observers. The default database
 * executor is single-threaded, which also gives queued database tasks a stable
 * execution order; the public constructor allows tests or composition code to
 * supply controlled executors.
 */
public final class AppExecutors
{
    private static volatile AppExecutors INSTANCE;

    private final ExecutorService databaseIO;
    private final Executor mainThread;



    private AppExecutors()
    {
        this(
                Executors.newSingleThreadExecutor(),
                new MainThreadExecutor()
        );
    }



    public AppExecutors(
            ExecutorService databaseIO,
            Executor mainThread)
    {
        if (databaseIO == null)
        {
            throw new IllegalArgumentException(
                    "Database executor is required."
            );
        }

        if (mainThread == null)
        {
            throw new IllegalArgumentException(
                    "Main-thread executor is required."
            );
        }


        this.databaseIO =
                databaseIO;

        this.mainThread =
                mainThread;
    }


    public static AppExecutors getInstance()
    {
        if (INSTANCE == null)
        {
            synchronized (AppExecutors.class)
            {
                if (INSTANCE == null)
                {
                    INSTANCE =
                            new AppExecutors();
                }
            }
        }


        return INSTANCE;
    }


    public ExecutorService databaseIO()
    {
        return databaseIO;
    }


    public Executor mainThread()
    {
        return mainThread;
    }


    private static class MainThreadExecutor
            implements Executor
    {
        private final Handler handler =
                new Handler(
                        Looper.getMainLooper()
                );


        @Override

        public void execute(
                Runnable command)
        {
            handler.post(
                    command
            );
        }
    }
}
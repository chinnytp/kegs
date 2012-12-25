package com.froop.app.kegs;

import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.Log;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentLinkedQueue;

// This is the primary interface into the native KEGS thread, and also
// where the native KEGS thread calls back into Java.

class KegsThread extends Thread {
  private ConcurrentLinkedQueue<Event.KegsEvent> mEventQueue = new ConcurrentLinkedQueue<Event.KegsEvent>();

  private String mConfigFile;  // full path to config_kegs
  private Bitmap mBitmap;
  private final ReentrantLock mPauseLock = new ReentrantLock();
  private final ReentrantLock mPowerWait = new ReentrantLock();

  // Look also at mPauseLock.
  private boolean mPaused = false;
  private boolean mReady = false;     // 'true' will begin the native thread.

  interface UpdateScreen {
    void updateScreen();
  }
  private UpdateScreen mUpdateScreen;

  public KegsThread(String configFile, Bitmap bitmap) {
    mConfigFile = configFile;
    mBitmap = bitmap;
  }

  public ConcurrentLinkedQueue getEventQueue() {
    return mEventQueue;
  }

  public void registerUpdateScreenInterface(UpdateScreen update) {
    mUpdateScreen = update;
  }

  // Called by native thread.  Sometimes called by UI thread.
  protected void updateScreen() {
    if (mUpdateScreen != null) {
      mUpdateScreen.updateScreen();
    }
  }

  // Called by native thread.
  private void checkForPause() {
    if (mPaused) {
      mPauseLock.lock();
      // deadlock here until onResume.  Maybe not efficient.
      mPauseLock.unlock();
    }
  }

  // Called by native thread.
  private String getConfigFile() {
    return mConfigFile;
  }

  // See jni/android_driver.c:mainLoop()
  private native void mainLoop(Bitmap b, ConcurrentLinkedQueue q);

  @Override
  public void run() {
    while(true) {
      mPowerWait.lock();
      mPowerWait.unlock();
      mainLoop(mBitmap, mEventQueue);
    }
// For TESTING:
//      while (true) {
//        try {
//          Thread.sleep(500);
//          checkForPause();
//        } catch (InterruptedException e) {}
//      }
  }

  public void onPause() {
    if (!mReady) {
      return;  // bail out, we haven't started doing anything yet
    }
    if (!mPaused) {
      mPaused = true;
      mPauseLock.lock();
    }
  }

  public void onResume() {
    if (!mReady) {
      return;  // bail out, we haven't started doing anything yet
    }
    updateScreen();
    if (mPaused) {
      mPaused = false;
      mPauseLock.unlock();
    } else if (!isAlive()) {
      start();
    }
  }

  // Has the thread itself actually paused waiting to acquire the lock?
  public boolean nowPaused() {
    return mPauseLock.hasQueuedThreads();
  }

  public void doPowerOff() {
    if (!mReady) {
      return;  // bail out, we haven't started doing anything yet
    }

    // Tell the native thread loop to wait before powering on again.
    mPowerWait.lock();

    // Special event, see android_driver.c:x_key_special()
    mEventQueue.add(new Event.KeyKegsEvent(120 + 0x80, true));
  }

  // Call from the UI thread only.
  public void allowPowerOn() {
    if (!mReady) {
      mReady = true;
      onResume();  // Will start the thread if not already started.
      return;
    }

    // As the native thread is allowed to loop by default,
    // this is only useful after using doPowerOff().
    if (mPowerWait.isHeldByCurrentThread()) {
      mPowerWait.unlock();
    }
  }

  // Is native thread loop sitting around waiting for us to allow power on?
  public boolean nowWaitingForPowerOn() {
    if (!mReady) {
      return true;  // bail out, we haven't started doing anything yet
    }

    return mPowerWait.hasQueuedThreads();
  }

  public void setEmulationSpeed(int speed) {
    // Speed matches g_limit_speed inside KEGS.
    // Instead of a separate "control" event, key ids with bit 8 high are
    // special events.  See android_driver.c:x_key_special()
    getEventQueue().add(new Event.KeyKegsEvent(speed + 0x80, true));
  }

  public void doWarmReset() {
    // Press keys down.
    getEventQueue().add(new Event.KeyKegsEvent(KegsKeyboard.KEY_OPEN_APPLE, false));
    getEventQueue().add(new Event.KeyKegsEvent(KegsKeyboard.KEY_CONTROL, false));
    getEventQueue().add(new Event.KeyKegsEvent(KegsKeyboard.KEY_RESET, false));
    // Release reset key first, then the others.
    getEventQueue().add(new Event.KeyKegsEvent(KegsKeyboard.KEY_RESET, true));
    getEventQueue().add(new Event.KeyKegsEvent(KegsKeyboard.KEY_CONTROL, true));
    getEventQueue().add(new Event.KeyKegsEvent(KegsKeyboard.KEY_OPEN_APPLE, true));
  }
}

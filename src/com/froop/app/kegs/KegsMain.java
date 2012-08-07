package com.froop.app.kegs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.AsyncTask;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ToggleButton;

public class KegsMain extends Activity implements KegsKeyboard.StickyReset {
  private static final String FRAGMENT_ROM = "rom";
  private static final String FRAGMENT_DOWNLOAD = "download";
  private static final String FRAGMENT_ERROR = "error";

  private KegsView mKegsView;
  private KegsTouch mKegsTouch;
  private KegsKeyboard mKegsKeyboard;

  private View.OnClickListener mButtonClick = new View.OnClickListener() {
    public void onClick(View v) {
//      Log.e("kegs", "button clicked");
      int click_id = v.getId();
      int key_id = -1;
      boolean sticky = false;
      if (click_id == R.id.key_escape) {
        key_id = KegsKeyboard.KEY_ESCAPE;
      } else if (click_id == R.id.key_return) {
        key_id = KegsKeyboard.KEY_RETURN;
      } else if (click_id == R.id.key_f4) {
        key_id = KegsKeyboard.KEY_F4;
      } else if (click_id == R.id.key_tab) {
        key_id = KegsKeyboard.KEY_TAB;
      } else if (click_id == R.id.key_control) {
        key_id = KegsKeyboard.KEY_CONTROL;
        sticky = true;
      } else if (click_id == R.id.key_open_apple) {
        key_id = KegsKeyboard.KEY_OPEN_APPLE;
        sticky = true;
      } else if (click_id == R.id.key_closed_apple) {
        key_id = KegsKeyboard.KEY_CLOSED_APPLE;
        sticky = true;
      } else if (click_id == R.id.key_left) {
        key_id = KegsKeyboard.KEY_LEFT;
      } else if (click_id == R.id.key_right) {
        key_id = KegsKeyboard.KEY_RIGHT;
      } else if (click_id == R.id.key_up) {
        key_id = KegsKeyboard.KEY_UP;
      } else if (click_id == R.id.key_down) {
        key_id = KegsKeyboard.KEY_DOWN;
      } else {
        Log.e("kegs", "UNKNOWN BUTTON " + click_id + " CLICKED");
      }
      if (key_id != -1) {
        if (sticky) {
          mKegsKeyboard.keyDownSticky(key_id, !((ToggleButton)v).isChecked());
        } else {
          mKegsKeyboard.keyDownUp(key_id);
        }
      }
    }
  };

  public void onStickyReset() {
    ((ToggleButton)findViewById(R.id.key_control)).setChecked(false);
    ((ToggleButton)findViewById(R.id.key_open_apple)).setChecked(false);
    ((ToggleButton)findViewById(R.id.key_closed_apple)).setChecked(false);
  }

  protected void getRomFile(String romfile) {
    final DialogFragment download = new DownloadDialogFragment();
    download.show(getFragmentManager(), FRAGMENT_DOWNLOAD);
    new DownloadRom().execute(romfile);
  }

  class DownloadRom extends AsyncTask<String, Void, Boolean> {
    private String mRomfile;
    protected Boolean doInBackground(String ... raw_romfile) {
      mRomfile = raw_romfile[0];
      return new DownloadHelper().save(
          "http://jsan.co/" + mRomfile, Config.mPath.getPath() + "/" + mRomfile);
    }
    protected void onPostExecute(Boolean success) {
      final DialogFragment frag = (DialogFragment)getFragmentManager().findFragmentByTag(FRAGMENT_DOWNLOAD);
      if (frag != null) {
        frag.dismiss();
      }
      if (!success) {
        if (!isCancelled()) {
          final DialogFragment dialog = new ErrorDialogFragment();
          dialog.show(getFragmentManager(), FRAGMENT_ERROR);
        }
      } else {
        Config.checkConfig(mRomfile);
        mKegsView.setReady(true);
      }
    }
  }

  class DownloadDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      ProgressDialog dialog = new ProgressDialog(getActivity());
      // TODO: should probably use an XML layout for this.
      dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      dialog.setMessage("Checking for ROM...");
      dialog.setProgressNumberFormat(null);
      dialog.setProgressPercentFormat(null);
      dialog.setIndeterminate(true);
      dialog.setCancelable(false);
      dialog.setCanceledOnTouchOutside(false);  // lame
      return dialog;
    }
  }

  class ErrorDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setMessage("Unable to obtain ROM.  Find ROM.01 or ROM.03 and put it in the /KEGS/ directory on your SD Card.");
// TODO do getActivity().finish() on button clicks
      return builder.create();
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
// TODO: verify that this is all OK, and even Return works immediately.
    return executeKeyEvent(event) || super.dispatchKeyEvent(event);
//    return super.dispatchKeyEvent(event) || executeKeyEvent(event);
  }

  public boolean executeKeyEvent(KeyEvent event) {
    return mKegsKeyboard.keyEvent(event);
  }

//  @Override
//  public boolean dispatchGenericMotionEvent(MotionEvent event) {
//    // Joystick!  if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && event.getAction() == MotionEvent.ACTION_MOVE) {}
//    // See also GameControllerInput.java from ApiDemos
//    return super.dispatchGenericMotionEvent(event);
//  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    mKegsView = (KegsView)findViewById(R.id.kegsview);

    mKegsTouch = new KegsTouch(mKegsView.getEventQueue());
    final GestureDetector inputDetect = new GestureDetector(this, mKegsTouch);

    final View mainView = findViewById(R.id.mainview);
    mainView.setClickable(true);
    mainView.setLongClickable(true);
    mainView.setOnTouchListener(new OnTouchListener() {
      public boolean onTouch(View v, MotionEvent event) {
        return inputDetect.onTouchEvent(event);
      }
    });

    mKegsKeyboard = new KegsKeyboard(mKegsView.getEventQueue());
    mKegsKeyboard.setOnStickyReset(this);

    findViewById(R.id.key_escape).setOnClickListener(mButtonClick);
    findViewById(R.id.key_return).setOnClickListener(mButtonClick);
    findViewById(R.id.key_f4).setOnClickListener(mButtonClick);
    findViewById(R.id.key_control).setOnClickListener(mButtonClick);
    findViewById(R.id.key_open_apple).setOnClickListener(mButtonClick);
    findViewById(R.id.key_closed_apple).setOnClickListener(mButtonClick);
    findViewById(R.id.key_left).setOnClickListener(mButtonClick);
    findViewById(R.id.key_right).setOnClickListener(mButtonClick);
    findViewById(R.id.key_up).setOnClickListener(mButtonClick);
    findViewById(R.id.key_down).setOnClickListener(mButtonClick);

    final String romfile = Config.whichRomFile();
    if (romfile == null) {
      final DialogFragment chooseRom = new RomDialogFragment();
      chooseRom.show(getFragmentManager(), FRAGMENT_ROM);
    } else {
      Config.checkConfig(romfile);
      mKegsView.setReady(true);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    mKegsView.getThread().onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mKegsView.getThread().onResume();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Log.w("kegs", "onDestroy called, halting");
    // Force process to exit.  We cannot handle another onCreate
    // once a KegsView has been active.  (JNI kegsmain has already run)
    java.lang.Runtime.getRuntime().halt(0);
  }

  static {
    System.loadLibrary("kegs");
  }
}
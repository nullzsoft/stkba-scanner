package com.google.zxing.client.android;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.client.android.history.DBHelper;
import com.google.zxing.client.android.history.HistoryManager;
import com.google.zxing.client.android.result.ResultButtonListener;
import com.google.zxing.client.android.result.ResultHandler;
import com.google.zxing.client.android.result.ResultHandlerFactory;
import com.google.zxing.client.android.result.supplement.SupplementalInfoRetriever;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class HistoryActivity extends Activity {
	  private static final int[] buttons = {
	      R.string.button_prev,
	      R.string.button_next,
	      R.string.button_send_history,
	      R.string.button_delete_history
	  };	
	  private static final String TAG = CaptureActivity.class.getSimpleName();

	  private static final int SHARE_ID = Menu.FIRST;
	  private static final int HISTORY_ID = Menu.FIRST + 1;
	  private static final int SETTINGS_ID = Menu.FIRST + 2;
	  private static final int HELP_ID = Menu.FIRST + 3;
	  private static final int ABOUT_ID = Menu.FIRST + 4;

	  private static final long INTENT_RESULT_DURATION = 1500L;
	  private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;

	  private static final String PACKAGE_NAME = "com.google.zxing.client.android";
	  private static final String PRODUCT_SEARCH_URL_PREFIX = "http://www.google";
	  private static final String PRODUCT_SEARCH_URL_SUFFIX = "/m/products/scan";
	  private static final String ZXING_URL = "http://zxing.appspot.com/scan";
	  private static final String RETURN_CODE_PLACEHOLDER = "{CODE}";
	  private static final String RETURN_URL_PARAM = "ret";
	  private static final Set<ResultMetadataType> DISPLAYABLE_METADATA_TYPES;
	  
	  static {
	    DISPLAYABLE_METADATA_TYPES = new HashSet<ResultMetadataType>(5);
	    DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.ISSUE_NUMBER);
	    DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.SUGGESTED_PRICE);
	    DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.ERROR_CORRECTION_LEVEL);
	    DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.POSSIBLE_COUNTRY);
	  }

	  private View resultView;
	  private List<Result> items;	  
	  private int m_index;

	  private static final int MAX_ITEMS = 500;

	  private static final String[] GET_ITEM_COL_PROJECTION = {
	      DBHelper.TEXT_COL,
	      DBHelper.DISPLAY_COL,
	      DBHelper.FORMAT_COL,
	      DBHelper.TIMESTAMP_COL,
	  };
	  private static final String[] EXPORT_COL_PROJECTION = {
	      DBHelper.TEXT_COL,
	      DBHelper.DISPLAY_COL,
	      DBHelper.FORMAT_COL,
	      DBHelper.TIMESTAMP_COL,
	  };
	  

	@Override
	  protected void onCreate(Bundle icicle) {
	    super.onCreate(icicle);
	    setContentView(R.layout.history);

	    resultView = findViewById(R.id.result_view2);

	    resultView.setVisibility(View.VISIBLE);  
	    	      
	    m_index = 0;
	    setResult();
	    if(items.size() > 0)
	    	handleDecode(items.get(m_index), null);
	    
	}
	
	private void setResult(){
	    SQLiteOpenHelper helper = new DBHelper(this.getApplicationContext());
	    items = new ArrayList<Result>();
	    List<String> dialogItems = new ArrayList<String>();
	    SQLiteDatabase db = null;
	    Cursor cursor = null;
	    try {
	      db = helper.getWritableDatabase();
	      cursor = db.query(DBHelper.TABLE_NAME, GET_ITEM_COL_PROJECTION, null, null, null, null,
	          DBHelper.TIMESTAMP_COL + " DESC");
	      while (cursor.moveToNext()) {
	        Result result = new Result(cursor.getString(0), null, null,
	            BarcodeFormat.valueOf(cursor.getString(2)), cursor.getLong(3));
	        items.add(result);
	        String display = cursor.getString(1);
	        if (display == null || display.length() == 0) {
	          display = result.getText();
	        }
	        dialogItems.add(display);
	      }
	    } catch (SQLiteException sqle) {
	      Log.w(TAG, "Error while opening database", sqle);
	    } finally {
	      if (cursor != null) {
	        cursor.close();
	      }
	      if (db != null) {
	        db.close();
	      }
	      
	    }
	}
	
	  /**
	   * A valid barcode has been found, so give an indication of success and show the results.
	   *
	   * @param rawResult The contents of the barcode.
	   * @param barcode   A greyscale bitmap of the camera data which was decoded.
	   */
	  public void handleDecode(Result rawResult, Bitmap barcode) {

	    ResultHandler resultHandler = ResultHandlerFactory.makeResultHistoryHandler(this, rawResult);

	    if (barcode == null) {
	      // This is from history -- no saved barcode
	      handleDecodeInternally(rawResult, resultHandler, null);
	    } 
	  }
	  
	  // Put up our own UI for how to handle the decoded contents.
	  private void handleDecodeInternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {	  
 
	    
	    ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view2);

	    barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
	          R.drawable.launcher_icon));


	    TextView formatTextView = (TextView) findViewById(R.id.format_text_view2);
	    formatTextView.setText(rawResult.getBarcodeFormat().toString());

	    TextView typeTextView = (TextView) findViewById(R.id.type_text_view2);
	    typeTextView.setText(resultHandler.getType().toString());

	    DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
	    String formattedTime = formatter.format(new Date(rawResult.getTimestamp()));
	    TextView timeTextView = (TextView) findViewById(R.id.time_text_view2);
	    timeTextView.setText(formattedTime);

	    TextView metaTextView = (TextView) findViewById(R.id.meta_text_view2);
	    View metaTextViewLabel = findViewById(R.id.meta_text_view_label2);
	    metaTextView.setVisibility(View.GONE);
	    metaTextViewLabel.setVisibility(View.GONE);
	    Map<ResultMetadataType,Object> metadata =
	        (Map<ResultMetadataType,Object>) rawResult.getResultMetadata();
	    if (metadata != null) {
	      StringBuilder metadataText = new StringBuilder(20);
	      for (Map.Entry<ResultMetadataType,Object> entry : metadata.entrySet()) {
	        if (DISPLAYABLE_METADATA_TYPES.contains(entry.getKey())) {
	          metadataText.append(entry.getValue()).append('\n');
	        }
	      }
	      if (metadataText.length() > 0) {
	        metadataText.setLength(metadataText.length() - 1);
	        metaTextView.setText(metadataText);
	        metaTextView.setVisibility(View.VISIBLE);
	        metaTextViewLabel.setVisibility(View.VISIBLE);
	      }
	    }

	    TextView contentsTextView = (TextView) findViewById(R.id.contents_text_view2);
	    CharSequence displayContents = resultHandler.getDisplayContents();
	    contentsTextView.setText(displayContents);
	    // Crudely scale betweeen 22 and 32 -- bigger font for shorter text
	    int scaledSize = Math.max(22, 32 - displayContents.length() / 4);
	    contentsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);

	    TextView supplementTextView = (TextView) findViewById(R.id.contents_supplement_text_view2);
	    supplementTextView.setText("");
	    supplementTextView.setOnClickListener(null);
/*	    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
	        PreferencesActivity.KEY_SUPPLEMENTAL, true)) {
	      SupplementalInfoRetriever.maybeInvokeRetrieval(supplementTextView, resultHandler.getResult(),
	          handler, this);
	    }
*/
	    
	    int buttonCount = resultHandler.getButtonCount();
	    ViewGroup buttonView = (ViewGroup) findViewById(R.id.result_button_view2);
	    buttonView.requestFocus();
	    for (int x = 0; x < ResultHandler.MAX_BUTTON_COUNT; x++) {
	      TextView button = (TextView) buttonView.getChildAt(x);
	      if (x < buttonCount) {
	        button.setVisibility(View.VISIBLE);
	        button.setText(resultHandler.getButtonText(x));
	        button.setOnClickListener(new ResultButtonListener(resultHandler, x));
	      } else {
	        button.setVisibility(View.GONE);
	      }
	    }

	  }
}

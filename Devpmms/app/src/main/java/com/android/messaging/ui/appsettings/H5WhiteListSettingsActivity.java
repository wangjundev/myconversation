package com.android.messaging.ui.appsettings;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.UIIntents;

import java.util.ArrayList;
import java.util.List;

public class H5WhiteListSettingsActivity extends BugleActionBarActivity {
    private static final int TAB_LENGTH_MAX = 50;
    private H5WLListView myListView;

    //private H5CNAdapter adapter;
    private SimpleCursorAdapter mAdapter;
    private Cursor mCursor;

//    private H5WLDatabaseHelper mdb_helper;
//    private SQLiteDatabase mdb;
    private DatabaseWrapper mdbWrapper;
    private Handler mHandler;


    private List<String> contentList = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.h5wllistview_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final boolean topLevel = getIntent().getBooleanExtra(
                UIIntents.UI_INTENT_EXTRA_TOP_LEVEL_SETTINGS, false);
        /*if (topLevel)*/ {
            getSupportActionBar().setTitle(/*getString(R.string.settings_activity_title)*/"H5直显白名单");
        }

        //initList();
        myListView = (H5WLListView) findViewById(R.id.my_list_view);
        myListView.setOnDeleteListener(new H5WLListView.OnDeleteListener() {
            @Override
            public void onDelete(int index) {
                //contentList.remove(index);
                //adapter.notifyDataSetChanged();
                deleteItem(index);
            }
        });
        //adapter = new H5CNAdapter(this, 0, contentList);
        //myListView.setAdapter(adapter);
        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mAdapter = new SimpleCursorAdapter(getApplicationContext(), R.layout.h5wl_listview_item, mCursor,
                        new String[]{"codenumber"}, new int[]{R.id.text_view}, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
                myListView.setAdapter(mAdapter);
            }
        };

//        mdb_helper = new H5WLDatabaseHelper(this, "h5wldb", null, 1);
//        mdb = mdb_helper.getWritableDatabase();
//        mCursor = mdb.query(H5WLDatabaseHelper.H5WL_TABLENAME, null, null, null, null, null, null);
        new Thread(new Runnable(){
            @Override
            public void run() {
                mdbWrapper = DataModel.get().getDatabase();
                mCursor = mdbWrapper.query(DatabaseHelper.H5WHITELIST_TABLE, null, null, null, null, null, null);
                Message msg = new Message();
                msg.arg1 = 1;
                mHandler.sendMessage(msg);
            }
        }).start();
//                mAdapter = new SimpleCursorAdapter(getApplicationContext(), R.layout.h5wl_listview_item, mCursor,
//                        new String[]{"codenumber"}, new int[]{R.id.text_view}, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
//                myListView.setAdapter(mAdapter);
//        mdbWrapper = DataModel.get().getDatabase();
//        mdbWrapper.query(H5WLDatabaseHelper.H5WL_TABLENAME, null, null, null, null, null, null);
//        mAdapter = new SimpleCursorAdapter(this, R.layout.h5wl_listview_item, mCursor,
//                new String[]{"codenumber"}, new int[]{R.id.text_view}, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
//        myListView.setAdapter(mAdapter);
    }



    /*@Override
    public void onContentChanged() {
        mCursor = mdb.query("H5_Whitelist", null, null, null, null, null, null);
        mAdapter.changeCursor(mCursor);
    }*/

//    @Override
//    protected void onResume() {
//        super.onResume();
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                mdbWrapper = DataModel.get().getDatabase();
//                mCursor = mdbWrapper.query(DatabaseHelper.H5WHITELIST_TABLE, null, null, null, null, null, null);
//                Message msg = new Message();
//                msg.arg1 = 1;
//                mHandler.sendMessage(msg);
//            }
//        }).start();
//    }

    private void refreshListView(){
//        mCursor = mdb.query(H5WLDatabaseHelper.H5WL_TABLENAME, null, null,
//                null, null, null, null);
        mCursor = mdbWrapper.query(DatabaseHelper.H5WHITELIST_TABLE, null, null, null, null, null, null);
        mAdapter.changeCursor(mCursor);
        MessagingContentProvider.notifyConversationListChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (super.onCreateOptionsMenu(menu)) {
            return true;
        }
        getMenuInflater().inflate(R.menu.add_h5_white_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()){
            case R.id.add_h5_wl:
                buildEditDialog();
                return true;
            case android.R.id.home:
                onBackPressed();
                //MessagingContentProvider.notifyConversationListChanged();
                return true;
            default:
               break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void addItem(String addNumber){
        ContentValues cv = new ContentValues();
        String tempNumber = addNumber.replaceAll(" ", "");

        if(tempNumber.length() <= 6 || tempNumber.startsWith("106") || tempNumber.startsWith("+86") ){
            cv.put(DatabaseHelper.H5CodeNumberColumns.H5_DISPLAY_CODENUMBER, tempNumber);
        }else if(tempNumber.startsWith("86")){
            cv.put(DatabaseHelper.H5CodeNumberColumns.H5_DISPLAY_CODENUMBER, "+"+tempNumber);
        }else{
            cv.put(DatabaseHelper.H5CodeNumberColumns.H5_DISPLAY_CODENUMBER, "+86"+tempNumber);
        }
        //cv.put("codenumber", addNumber);
//        mdb.insert(H5WLDatabaseHelper.H5WL_TABLENAME, null, cv);
        mdbWrapper.insert(DatabaseHelper.H5WHITELIST_TABLE, null, cv);
        refreshListView();
    }

    public void deleteItem(int positon) {
        Cursor mCursor = mAdapter.getCursor();
        mCursor.moveToPosition(positon);
        int itemId = mCursor.getInt(mCursor.getColumnIndex("_id"));
//        mdb.delete(H5WLDatabaseHelper.H5WL_TABLENAME, "_id=?", new String[]{itemId + ""});
        mdbWrapper.delete(DatabaseHelper.H5WHITELIST_TABLE, "_id=?", new String[]{itemId + ""});
        refreshListView();
    }

    private void buildEditDialog(){
        final EditText text = new EditText(this);
        new android.app.AlertDialog.Builder(this).setTitle(/*R.string.new_tab*/"您要添加的号码是：")
                .setView(text)
                .setPositiveButton(/*R.string.ok*/"确定", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String string = text.getText().toString();
                        int size = string.length();
                        if(size == 0){
                            buildEditDialog();
                            Toast.makeText(myListView.getContext(), "您的输入为空，请重新输入！", Toast.LENGTH_LONG).show();
                        }else if(size > TAB_LENGTH_MAX){
                            buildEditDialog();
                            //Toast.makeText(this, getResources().getString(R.string.check_length), 500).show();
                            Toast.makeText(myListView.getContext(), "您输入的长度超过了50个字符，请检查", Toast.LENGTH_LONG).show();
                        }else{
                            addItem(string);
                        }

                    }
                })
                .setNegativeButton(/*R.string.cancel*/"取消", null)
                .show();
    }

    @Override
    protected void onStop() {
//        mCursor.close();
//        mdb_helper.close();
//        mdb.close();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mCursor.close();
        super.onDestroy();
    }
}

package com.example.notex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class LocalDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "notex_local.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NOTES = "local_notes";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TEXT = "text";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    public LocalDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NOTES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TEXT + " TEXT, " +
                COLUMN_TIMESTAMP + " LONG)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        onCreate(db);
    }

    public String insertNote(String text, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TEXT, text);
        values.put(COLUMN_TIMESTAMP, timestamp);
        long id = db.insert(TABLE_NOTES, null, values);
        return String.valueOf(id);
    }

    public ArrayList<String> getAllNotes() {
        ArrayList<String> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NOTES + " ORDER BY " + COLUMN_TIMESTAMP + " DESC", null);

        if (cursor.moveToFirst()) {
            do {
                String text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEXT));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                notes.add(text + "||" + timestamp + "||local_" + id);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return notes;
    }

    public void deleteNote(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Remove 'local_' prefix if present
        String numericId = id.replace("local_", "");
        db.delete(TABLE_NOTES, COLUMN_ID + " = ?", new String[]{numericId});
    }
}

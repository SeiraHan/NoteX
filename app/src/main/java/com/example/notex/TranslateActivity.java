package com.example.notex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TranslateActivity extends AppCompatActivity {

    private static final String TAG = "TranslateActivity";

    private MaterialButton btnSelectNote, btnSelectLanguage, btnTranslate;
    private MaterialCardView statusCard;
    private TextView tvStatus;

    private FirebaseAuth mAuth;
    private LocalDatabaseHelper localDb;
    private OkHttpClient httpClient;

    private final ArrayList<String> fullNoteList = new ArrayList<>();
    private String selectedNoteContent = null;
    private String selectedLanguage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_translate);

        // INIT VIEWS
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        btnSelectNote = findViewById(R.id.btnSelectNote);
        btnSelectLanguage = findViewById(R.id.btnSelectLanguage);
        btnTranslate = findViewById(R.id.btnTranslate);
        statusCard = findViewById(R.id.statusCard);
        tvStatus = findViewById(R.id.tvStatus);

        localDb = new LocalDatabaseHelper(this);
        mAuth = FirebaseAuth.getInstance();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // SETUP TOOLBAR
        if (topAppBar != null) {
            topAppBar.setNavigationOnClickListener(v -> finish());
            topAppBar.inflateMenu(R.menu.menu);

            if (topAppBar.getMenu().findItem(R.id.action_search) != null) {
                topAppBar.getMenu().findItem(R.id.action_search).setVisible(false);
            }

            topAppBar.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    finish();
                    return true;
                } else if (id == R.id.nav_saved) {
                    startActivity(new Intent(this, SavedFilesActivity.class));
                    return true;
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                }
                return false;
            });
        }

        // Authentication Check
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            mAuth.signInAnonymously().addOnSuccessListener(authResult -> loadNotes());
        } else {
            loadNotes();
        }

        // BUTTON CLICK LISTENERS
        btnSelectNote.setOnClickListener(v -> showNoteSelectionDialog());
        btnSelectLanguage.setOnClickListener(v -> showLanguageSelectionDialog());
        btnTranslate.setOnClickListener(v -> startTranslation());
    }

    private void loadNotes() {
        fullNoteList.clear();
        fullNoteList.addAll(localDb.getAllNotes());

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            DatabaseReference databaseRef = FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes").child(user.getUid());
            databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot data : snapshot.getChildren()) {
                        String text = data.child("text").getValue(String.class);
                        Long timestamp = data.child("timestamp").getValue(Long.class);
                        String key = data.getKey();
                        if (text != null && key != null) {
                            fullNoteList.add(text + LocalDatabaseHelper.SEPARATOR + (timestamp != null ? timestamp : 0L) + LocalDatabaseHelper.SEPARATOR + key);
                        }
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, error.getMessage());
                }
            });
        }
    }

    private void showNoteSelectionDialog() {
        if (fullNoteList.isEmpty()) {
            Toast.makeText(this, "No notes found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] noteTitles = new String[fullNoteList.size()];
        for (int i = 0; i < fullNoteList.size(); i++) {
            String[] parts = LocalDatabaseHelper.splitNote(fullNoteList.get(i));
            String content = parts[0];
            noteTitles[i] = content.split("\n")[0];
            if (noteTitles[i].length() > 40) noteTitles[i] = noteTitles[i].substring(0, 37) + "...";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Note")
                .setItems(noteTitles, (dialog, which) -> {
                    String[] parts = LocalDatabaseHelper.splitNote(fullNoteList.get(which));
                    selectedNoteContent = parts[0];
                    btnSelectNote.setText(noteTitles[which]);
                })
                .show();
    }

    private void showLanguageSelectionDialog() {
        String[] languages = {"Malay", "English", "Chinese", "Japanese", "Korean"};
        new AlertDialog.Builder(this)
                .setTitle("Select Language")
                .setItems(languages, (dialog, which) -> {
                    selectedLanguage = languages[which];
                    btnSelectLanguage.setText(selectedLanguage);
                })
                .show();
    }

    private void startTranslation() {
        if (selectedNoteContent == null || selectedLanguage == null) {
            Toast.makeText(this, "Select a note and language", Toast.LENGTH_SHORT).show();
            return;
        }

        statusCard.setVisibility(View.VISIBLE);
        tvStatus.setText("AI processing...");
        btnTranslate.setEnabled(false);

        try {
            JSONObject json = new JSONObject();
            json.put("model", "llama-3.1-8b-instant");
            JSONArray messages = new JSONArray();
            
            String prompt = "Translate this note into " + selectedLanguage + ". " +
                    "Plain text only. First line must be a title. No symbols.\n\nContent:\n" + selectedNoteContent;

            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            json.put("messages", messages);

            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + Config.GROQ_API_KEY)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        btnTranslate.setEnabled(true);
                        tvStatus.setText("Connection failed");
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        if (response.isSuccessful() && responseBody != null) {
                            JSONObject jsonResponse = new JSONObject(responseBody.string());
                            String translatedText = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            runOnUiThread(() -> saveAndOpenTranslatedNote(translatedText));
                        } else {
                            runOnUiThread(() -> {
                                btnTranslate.setEnabled(true);
                                tvStatus.setText("AI Error");
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parsing Error", e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Execution Error", e);
        }
    }

    private void saveAndOpenTranslatedNote(String text) {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isCloud = prefs.getBoolean("cloud_sync", true);
        long ts = System.currentTimeMillis();

        if (isCloud) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                DatabaseReference databaseRef = FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes").child(currentUser.getUid());
                String id = databaseRef.push().getKey();
                if (id != null) {
                    HashMap<String, Object> note = new HashMap<>();
                    note.put("text", text);
                    note.put("timestamp", ts);
                    databaseRef.child(id).setValue(note);
                    openNoteDetail(text, id);
                }
            } else {
                saveLocally(text, ts);
            }
        } else {
            saveLocally(text, ts);
        }
    }

    private void saveLocally(String text, long ts) {
        String id = localDb.insertNote(text, ts);
        openNoteDetail(text, "local_" + id);
    }

    private void openNoteDetail(String text, String id) {
        Intent intent = new Intent(this, NoteDetailActivity.class);
        intent.putExtra("text", text);
        intent.putExtra("noteId", id);
        startActivity(intent);
        finish();
    }
}

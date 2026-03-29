package com.example.notex;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class ScanActivity extends AppCompatActivity {

    private static final String TAG = "ScanActivity";

    private ImageView imagePreview;
    private MaterialButton btnGallery, btnCamera, btnExtract, btnFinish;
    private TextView tvResult;
    private MaterialToolbar topBarScan;

    private FirebaseAuth mAuth;
    private OkHttpClient httpClient;
    private LocalDatabaseHelper localDb;

    private Uri imageUri;
    private Uri cameraImageUri;
    
    private StringBuilder fullExtractedText = new StringBuilder();
    private int scanCount = 0;
    private boolean isMultiPageEnabled = true;

    private static final int CAMERA_PERMISSION_CODE = 100;

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    imagePreview.setImageURI(imageUri);
                    tvResult.setText("Image selected. Tap to scan.");
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    imageUri = cameraImageUri;
                    imagePreview.setImageURI(imageUri);
                    tvResult.setText("Photo captured. Tap to scan.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scan);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        isMultiPageEnabled = prefs.getBoolean("multi_page", true);

        mAuth = FirebaseAuth.getInstance();
        localDb = new LocalDatabaseHelper(this);
        
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        // Init views
        imagePreview = findViewById(R.id.imagePreview);
        btnGallery = findViewById(R.id.btnGallery);
        btnCamera = findViewById(R.id.btnCamera);
        btnExtract = findViewById(R.id.btnExtract);
        btnFinish = findViewById(R.id.btnFinish);
        tvResult = findViewById(R.id.tvResult);
        topBarScan = findViewById(R.id.topBarScan);

        if (topBarScan != null) {
            topBarScan.setNavigationOnClickListener(v -> finish());
            topBarScan.inflateMenu(R.menu.menu);
            
            if (topBarScan.getMenu().findItem(R.id.action_search) != null) {
                topBarScan.getMenu().findItem(R.id.action_search).setVisible(false);
            }

            topBarScan.setOnMenuItemClickListener(item -> {
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

        if (!isMultiPageEnabled) {
            btnExtract.setText("Extract Smart Note");
        }

        btnGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            galleryLauncher.launch(intent);
        });

        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        });

        btnExtract.setOnClickListener(v -> startOCR());

        btnFinish.setOnClickListener(v -> {
            if (scanCount > 0) {
                processWithAI(fullExtractedText.toString());
            } else {
                Toast.makeText(this, "Scan at least one page first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "NoteX Image");
        cameraImageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
        cameraLauncher.launch(intent);
    }

    private void startOCR() {
        if (imageUri == null) {
            Toast.makeText(this, "Select or capture an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        tvResult.setText("Processing OCR...");
        btnExtract.setEnabled(false);

        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(result -> {
                        String rawText = result.getText();
                        if (rawText.trim().isEmpty()) {
                            tvResult.setText("No text detected. Try another image.");
                            btnExtract.setEnabled(true);
                            return;
                        }
                        
                        if (isMultiPageEnabled) {
                            scanCount++;
                            fullExtractedText.append("\n[Page ").append(scanCount).append("]\n").append(rawText).append("\n");
                            tvResult.setText("Added page " + scanCount + ". Capture next or Finish.");
                            btnFinish.setVisibility(View.VISIBLE);
                            btnFinish.setEnabled(true);
                            btnExtract.setEnabled(true);
                            imageUri = null;
                            imagePreview.setImageResource(R.drawable.notemain);
                        } else {
                            processWithAI(rawText);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "OCR Error: ", e);
                        tvResult.setText("OCR Failed: " + e.getMessage());
                        btnExtract.setEnabled(true);
                    });
        } catch (Exception e) {
            Log.e(TAG, "InputImage Error: ", e);
            tvResult.setText("Error loading image.");
            btnExtract.setEnabled(true);
        }
    }

    private void processWithAI(String combinedText) {
        runOnUiThread(() -> {
            tvResult.setText("AI generating smart note...");
            btnFinish.setEnabled(false);
            btnExtract.setEnabled(false);
            btnGallery.setEnabled(false);
            btnCamera.setEnabled(false);
        });

        try {
            JSONObject json = new JSONObject();
            json.put("model", "llama-3.1-8b-instant");
            JSONArray messages = new JSONArray();
            
            String prompt = "Clean up and structure the following raw OCR text into organized study notes. " +
                    "Use plain text only. Do not use any markdown formatting like bold (**), italics, or headers. " +
                    "FIRST LINE MUST BE A SHORT TITLE. " +
                    "\n\nContent:\n" + combinedText;

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
                    Log.e(TAG, "AI Request Failure", e);
                    handleAIResult(combinedText, "AI Request Failed. Saving raw text.");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (response.isSuccessful() && responseBody != null) {
                            String responseString = responseBody.string();
                            JSONObject jsonResponse = new JSONObject(responseString);
                            String out = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            handleAIResult(out, null);
                        } else {
                            String respBody = (responseBody != null) ? responseBody.string() : "null";
                            Log.e(TAG, "AI API Error: " + response.code() + " Body: " + respBody);
                            handleAIResult(combinedText, "AI Error (" + response.code() + "). Saving raw.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "AI Response Processing Error", e);
                        handleAIResult(combinedText, "Processing Error. Saving raw.");
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "AI Execution Error", e);
            handleAIResult(combinedText, "Execution Error. Saving raw.");
        }
    }

    private void handleAIResult(String text, String message) {
        runOnUiThread(() -> {
            if (message != null) Toast.makeText(ScanActivity.this, message, Toast.LENGTH_SHORT).show();
            saveNote(text);
        });
    }

    private void saveNote(String text) {
        if (text == null || text.trim().isEmpty()) {
             runOnUiThread(() -> {
                 resetButtons();
                 tvResult.setText("Empty note content. Aborted.");
             });
             return;
        }
        
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isCloud = prefs.getBoolean("cloud_sync", true);
        long ts = System.currentTimeMillis();

        if (isCloud) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                saveToCloud(text, ts, currentUser.getUid());
            } else {
                mAuth.signInAnonymously().addOnSuccessListener(this, authResult -> {
                    FirebaseUser newUser = authResult.getUser();
                    if (newUser != null) {
                        saveToCloud(text, ts, newUser.getUid());
                    } else {
                        saveLocally(text, ts);
                    }
                }).addOnFailureListener(this, e -> {
                    Toast.makeText(this, "Offline Mode: Saving locally.", Toast.LENGTH_SHORT).show();
                    saveLocally(text, ts);
                });
            }
        } else {
            saveLocally(text, ts);
        }
    }

    private void saveToCloud(String text, long ts, String uid) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes").child(uid);
        String id = databaseRef.push().getKey();
        if (id != null) {
            HashMap<String, Object> note = new HashMap<>();
            note.put("text", text);
            note.put("timestamp", ts);
            
            databaseRef.child(id).setValue(note);
            
            Toast.makeText(this, "Note Saved Successfully", Toast.LENGTH_SHORT).show();
            openNoteDetail(text, id);
        } else {
            saveLocally(text, ts);
        }
    }

    private void saveLocally(String text, long timestamp) {
        String id = localDb.insertNote(text, timestamp);
        Toast.makeText(this, "Note Saved Locally", Toast.LENGTH_SHORT).show();
        openNoteDetail(text, "local_" + id);
    }

    private void resetButtons() {
        btnFinish.setEnabled(true);
        btnExtract.setEnabled(true);
        btnGallery.setEnabled(true);
        btnCamera.setEnabled(true);
    }

    private void openNoteDetail(String text, String id) {
        Intent intent = new Intent(this, NoteDetailActivity.class);
        intent.putExtra("text", text);
        intent.putExtra("noteId", id);
        startActivity(intent);
        finish();
    }
}

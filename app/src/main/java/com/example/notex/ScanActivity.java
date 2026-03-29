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

    private DatabaseReference databaseRef;
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
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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
            
            // Hide search icon
            if (topBarScan.getMenu().findItem(R.id.action_search) != null) {
                topBarScan.getMenu().findItem(R.id.action_search).setVisible(false);
            }

            topBarScan.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    finish();
                    return true;
                } else if (id == R.id.nav_saved) {
                    // 🔥 ADDED SAVED FILES NAVIGATION
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
            Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        tvResult.setText("Processing...");
        btnExtract.setEnabled(false);

        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(result -> {
                        String rawText = result.getText();
                        if (rawText.trim().isEmpty()) {
                            tvResult.setText("No text found.");
                            btnExtract.setEnabled(true);
                            return;
                        }
                        
                        if (isMultiPageEnabled) {
                            scanCount++;
                            fullExtractedText.append("\n[Page ").append(scanCount).append("]\n").append(rawText).append("\n");
                            tvResult.setText("Scanned " + scanCount + " pages. Tap 'Finish' to generate note.");
                            btnFinish.setVisibility(View.VISIBLE);
                            btnExtract.setEnabled(true);
                            imageUri = null;
                            imagePreview.setImageResource(R.drawable.notemain);
                        } else {
                            processWithAI(rawText);
                        }
                    })
                    .addOnFailureListener(e -> {
                        tvResult.setText("OCR Failed.");
                        btnExtract.setEnabled(true);
                    });
        } catch (Exception e) {
            btnExtract.setEnabled(true);
        }
    }

    private void processWithAI(String combinedText) {
        runOnUiThread(() -> {
            tvResult.setText("Generating smart note...");
            btnFinish.setEnabled(false);
            btnExtract.setEnabled(false);
        });

        try {
            JSONObject json = new JSONObject();
            json.put("model", "llama-3.1-8b-instant");
            JSONArray messages = new JSONArray();
            
            String prompt = "Clean up and structure the following text into organized plain text study notes. " +
                    "Do NOT use markdown like ## or **. " +
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
                    handleAIResult(combinedText, "Request Failed.");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (response.isSuccessful() && responseBody != null) {
                            JSONObject jsonResponse = new JSONObject(responseBody.string());
                            String out = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            handleAIResult(out, "Smart Note Generated!");
                        } else {
                            handleAIResult(combinedText, "AI Error.");
                        }
                    } catch (Exception e) {
                        handleAIResult(combinedText, "Parsing Error.");
                    }
                }
            });
        } catch (Exception e) {
            handleAIResult(combinedText, "Execution Error.");
        }
    }

    private void handleAIResult(String text, String message) {
        runOnUiThread(() -> {
            if (message != null) Toast.makeText(ScanActivity.this, message, Toast.LENGTH_SHORT).show();
            saveNote(text);
        });
    }

    private void saveNote(String text) {
        if (text == null || text.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isCloud = prefs.getBoolean("cloud_sync", true);
        long ts = System.currentTimeMillis();

        if (isCloud) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                databaseRef = FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes").child(currentUser.getUid());
                String id = databaseRef.push().getKey();
                if (id != null) {
                    HashMap<String, Object> note = new HashMap<>();
                    note.put("text", text);
                    note.put("timestamp", ts);
                    databaseRef.child(id).setValue(note).addOnSuccessListener(unused -> openNoteDetail(text, id));
                }
            } else {
                saveLocally(text, ts);
            }
        } else {
            saveLocally(text, ts);
        }
    }

    private void saveLocally(String text, long timestamp) {
        String id = localDb.insertNote(text, timestamp);
        Toast.makeText(this, "Note Saved Locally!", Toast.LENGTH_SHORT).show();
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

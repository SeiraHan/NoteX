package com.example.notex;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;

import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    DatabaseReference databaseRef;
    FirebaseAuth mAuth;
    LocalDatabaseHelper localDb;
    RecyclerView recyclerView;
    ArrayList<String> cloudList = new ArrayList<>();
    ArrayList<String> localList = new ArrayList<>();
    NoteAdapter adapter;
    MaterialToolbar topAppBar;
    FloatingActionButton fabScan;
    MaterialCardView cardUpload, cardTranslate, cardConvert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Enable Firebase Offline Persistence
        try {
            FirebaseDatabase.getInstance(Config.DB_URL).setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "Persistence: " + e.getMessage());
        }

        recyclerView = findViewById(R.id.recyclerNotes);
        cardUpload = findViewById(R.id.cardUpload);
        cardTranslate = findViewById(R.id.cardTranslate);
        cardConvert = findViewById(R.id.cardConvert);
        topAppBar = findViewById(R.id.topAppBar);
        fabScan = findViewById(R.id.fabScan);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        localDb = new LocalDatabaseHelper(this);
        mAuth = FirebaseAuth.getInstance();

        adapter = new NoteAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        adapter.setOnItemLongClickListener(position -> {
            String item = adapter.getItem(position);
            if (item != null) {
                String[] parts = LocalDatabaseHelper.splitNote(item);
                showDeleteDialog(parts[2]);
            }
        });

        // Initialize user and then load data
        initializeUserAndLoadData();

        if (cardUpload != null) {
            cardUpload.setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        }

        if (cardTranslate != null) {
            cardTranslate.setOnClickListener(v -> startActivity(new Intent(this, TranslateActivity.class)));
        }

        if (cardConvert != null) {
            cardConvert.setOnClickListener(v -> startActivity(new Intent(this, ConvertActivity.class)));
        }

        if (fabScan != null) {
            fabScan.setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        }

        if (topAppBar != null) {
            if (topAppBar.getMenu().findItem(R.id.action_search) != null) {
                topAppBar.getMenu().findItem(R.id.action_search).setVisible(false);
            }
            if (topAppBar.getMenu().findItem(R.id.nav_home) != null) {
                topAppBar.getMenu().findItem(R.id.nav_home).setVisible(false);
            }

            topAppBar.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) return true;
                if (id == R.id.nav_saved) {
                    startActivity(new Intent(this, SavedFilesActivity.class));
                    return true;
                }
                if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                }
                if (id == R.id.action_search) {
                    Intent intent = new Intent(this, SavedFilesActivity.class);
                    intent.putExtra("open_search", true);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
        }
    }

    private void initializeUserAndLoadData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "No user found, signing in anonymously...");
            mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    Log.d(TAG, "signInAnonymously:success. UID: " + (user != null ? user.getUid() : "null"));
                    loadData();
                } else {
                    Exception e = task.getException();
                    Log.e(TAG, "signInAnonymously:failure", e);
                    String error = (e != null) ? e.getMessage() : "Unknown error";
                    
                    // Specific check for disabled Anonymous Auth
                    showSetupErrorDialog("Firebase Authentication Error", 
                        "The app failed to sign in anonymously. " +
                        "Please go to your Firebase Console > Authentication > Sign-in method and ENABLE 'Anonymous'.\n\nError: " + error);
                    
                    loadLocalData();
                }
            });
        } else {
            Log.d(TAG, "Existing user found: " + currentUser.getUid());
            loadData();
        }
    }

    private void showSetupErrorDialog(String title, String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Check Console", (dialog, which) -> {
                    // Just dismiss, but helpful instructions provided
                })
                .setNegativeButton("Work Offline", (dialog, which) -> {
                    Toast.makeText(this, "Operating in local mode only.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLocalData();
        if (mAuth.getCurrentUser() != null) {
            loadData();
        }
    }

    private void loadData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        databaseRef = FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes").child(currentUser.getUid());
        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cloudList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String text = data.child("text").getValue(String.class);
                    Long timestamp = data.child("timestamp").getValue(Long.class);
                    String key = data.getKey();
                    if (text != null && key != null) {
                        cloudList.add(text + LocalDatabaseHelper.SEPARATOR + (timestamp != null ? timestamp : 0L) + LocalDatabaseHelper.SEPARATOR + key);
                    }
                }
                updateUnifiedList();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error: " + error.getMessage());
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    showSetupErrorDialog("Database Permission Denied", 
                        "Firebase rejected the read request. Please check your Database Rules in the Firebase Console. They should allow authenticated users to read/write.");
                }
            }
        });
    }

    private void loadLocalData() {
        localList = localDb.getAllNotes();
        updateUnifiedList();
    }

    private void updateUnifiedList() {
        ArrayList<String> unifiedList = new ArrayList<>();
        unifiedList.addAll(cloudList);
        
        for (String localNote : localList) {
            boolean exists = false;
            String localId = LocalDatabaseHelper.splitNote(localNote)[2];
            for (String cloudNote : cloudList) {
                if (LocalDatabaseHelper.splitNote(cloudNote)[2].equals(localId)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) unifiedList.add(localNote);
        }

        Collections.sort(unifiedList, (a, b) -> {
            try {
                String[] p1 = LocalDatabaseHelper.splitNote(a);
                String[] p2 = LocalDatabaseHelper.splitNote(b);
                long t1 = Long.parseLong(p1[1]);
                long t2 = Long.parseLong(p2[1]);
                return Long.compare(t2, t1);
            } catch (Exception e) {
                return 0;
            }
        });

        adapter.updateData(unifiedList);
    }

    private void showDeleteDialog(String noteId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Note")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (noteId.startsWith("local_")) {
                        localDb.deleteNote(noteId);
                        loadLocalData();
                        Toast.makeText(this, "Local Note Deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        if (currentUser != null) {
                            FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes")
                                    .child(currentUser.getUid()).child(noteId).removeValue()
                                    .addOnSuccessListener(a -> Toast.makeText(this, "Cloud Note Deleted", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

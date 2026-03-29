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
    MaterialCardView cardUpload, cardTranslate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerNotes);
        cardUpload = findViewById(R.id.cardUpload);
        cardTranslate = findViewById(R.id.cardTranslate);
        topAppBar = findViewById(R.id.topAppBar);
        fabScan = findViewById(R.id.fabScan);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        localDb = new LocalDatabaseHelper(this);
        mAuth = FirebaseAuth.getInstance();

        adapter = new NoteAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        adapter.setOnItemLongClickListener(position -> {
            String item = adapter.getItem(position);
            String[] parts = item.split("\\|\\|");
            if (parts.length >= 3) {
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
            mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "signInAnonymously:success");
                    loadData();
                } else {
                    Log.e(TAG, "signInAnonymously:failure", task.getException());
                    // Provide a clearer message for debugging
                    String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    if (error.contains("blocked")) {
                        Toast.makeText(MainActivity.this, "Auth blocked. Please enable Identity Toolkit API in Google Cloud Console.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Authentication failed: " + error, Toast.LENGTH_SHORT).show();
                    }
                    // Load local data anyway so app is somewhat functional
                    loadLocalData();
                }
            });
        } else {
            loadData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLocalData();
    }

    private void loadData() {
        loadLocalData();
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
                        cloudList.add(text + "||" + (timestamp != null ? timestamp : 0L) + "||" + key);
                    }
                }
                updateUnifiedList();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, error.getMessage());
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
        unifiedList.addAll(localList);

        Collections.sort(unifiedList, (a, b) -> {
            try {
                long t1 = Long.parseLong(a.split("\\|\\|")[1]);
                long t2 = Long.parseLong(b.split("\\|\\|")[1]);
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
                                    .addOnSuccessListener(a -> Toast.makeText(this, "Cloud Note Deleted", Toast.LENGTH_SHORT).show());
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

package com.example.notex;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

public class NoteDetailActivity extends AppCompatActivity {

    TextView tvFullText;
    MaterialToolbar topAppBar;
    String noteId;
    String fullTextContent;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_note_detail);

        tvFullText = findViewById(R.id.tvFullText);
        topAppBar = findViewById(R.id.topAppBar);
        mAuth = FirebaseAuth.getInstance();

        fullTextContent = getIntent().getStringExtra("text");
        noteId = getIntent().getStringExtra("noteId");
        
        tvFullText.setText(fullTextContent);

        if (topAppBar != null) {
            topAppBar.setNavigationOnClickListener(v -> finish());

            topAppBar.inflateMenu(R.menu.note_detail_menu);
            topAppBar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_share) {
                    shareNote();
                    return true;
                } else if (item.getItemId() == R.id.action_delete) {
                    confirmDelete();
                    return true;
                }
                return false;
            });
        }
    }

    private void shareNote() {
        if (fullTextContent == null || fullTextContent.isEmpty()) return;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, fullTextContent);
        startActivity(Intent.createChooser(shareIntent, "Share note via"));
    }

    private void confirmDelete() {
        if (noteId == null) {
            Toast.makeText(this, "Cannot delete temporary note", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Note")
                .setMessage("Are you sure you want to delete this note?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (noteId.startsWith("local_")) {
                        new LocalDatabaseHelper(this).deleteNote(noteId);
                        Toast.makeText(this, "Local Note Deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            FirebaseDatabase.getInstance(Config.DB_URL)
                                    .getReference("notes")
                                    .child(user.getUid())
                                    .child(noteId)
                                    .removeValue()
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(this, "Cloud Note Deleted", Toast.LENGTH_SHORT).show();
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

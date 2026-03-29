package com.example.notex;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.Collections;

public class SavedFilesActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    ArrayList<String> cloudList = new ArrayList<>();
    ArrayList<String> localList = new ArrayList<>();
    NoteAdapter adapter;
    DatabaseReference ref;
    LocalDatabaseHelper localDb;
    MaterialToolbar topAppBar;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_saved_files);

        // INIT VIEWS
        recyclerView = findViewById(R.id.savedRecycler);
        topAppBar = findViewById(R.id.topAppBar);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        localDb = new LocalDatabaseHelper(this);
        mAuth = FirebaseAuth.getInstance();

        adapter = new NoteAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // HOLD TO SELECT MULTIPLE
        adapter.setOnItemLongClickListener(position -> {
            if (!adapter.isSelectionMode()) {
                adapter.setSelectionMode(true);
                adapter.toggleSelection(position);
                showSelectionMenu();
            }
        });

        adapter.setOnSelectionChangeListener(count -> {
            if (count == 0) {
                hideSelectionMenu();
            } else {
                topAppBar.setTitle(count + " Selected");
            }
        });

        loadData();
        setupNormalMenu();

        if (getIntent().getBooleanExtra("open_search", false)) {
            MenuItem searchItem = topAppBar.getMenu().findItem(R.id.action_search);
            if (searchItem != null) {
                searchItem.expandActionView();
            }
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

        ref = FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes").child(currentUser.getUid());
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (adapter.isSelectionMode()) return;
                
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
            public void onCancelled(DatabaseError error) {
                Log.e("SavedFiles", error.getMessage());
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

    private void setupNormalMenu() {
        topAppBar.setTitle("Saved Files");
        topAppBar.getMenu().clear();
        topAppBar.inflateMenu(R.menu.menu);
        
        // Setup Search
        MenuItem searchItem = topAppBar.getMenu().findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setQueryHint("Search all notes...");
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    adapter.filter(query);
                    return true;
                }
                @Override
                public boolean onQueryTextChange(String newText) {
                    adapter.filter(newText);
                    return true;
                }
            });

            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    return true;
                }
            });
        }

        topAppBar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                MenuItem sItem = topAppBar.getMenu().findItem(R.id.action_search);
                if (sItem != null && sItem.isActionViewExpanded()) {
                    sItem.collapseActionView();
                }
                finish();
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void showSelectionMenu() {
        topAppBar.getMenu().clear();
        topAppBar.inflateMenu(R.menu.saved_selection_menu);
        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete) {
                confirmDeleteSelected();
                return true;
            } else if (item.getItemId() == R.id.action_cancel) {
                hideSelectionMenu();
                return true;
            }
            return false;
        });
    }

    private void hideSelectionMenu() {
        adapter.setSelectionMode(false);
        setupNormalMenu();
    }

    private void confirmDeleteSelected() {
        ArrayList<Integer> positions = adapter.getSelectedPositions();
        new AlertDialog.Builder(this)
                .setTitle("Delete Notes")
                .setMessage("Are you sure you want to delete " + positions.size() + " notes?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    for (int pos : positions) {
                        String item = adapter.getItem(pos);
                        if (item == null) continue;
                        String[] parts = LocalDatabaseHelper.splitNote(item);
                        String key = parts[2];
                        if (key.startsWith("local_")) {
                            localDb.deleteNote(key);
                        } else if (currentUser != null) {
                            FirebaseDatabase.getInstance(Config.DB_URL).getReference("notes")
                                    .child(currentUser.getUid()).child(key).removeValue();
                        }
                    }
                    loadLocalData();
                    Toast.makeText(this, "Notes Deleted", Toast.LENGTH_SHORT).show();
                    hideSelectionMenu();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

/*
 * Copyright (c) 2006-2024 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.love2d.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Minimal stub so a freshly created project already runs without
    // erroring, instead of forcing the user to write boilerplate before
    // they can even open it in GameActivity.
    private static final String MAIN_LUA_STUB =
        "function love.load()\n" +
        "end\n" +
        "\n" +
        "function love.update(dt)\n" +
        "end\n" +
        "\n" +
        "function love.draw()\n" +
        "    love.graphics.print(\"Hello, LOVE!\", 400, 300)\n" +
        "end\n";

    private static final String CONF_LUA_STUB =
        "function love.conf(t)\n" +
        "    t.window.title = \"My LOVE Game\"\n" +
        "end\n";

    private final Executor executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> openFileLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        (Uri result) -> {
            if (result != null) {
                Intent intent = new Intent(this, GameActivity.class);
                intent.setData(result);
                startActivity(intent);
            }
        }
    );

    private GameListAdapter adapter;
    private ConstraintLayout noGameText;
    private SwipeRefreshLayout swipeLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        swipeLayout = findViewById(R.id.swipeRefreshLayout);
        noGameText = findViewById(R.id.constraintLayout);
        FloatingActionButton fabNewProject = findViewById(R.id.fabNewProject);

        adapter = new GameListAdapter();

        // Set refresh listener
        swipeLayout.setOnRefreshListener(() -> {
            scanGames(adapter, noGameText, swipeLayout);
        });

        // Set layout manager and adapter
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);

        fabNewProject.setOnClickListener(v -> getNewProjectDialog().show());

        scanGames(adapter, noGameText, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        // Handle item selection
        if (itemId == R.id.optionItem) {
            openFileLauncher.launch(new String[]{"*/*"});
            return true;
        } else if (itemId == R.id.optionItem2) {
            Intent intent = new Intent(this, GameActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.optionItem3) {
            getGameFolderDialog().show();
            return true;
        } else if (itemId == R.id.optionItem4) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private AlertDialog getGameFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.game_folder))
            .setPositiveButton(R.string.ok, (dialog1, which) -> {
            });
        StringBuilder message = new StringBuilder()
            .append(getString(R.string.game_folder_location, getPackageName()))
            .append("\n\n");

        if (Build.VERSION.SDK_INT >= 30) {
            message.append(getString(R.string.game_folder_inaccessible));
        } else {
            message.append(getString(R.string.game_folder_accessible));
        }

        return builder.setMessage(message.toString()).create();
    }

    private AlertDialog getNewProjectDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.new_project_name_hint);

        return new AlertDialog.Builder(this)
            .setTitle(R.string.new_project)
            .setView(input)
            .setPositiveButton(R.string.create, (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.new_project_name_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                createNewProject(name);
            })
            .setNegativeButton(R.string.close, null)
            .create();
    }

    private void createNewProject(String name) {
        executor.execute(() -> {
            File gamesDir = getExternalFilesDir("games");
            if (gamesDir == null) {
                return;
            }

            File projectDir = new File(gamesDir, name);

            if (projectDir.exists()) {
                runOnUiThread(() ->
                    Toast.makeText(this, R.string.new_project_already_exists, Toast.LENGTH_SHORT).show());
                return;
            }

            boolean created = projectDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create project directory: " + projectDir);
                runOnUiThread(() ->
                    Toast.makeText(this, R.string.new_project_creation_failed, Toast.LENGTH_SHORT).show());
                return;
            }

            try {
                writeStubFile(new File(projectDir, "main.lua"), MAIN_LUA_STUB);
                writeStubFile(new File(projectDir, "conf.lua"), CONF_LUA_STUB);
            } catch (IOException e) {
                Log.e(TAG, "Failed to write stub files", e);
                runOnUiThread(() ->
                    Toast.makeText(this, R.string.new_project_creation_failed, Toast.LENGTH_SHORT).show());
                return;
            }

            runOnUiThread(() -> scanGames(adapter, noGameText, swipeLayout));
        });
    }

    private static void writeStubFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void scanGames(GameListAdapter adapter, ConstraintLayout noGameText, SwipeRefreshLayout swipeRefreshLayout) {
        executor.execute(() -> {
            File extDir = getExternalFilesDir("games");

            if (extDir != null) {
                if (!extDir.isDirectory()) {
                    if (!extDir.mkdir()) {
                        // Scan failure, abort
                        runOnUiThread(() -> {
                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        });

                        Log.e(TAG, "Directory creation failure.");
                        return;
                    }
                }

                ArrayList<GameListAdapter.Data> validGames = new ArrayList<>();
                File[] files = extDir.listFiles();

                if (files != null) {
                    for (File file : files) {
                        GameListAdapter.Data gameData = null;

                        if (file.isDirectory()) {
                            if (isValidGamedir(file)) {
                                gameData = new GameListAdapter.Data();
                                gameData.path = file;
                                gameData.directory = true;
                            }
                        } else {
                            if (isValidLovegame(file)) {
                                gameData = new GameListAdapter.Data();
                                gameData.path = file;
                                gameData.directory = false;
                            }
                        }

                        if (gameData != null) {
                            validGames.add(gameData);
                        }
                    }
                }

                boolean empty = validGames.isEmpty();

                runOnUiThread(() -> {
                    if (empty) {
                        adapter.setData(null);
                    } else {
                        GameListAdapter.Data[] gameDatas = new GameListAdapter.Data[validGames.size()];
                        validGames.toArray(gameDatas);
                        adapter.setData(gameDatas);
                    }

                    adapter.notifyDataSetChanged();

                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    noGameText.setVisibility(empty ? View.VISIBLE : View.INVISIBLE);
                });
            }
        });
    }

    public static boolean isValidLovegame(File file) {
        boolean valid = false;

        try {
            ZipFile zip = new ZipFile(file, ZipFile.OPEN_READ);
            valid = zip.getEntry("main.lua") != null;
            zip.close();
        } catch (IOException ignored) {
        }

        return valid;
    }

    public static boolean isValidGamedir(File file) {
        File mainLua = new File(file, "main.lua");
        return mainLua.isFile();
    }
}

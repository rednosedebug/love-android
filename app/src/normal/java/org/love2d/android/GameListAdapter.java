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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GameListAdapter extends RecyclerView.Adapter<GameListAdapter.ViewHolder> {
    private static final String TAG = "GameListAdapter";

    private Data[] data = null;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_game, parent, false);
        ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(holder);
        view.setOnLongClickListener(holder);

        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setData(this.data[position]);
    }

    @Override
    public int getItemCount() {
        return data != null ? data.length : 0;
    }

    public void setData(Data[] data) {
        this.data = data;
    }

    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final TextView name;
        private final ImageView image;
        private final ImageButton menuButton;
        private File file;

        public ViewHolder(View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.textView);
            image = itemView.findViewById(R.id.imageView);
            menuButton = itemView.findViewById(R.id.rowMenuButton);

            menuButton.setOnClickListener(this::showRowMenu);
        }

        public void setData(Data data) {
            name.setText(data.path.getName());
            image.setImageResource(data.directory ? R.drawable.ic_baseline_folder_32 : R.drawable.ic_baseline_insert_drive_file_32);
            file = data.path;
        }

        @Override
        public void onClick(View v) {
            if (file == null) {
                return;
            }

            Context context = v.getContext();
            Intent intent = new Intent(context, GameActivity.class);
            intent.setData(Uri.fromFile(file));
            context.startActivity(intent);
        }

        @Override
        public boolean onLongClick(View v) {
            if (file == null) {
                return false;
            }

            Context context = v.getContext();
            String fullPath = file.getAbsolutePath();

            new AlertDialog.Builder(context)
                .setTitle(file.getName())
                .setMessage(fullPath)
                .setPositiveButton(R.string.copy_path, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("game_path", fullPath);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();

            return true;
        }

        private void showRowMenu(View anchor) {
            if (file == null) {
                return;
            }

            Context context = anchor.getContext();
            PopupMenu popup = new PopupMenu(context, anchor);
            popup.getMenu().add(0, 1, 0, R.string.row_menu_backup);
            popup.getMenu().add(0, 2, 1, R.string.row_menu_create_love);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) {
                    backupProject(context, file);
                    return true;
                } else if (id == 2) {
                    createLoveFile(context, file);
                    return true;
                }
                return false;
            });

            popup.show();
        }

        // Zips the given project directory (or copies the file directly, if
        // it's already a single .love file) to a name the user picks via
        // the system's Storage Access Framework, so it lands somewhere
        // outside the app's private storage that survives uninstalling
        // the app.
        private void backupProject(Context context, File source) {
            if (!(context instanceof Activity)) {
                return;
            }

            try {
                File outFile = File.createTempFile("love_backup_", ".zip", context.getCacheDir());
                zipDirectory(source, outFile);

                Toast.makeText(context, context.getString(R.string.row_menu_backup_done, outFile.getName()),
                    Toast.LENGTH_LONG).show();

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/zip");
                Uri uri = Uri.fromFile(outFile);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.row_menu_backup)));
            } catch (IOException e) {
                Log.e(TAG, "Backup failed", e);
                Toast.makeText(context, R.string.row_menu_backup_failed, Toast.LENGTH_SHORT).show();
            }
        }

        // Packages a project directory into a .love file (which is just a
        // zip with main.lua at its root) sitting next to the original
        // folder in the games directory, then launches it immediately.
        private void createLoveFile(Context context, File source) {
            if (!source.isDirectory()) {
                // Already a .love/zip file; nothing to package.
                Toast.makeText(context, R.string.row_menu_already_love, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                File outFile = new File(source.getParentFile(), source.getName() + ".love");
                zipDirectory(source, outFile);

                Intent intent = new Intent(context, GameActivity.class);
                intent.setData(Uri.fromFile(outFile));
                context.startActivity(intent);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create .love file", e);
                Toast.makeText(context, R.string.row_menu_create_love_failed, Toast.LENGTH_SHORT).show();
            }
        }

        private static void zipDirectory(File source, File outFile) throws IOException {
            try (OutputStream fos = new FileOutputStream(outFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                if (source.isDirectory()) {
                    zipDirectoryContents(source, source, zos);
                } else {
                    addFileToZip(source, source.getName(), zos);
                }
            }
        }

        private static void zipDirectoryContents(File root, File current, ZipOutputStream zos) throws IOException {
            File[] children = current.listFiles();
            if (children == null) {
                return;
            }

            for (File child : children) {
                String relativePath = root.toURI().relativize(child.toURI()).getPath();
                if (child.isDirectory()) {
                    zipDirectoryContents(root, child, zos);
                } else {
                    addFileToZip(child, relativePath, zos);
                }
            }
        }

        private static void addFileToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
            try (InputStream fis = new FileInputStream(file)) {
                zos.putNextEntry(new ZipEntry(entryName));

                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }

                zos.closeEntry();
            }
        }
    }

    static class Data {
        // Absolute path of the file.
        public File path;
        // Denote if this game is a directory.
        public boolean directory;
    }
}

// Learn more about Tauri commands at https://tauri.app/develop/calling-rust/
use serde::Serialize;
use std::fs;
use std::path::Path;
use std::time::UNIX_EPOCH;

#[derive(Serialize)]
pub struct ProjectInfo {
    name: String,
    path: String,
    last_modified: u64,
    preview_image: Option<String>,
}

#[tauri::command]
fn scan_projects(dir_path: &str) -> Vec<ProjectInfo> {
    let mut projects = Vec::new();
    let root = Path::new(dir_path);

    if !root.exists() || !root.is_dir() {
        return projects;
    }

    let mut queue = vec![(root.to_path_buf(), 0)];
    let max_depth = 2;

    while let Some((current_dir, depth)) = queue.pop() {
        if depth > max_depth {
            continue;
        }

        if let Ok(entries) = fs::read_dir(&current_dir) {
            for entry in entries.flatten() {
                let path = entry.path();

                if path.is_dir() {
                    if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                        if !name.starts_with('.') {
                            queue.push((path, depth + 1));
                        }
                    }
                } else if path.is_file() {
                    if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
                        if ext == "uproject" {
                            let name = path
                                .file_stem()
                                .and_then(|n| n.to_str())
                                .unwrap_or("Unknown")
                                .to_string();

                            let mut last_modified = 0;
                            if let Ok(metadata) = entry.metadata() {
                                if let Ok(modified) = metadata.modified() {
                                    if let Ok(duration) = modified.duration_since(UNIX_EPOCH) {
                                        last_modified = duration.as_secs();
                                    }
                                }
                            }

                            let normalized_path =
                                path.to_string_lossy().to_string().replace('\\', "/");

                            // Check for ProjectPreview.png or ProjectPreview.PNG
                            let mut preview_image = None;
                            let png_path = current_dir.join("ProjectPreview.png");
                            let upper_png_path = current_dir.join("ProjectPreview.PNG");

                            if png_path.exists() {
                                preview_image =
                                    Some(png_path.to_string_lossy().to_string().replace('\\', "/"));
                            } else if upper_png_path.exists() {
                                preview_image = Some(
                                    upper_png_path
                                        .to_string_lossy()
                                        .to_string()
                                        .replace('\\', "/"),
                                );
                            }

                            projects.push(ProjectInfo {
                                name,
                                path: normalized_path,
                                last_modified,
                                preview_image,
                            });
                        }
                    }
                }
            }
        }
    }

    projects.sort_by(|a, b| b.last_modified.cmp(&a.last_modified));
    projects
}

use tauri::Emitter;
use tauri_plugin_opener::OpenerExt;

#[tauri::command]
fn rename_project(old_path: &str, new_name: &str) -> Result<String, String> {
    let old_file = Path::new(old_path);
    if !old_file.exists() {
        return Err("Project file does not exist".to_string());
    }

    let parent_dir = old_file.parent().ok_or("Cannot find parent directory")?;
    let old_name = old_file.file_stem().and_then(|n| n.to_str()).unwrap_or("");

    // Check if the parent dir has the exact same name as the .uproject file
    let parent_dir_name = parent_dir
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("");

    if parent_dir_name == old_name {
        // We need to rename the parent folder as well
        let grand_parent = parent_dir
            .parent()
            .ok_or("Cannot find grandparent directory")?;
        let new_parent_dir = grand_parent.join(new_name);

        fs::rename(parent_dir, &new_parent_dir).map_err(|e| e.to_string())?;

        // Inside the new folder, rename the .uproject file
        let new_old_file_path = new_parent_dir.join(format!("{}.uproject", old_name));
        let final_new_file_path = new_parent_dir.join(format!("{}.uproject", new_name));

        if new_old_file_path.exists() {
            fs::rename(&new_old_file_path, &final_new_file_path).map_err(|e| e.to_string())?;
        }

        return Ok(final_new_file_path
            .to_string_lossy()
            .to_string()
            .replace('\\', "/"));
    } else {
        // Just rename the .uproject file
        let new_file_path = parent_dir.join(format!("{}.uproject", new_name));
        fs::rename(old_file, &new_file_path).map_err(|e| e.to_string())?;

        return Ok(new_file_path
            .to_string_lossy()
            .to_string()
            .replace('\\', "/"));
    }
}

#[tauri::command]
fn open_project(app: tauri::AppHandle, path: &str) -> Result<(), String> {
    app.opener()
        .open_path(path.to_string(), None::<&str>)
        .map_err(|e| e.to_string())
}

#[derive(Clone, serde::Serialize)]
struct ProjectClosedPayload {
    path: String,
}

#[tauri::command]
async fn run_project(app: tauri::AppHandle, path: String) -> Result<(), String> {
    let path_clone = path.clone();

    tauri::async_runtime::spawn(async move {
        // `cmd /c start /wait` may return immediately if the default handler (UnrealVersionSelector)
        // just hands off the process to UnrealEditor.exe.
        // We'll try using PowerShell's Start-Process with -Wait.
        let mut child = match std::process::Command::new("powershell")
            .args([
                "-Command",
                &format!("Start-Process -FilePath \"{}\" -Wait", path_clone),
            ])
            .spawn()
        {
            Ok(c) => c,
            Err(e) => {
                eprintln!("Failed to spawn process: {}", e);
                return;
            }
        };

        // Wait for it to finish.
        let _ = child.wait();

        // Process finished
        let _ = app.emit(
            "project-closed",
            ProjectClosedPayload {
                path: path_clone.clone(),
            },
        );
    });

    Ok(())
}

#[tauri::command]
fn clone_project(
    source_uproject_path: &str,
    target_dir: &str,
    new_name: &str,
) -> Result<String, String> {
    let source_file = Path::new(source_uproject_path);
    if !source_file.exists() {
        return Err("Source project file does not exist".to_string());
    }

    let source_dir = source_file.parent().ok_or("Cannot find source directory")?;
    let target_root = Path::new(target_dir);
    let target_project_dir = target_root.join(new_name);

    if target_project_dir.exists() {
        return Err(
            "A folder with the new project name already exists in the target directory".to_string(),
        );
    }

    // Create new project directory
    fs::create_dir_all(&target_project_dir).map_err(|e| e.to_string())?;

    // Folders to copy
    let folders_to_copy = ["Config", "Content", "Plugins"];

    for folder in folders_to_copy.iter() {
        let src_folder = source_dir.join(folder);
        if src_folder.exists() && src_folder.is_dir() {
            let dst_folder = target_project_dir.join(folder);
            copy_dir_all(&src_folder, &dst_folder)
                .map_err(|e| format!("Failed to copy {}: {}", folder, e))?;
        }
    }

    // Copy and rename the .uproject file
    let new_uproject_file = target_project_dir.join(format!("{}.uproject", new_name));
    fs::copy(source_file, &new_uproject_file).map_err(|e| e.to_string())?;

    Ok(new_uproject_file
        .to_string_lossy()
        .to_string()
        .replace('\\', "/"))
}

// Helper function to recursively copy a directory
fn copy_dir_all(src: impl AsRef<Path>, dst: impl AsRef<Path>) -> std::io::Result<()> {
    fs::create_dir_all(&dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let ty = entry.file_type()?;
        if ty.is_dir() {
            copy_dir_all(entry.path(), dst.as_ref().join(entry.file_name()))?;
        } else {
            fs::copy(entry.path(), dst.as_ref().join(entry.file_name()))?;
        }
    }
    Ok(())
}

#[derive(Serialize)]
pub struct TemplateInfo {
    category: String,
    name: String,
    path: String,
    preview_image: Option<String>,
}

#[tauri::command]
fn get_templates(dir_path: &str) -> Vec<TemplateInfo> {
    let mut templates = Vec::new();
    let root = Path::new(dir_path);

    if !root.exists() || !root.is_dir() {
        return templates;
    }

    // Look for .uproject files up to depth 2 directly
    let mut queue = vec![(root.to_path_buf(), 0)];
    let max_depth = 2;

    while let Some((current_dir, depth)) = queue.pop() {
        if depth > max_depth {
            continue;
        }

        if let Ok(entries) = fs::read_dir(&current_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                        if !name.starts_with('.') {
                            queue.push((path, depth + 1));
                        }
                    }
                } else if path.is_file() {
                    if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
                        if ext == "uproject" {
                            let name = path
                                .file_stem()
                                .and_then(|n| n.to_str())
                                .unwrap_or("Unknown")
                                .to_string();

                            let normalized_path =
                                path.to_string_lossy().to_string().replace('\\', "/");

                            // Check for ProjectPreview.png or ProjectPreview.PNG
                            let mut preview_image = None;
                            let png_path = current_dir.join("ProjectPreview.png");
                            let upper_png_path = current_dir.join("ProjectPreview.PNG");

                            if png_path.exists() {
                                preview_image =
                                    Some(png_path.to_string_lossy().to_string().replace('\\', "/"));
                            } else if upper_png_path.exists() {
                                preview_image = Some(
                                    upper_png_path
                                        .to_string_lossy()
                                        .to_string()
                                        .replace('\\', "/"),
                                );
                            }

                            templates.push(TemplateInfo {
                                category: "铁路数字孪生".to_string(), // Fixed category per user's request
                                name,
                                path: normalized_path,
                                preview_image,
                            });
                        }
                    }
                }
            }
        }
    }

    templates
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            scan_projects,
            rename_project,
            open_project,
            run_project,
            clone_project,
            get_templates
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

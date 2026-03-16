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

// Helper function to resolve paths
fn get_base_dir() -> std::path::PathBuf {
    #[cfg(debug_assertions)]
    {
        // In dev mode, current_dir is usually UnrealEditor or UnrealEditor/src-tauri
        let mut dir = std::env::current_dir().unwrap_or_else(|_| std::path::PathBuf::from("."));
        if dir.file_name().and_then(|n| n.to_str()) == Some("src-tauri") {
            dir.pop();
        }
        // One level above editor is the parent
        dir.parent().unwrap_or(&dir).to_path_buf()
    }
    #[cfg(not(debug_assertions))]
    {
        // In prod mode, current_exe is UnrealEditor.exe inside UnrealEditor dir
        let exe_path = std::env::current_exe().unwrap_or_else(|_| std::env::current_dir().unwrap());
        let exe_dir = exe_path.parent().unwrap_or_else(|| std::path::Path::new(""));
        // One level above editor is the parent
        exe_dir.parent().unwrap_or(exe_dir).to_path_buf()
    }
}

#[tauri::command]
fn scan_projects() -> Vec<ProjectInfo> {
    let mut projects = Vec::new();
    let base_dir = get_base_dir();
    let root = base_dir.join("UnrealProject");

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
                            if let Ok(metadata) = fs::metadata(&current_dir) {
                                if let Ok(modified) = metadata.modified() {
                                    if let Ok(duration) = modified.duration_since(UNIX_EPOCH) {
                                        last_modified = duration.as_secs();
                                    }
                                }
                            }

                            let normalized_path =
                                path.to_string_lossy().to_string().replace('\\', "/");

                            // Only check for Saved/AutoScreenshot.png
                            let mut preview_image = None;
                            let screenshot_path =
                                current_dir.join("Saved").join("AutoScreenshot.png");

                            if screenshot_path.exists() {
                                preview_image = Some(
                                    screenshot_path
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
        return Err("项目文件不存在".to_string());
    }

    let parent_dir = old_file.parent().ok_or("找不到父目录")?;
    let old_name = old_file.file_stem().and_then(|n| n.to_str()).unwrap_or("");

    // Check if the parent dir has the exact same name as the .uproject file
    let parent_dir_name = parent_dir
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("");

    if parent_dir_name == old_name {
        // Renaming folder and file
        let grand_parent = parent_dir
            .parent()
            .ok_or("找不到上级目录")?;
        let new_parent_dir = grand_parent.join(new_name);

        if new_parent_dir.exists() {
            return Err("目标目录已存在，请更换名称".to_string());
        }

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
        // Just renaming the .uproject file
        let new_file_path = parent_dir.join(format!("{}.uproject", new_name));
        
        if new_file_path.exists() {
            return Err("同名项目文件已存在".to_string());
        }

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
fn check_project_folder_exists(name: &str) -> bool {
    let base_dir = get_base_dir();
    let target_dir = base_dir.join("UnrealProject").join(name);
    target_dir.exists() && target_dir.is_dir()
}

#[tauri::command]
async fn run_project(app: tauri::AppHandle, path: String) -> Result<(), String> {
    let path_clone = path.clone();

    tauri::async_runtime::spawn(async move {
        // `cmd /c start /wait` may return immediately if the default handler (UnrealVersionSelector)
        // just hands off the process to UnrealEditor.exe.
        // We'll try using PowerShell's Start-Process with -Wait.
        let mut cmd = std::process::Command::new("powershell");
        cmd.args([
            "-Command",
            &format!("Start-Process -FilePath \"{}\" -Wait", path_clone),
        ]);

        #[cfg(target_os = "windows")]
        {
            use std::os::windows::process::CommandExt;
            cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
        }

        let mut child = match cmd.spawn() {
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
    target_dir: &str, // If empty, defaults to UnrealProject
    new_name: &str,
) -> Result<String, String> {
    let source_file = Path::new(source_uproject_path);
    if !source_file.exists() {
        return Err("源项目文件不存在".to_string());
    }

    let source_dir = source_file.parent().ok_or("找不到源目录")?;
    
    let target_project_dir = if target_dir.is_empty() {
        let base_dir = get_base_dir();
        base_dir.join("UnrealProject").join(new_name)
    } else {
        Path::new(target_dir).join(new_name)
    };

    if target_project_dir.exists() {
        return Err(
            "目标目录下已存在同名文件夹，请更换项目名称".to_string(),
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
fn get_templates() -> Vec<TemplateInfo> {
    let mut templates = Vec::new();
    let base_dir = get_base_dir();
    let root = base_dir.join("Templates");

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

                            // Dynamic category detection
                            // If templates/CategoryA/Project/file.uproject, depth will be 2
                            // root is level 0. subfolder is level 1. project folder is level 2.
                            let mut category = "站场数字孪生".to_string();
                            if depth >= 1 {
                                // Try to get the folder name right under root
                                let relative = path.strip_prefix(&root).ok();
                                if let Some(rel) = relative {
                                    if let Some(cat) = rel.components().next() {
                                        let cat_str = cat.as_os_str().to_string_lossy().to_string();
                                        // Only use it if it's not the uproject file itself (i.e. it's a directory)
                                        if root.join(&cat_str).is_dir() {
                                            category = cat_str;
                                        }
                                    }
                                }
                            }

                            // Only check for Saved/AutoScreenshot.png
                            let mut preview_image = None;
                            let screenshot_path =
                                current_dir.join("Saved").join("AutoScreenshot.png");

                            if screenshot_path.exists() {
                                preview_image = Some(
                                    screenshot_path
                                        .to_string_lossy()
                                        .to_string()
                                        .replace('\\', "/"),
                                );
                            }

                            templates.push(TemplateInfo {
                                category,
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
            get_templates,
            check_project_folder_exists
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

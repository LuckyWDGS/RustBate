// Learn more about Tauri commands at https://tauri.app/develop/calling-rust/
use serde::Serialize;
use std::fs;
use std::path::Path;
use std::time::UNIX_EPOCH;
use std::io::BufReader;
use std::sync::atomic::{AtomicBool, Ordering};
use winreg::enums::*;
use winreg::RegKey;
use sysinfo::Disks;
use serde_json::Value;
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

fn clean_ghost_projects() {
    let base_dir = get_base_dir();
    let root = base_dir.join("UnrealProject");
    if !root.exists() || !root.is_dir() { return; }

    let now = std::time::SystemTime::now();

    if let Ok(entries) = fs::read_dir(&root) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
                    if name.starts_with('.') && (name.ends_with("_cloning") || name.ends_with("_deleting")) {
                        // Only delete if it's older than 60 seconds to avoid race conditions with active tasks
                        if let Ok(metadata) = entry.metadata() {
                            if let Ok(modified) = metadata.modified() {
                                if let Ok(el) = now.duration_since(modified) {
                                    if el.as_secs() < 60 {
                                        continue;
                                    }
                                }
                            }
                        }
                        // Silent cleanup
                        let _ = fs::remove_dir_all(&path);
                    }
                }
            }
        }
    }
}

#[tauri::command]
fn scan_projects() -> Vec<ProjectInfo> {
    std::thread::spawn(clean_ghost_projects);
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
                            let content_dir = current_dir.join("Content");
                            let path_to_check = if content_dir.exists() { &content_dir } else { &current_dir };
                            
                            if let Ok(metadata) = fs::metadata(path_to_check) {
                                if let Ok(modified) = metadata.modified() {
                                    if let Ok(duration) = modified.duration_since(UNIX_EPOCH) {
                                        last_modified = duration.as_secs();
                                    }
                                }
                            }

                            let normalized_path =
                                path.to_string_lossy().to_string().replace('\\', "/");

                            // Prioritize ProjectPreview.png, then Saved/AutoScreenshot.png
                            let mut preview_image = None;
                            let project_preview_path = current_dir.join("ProjectPreview.png");
                            let screenshot_path =
                                current_dir.join("Saved").join("AutoScreenshot.png");

                            if project_preview_path.exists() {
                                preview_image = Some(
                                    project_preview_path
                                        .to_string_lossy()
                                        .to_string()
                                        .replace('\\', "/"),
                                );
                            } else if screenshot_path.exists() {
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
fn validate_project_name(path: Option<String>, new_name: String) -> Result<(), String> {
    let lower_name = new_name.to_lowercase();
    let reserved = [
        "arts", "saved", "content", "intermediate", "binaries", "config", "deriveddatacache",
        "source", "plugins", "build", "script",
    ];

    if reserved.contains(&lower_name.as_str()) {
        return Err("当前命名为引擎或规范保留关键字(如Arts, Content, Saved等)，请更换命名".to_string());
    }

    let base_dir = get_base_dir();
    let target_dir = base_dir.join("UnrealProject").join(&new_name);
    if target_dir.exists() && target_dir.is_dir() {
        return Err("目录下已存在同名项目".to_string());
    }

    if let Some(p) = path {
        let uproject_path = Path::new(&p);
        if let Some(parent) = uproject_path.parent() {
            let content_dir = parent.join("Content");
            if content_dir.exists() && content_dir.is_dir() {
                let conflict_dir = content_dir.join(&new_name);
                if conflict_dir.exists() && conflict_dir.is_dir() {
                    return Err("项目 Content 目录下已存在同名文件夹，请更换命名".to_string());
                }
            }
        }
    }

    Ok(())
}


fn check_engine_installed(engine_association: &str) -> bool {
    if engine_association.is_empty() {
        return true;
    }
    
    // Check HKLM
    let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
    if let Ok(_epic_games) = hklm.open_subkey(format!("SOFTWARE\\EpicGames\\Unreal Engine\\{}", engine_association)) {
        return true;
    }

    // Check HKCU
    let hkcu = RegKey::predef(HKEY_CURRENT_USER);
    if let Ok(builds) = hkcu.open_subkey("SOFTWARE\\Epic Games\\Unreal Engine\\Builds") {
        if builds.get_value::<String, _>(engine_association).is_ok() {
            return true;
        }
    }

    false
}

#[tauri::command]
async fn run_project(app: tauri::AppHandle, path: String) -> Result<(), String> {
    let file = fs::File::open(&path).map_err(|e| format!("无法读取项目文件: {}", e))?;
    let reader = BufReader::new(file);
    if let Ok(uproject) = serde_json::from_reader::<_, Value>(reader) {
        if let Some(engine_assoc) = uproject.get("EngineAssociation").and_then(|v| v.as_str()) {
            if !check_engine_installed(engine_assoc) {
                return Err(format!("ENGINE_MISSING:{}", engine_assoc));
            }
        }
    }

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
                log::error!("Failed to spawn process: {}", e);
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
async fn delete_project(path: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let uproject_path = Path::new(&path);
        if !uproject_path.exists() {
            return Err("项目文件不存在".to_string());
        }

        let project_dir = uproject_path.parent().ok_or("找不到项目目录")?;
        
        // 1. Rename to ._deleting for atomicity
        let mut tmp_name = project_dir.file_name().unwrap_or_default().to_os_string();
        tmp_name.push("_deleting");
        let mut hidden_tmp_name = std::ffi::OsString::from(".");
        hidden_tmp_name.push(&tmp_name);
        
        let deleting_dir = project_dir.with_file_name(hidden_tmp_name);
        
        let target_to_delete = match fs::rename(project_dir, &deleting_dir) {
            Ok(_) => deleting_dir,
            Err(_) => project_dir.to_path_buf(), // fallback to original if rename fails
        };
        
        // Actual expensive deletion
        fs::remove_dir_all(&target_to_delete).map_err(|e| format!("删除失败: {}", e))?;
        
        Ok(())
    }).await.map_err(|e| format!("线程执行失败: {}", e))?
}

static CANCEL_CLONE_FLAG: AtomicBool = AtomicBool::new(false);

#[tauri::command]
fn cancel_clone() {
    CANCEL_CLONE_FLAG.store(true, Ordering::SeqCst);
}

#[derive(Clone, serde::Serialize)]
struct CloneProgressPayload {
    current: u64,
    total: u64,
}

fn calculate_dir_size(path: impl AsRef<Path>) -> std::io::Result<u64> {
    let mut total = 0;
    for entry in fs::read_dir(path)? {
        let entry = entry?;
        let metadata = entry.metadata()?;
        if metadata.is_dir() {
            total += calculate_dir_size(entry.path())?;
        } else {
            total += metadata.len();
        }
    }
    Ok(total)
}

fn copy_dir_all_with_progress(
    src: impl AsRef<Path>,
    dst: impl AsRef<Path>,
    app: &tauri::AppHandle,
    total_bytes: u64,
    copied_bytes: &mut u64,
    last_emit_time: &mut std::time::Instant,
) -> std::io::Result<()> {
    if CANCEL_CLONE_FLAG.load(Ordering::SeqCst) {
        return Err(std::io::Error::new(std::io::ErrorKind::Interrupted, "CANCELLED"));
    }
    
    fs::create_dir_all(&dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let ty = entry.file_type()?;
        if ty.is_dir() {
            copy_dir_all_with_progress(
                entry.path(),
                dst.as_ref().join(entry.file_name()),
                app,
                total_bytes,
                copied_bytes,
                last_emit_time,
            )?;
        } else {
            let src_file = entry.path();
            let dst_file = dst.as_ref().join(entry.file_name());
            
            // Chunked copy to emit progress cleanly for large files
            let mut src_handle = fs::File::open(&src_file)?;
            let mut dst_handle = fs::File::create(&dst_file)?;
            let mut buffer = [0; 65536]; // 64KB chunks
            use std::io::{Read, Write};
            
            loop {
                if CANCEL_CLONE_FLAG.load(Ordering::SeqCst) {
                    return Err(std::io::Error::new(std::io::ErrorKind::Interrupted, "CANCELLED"));
                }
                
                let n = src_handle.read(&mut buffer)?;
                if n == 0 { break; }
                dst_handle.write_all(&buffer[..n])?;
                *copied_bytes += n as u64;
                
                // throttle emit to ~100ms
                if last_emit_time.elapsed().as_millis() > 100 {
                    let _ = app.emit("clone-progress", CloneProgressPayload {
                        current: *copied_bytes,
                        total: total_bytes,
                    });
                    *last_emit_time = std::time::Instant::now();
                }
            }
        }
    }
    Ok(())
}

#[tauri::command]
async fn clone_project(
    app: tauri::AppHandle,
    source_uproject_path: String,
    target_dir: String, // If empty, defaults to UnrealProject
    new_name: String,
) -> Result<String, String> {
    let result = tauri::async_runtime::spawn_blocking(move || {
        let source_file = Path::new(&source_uproject_path);
        if !source_file.exists() {
            return Err("源项目文件不存在".to_string());
        }

        let source_dir = source_file.parent().ok_or("找不到源目录")?;
        
        let final_target_dir = if target_dir.is_empty() {
            let base_dir = get_base_dir();
            base_dir.join("UnrealProject").join(&new_name)
        } else {
            Path::new(&target_dir).join(&new_name)
        };

        // Hidden temp dir for clone
        let mut cloning_name = std::ffi::OsString::from(".");
        cloning_name.push(&new_name);
        cloning_name.push("_cloning");
        let target_project_dir = final_target_dir.with_file_name(cloning_name);

        if final_target_dir.exists() || target_project_dir.exists() {
            return Err(
                "目标目录下已存在同名文件夹或正在克隆，请更换项目名称".to_string(),
            );
        }

        let folders_to_copy = ["Config", "Content", "Plugins"];
        
        // 1. Calculate total size
        let mut total_bytes = 0;
        for folder in folders_to_copy.iter() {
            let src_folder = source_dir.join(folder);
            if src_folder.exists() && src_folder.is_dir() {
                if let Ok(size) = calculate_dir_size(&src_folder) {
                    total_bytes += size;
                }
            }
        }
        if let Ok(meta) = fs::metadata(source_file) {
            total_bytes += meta.len();
        }

        // PRE-CHECK Disk Space
        let disks = Disks::new_with_refreshed_list();
        let mut matching_disk = None;
        let mut longest_match = 0;
        let c_project_dir = match dunce::canonicalize(&target_project_dir) {
            Ok(p) => p,
            Err(_) => target_project_dir.clone(), // fallback if parent doesn't exist
        };
        for disk in disks.list() {
            let mount = disk.mount_point();
            if c_project_dir.starts_with(mount) || target_project_dir.starts_with(mount) {
                let mount_len = mount.as_os_str().len();
                if mount_len > longest_match {
                    longest_match = mount_len;
                    matching_disk = Some(disk);
                }
            }
        }

        if let Some(disk) = matching_disk {
            if disk.available_space() < total_bytes + (1024 * 1024 * 50) { // Add 50MB padding just in case
                let req_gb = total_bytes as f64 / 1_073_741_824.0;
                let av_gb = disk.available_space() as f64 / 1_073_741_824.0;
                return Err(format!("目标磁盘空间不足！剩余 {:.2} GB，但克隆至少需要 {:.2} GB，请清理磁盘后重试。", av_gb, req_gb));
            }
        }

        // Create new project directory
        fs::create_dir_all(&target_project_dir).map_err(|e| e.to_string())?;

        let mut copied_bytes = 0;
        let mut last_emit_time = std::time::Instant::now();
        
        // Reset cancel
        CANCEL_CLONE_FLAG.store(false, Ordering::SeqCst);

        // Send an initial 0% progress
        let _ = app.emit("clone-progress", CloneProgressPayload {
            current: 0,
            total: total_bytes,
        });

        // 2. Copy folders with progress
        for folder in folders_to_copy.iter() {
            let src_folder = source_dir.join(folder);
            if src_folder.exists() && src_folder.is_dir() {
                let dst_folder = target_project_dir.join(folder);
                let copy_res = copy_dir_all_with_progress(
                    &src_folder, 
                    &dst_folder, 
                    &app, 
                    total_bytes, 
                    &mut copied_bytes, 
                    &mut last_emit_time
                );
                
                if let Err(e) = copy_res {
                    if e.kind() == std::io::ErrorKind::Interrupted {
                        let _ = fs::remove_dir_all(&target_project_dir); // Cleanup
                        return Err("CANCELLED".to_string());
                    }
                    return Err(format!("Failed to copy {}: {}", folder, e));
                }
            }
        }

        // Copy and rename the .uproject file
        let new_uproject_file = target_project_dir.join(format!("{}.uproject", new_name));
        
        {
            let mut src_handle = fs::File::open(source_file).map_err(|e| e.to_string())?;
            let mut dst_handle = fs::File::create(&new_uproject_file).map_err(|e| e.to_string())?;
            let mut buffer = [0; 65536];
            use std::io::{Read, Write};
            loop {
                if CANCEL_CLONE_FLAG.load(Ordering::SeqCst) {
                        let _ = fs::remove_dir_all(&target_project_dir); // Cleanup
                        return Err("CANCELLED".to_string());
                }

                let n = src_handle.read(&mut buffer).map_err(|e| e.to_string())?;
                if n == 0 { break; }
                dst_handle.write_all(&buffer[..n]).map_err(|e| e.to_string())?;
                copied_bytes += n as u64;
                
                if last_emit_time.elapsed().as_millis() > 100 {
                    let _ = app.emit("clone-progress", CloneProgressPayload {
                        current: copied_bytes,
                        total: total_bytes,
                    });
                    last_emit_time = std::time::Instant::now();
                }
            }
            // Explicit drop is good practice but scope also works
            drop(src_handle);
            drop(dst_handle);
        }
        
        // Final 100% emit
        let _ = app.emit("clone-progress", CloneProgressPayload {
            current: total_bytes,
            total: total_bytes,
        });

        // ATOMIC RENAME: The project is fully constructed. Let's make it visible.
        // On Windows, Antivirus or Indexer might lock files even after handles are closed.
        // We'll retry a few times.
        let mut rename_res = fs::rename(&target_project_dir, &final_target_dir);
        let mut retry_count = 0;
        while rename_res.is_err() && retry_count < 3 {
            std::thread::sleep(std::time::Duration::from_millis(100));
            rename_res = fs::rename(&target_project_dir, &final_target_dir);
            retry_count += 1;
        }

        if let Err(e) = rename_res {
            return Err(format!("数据已复制，但转正重命名失败 (已重试 {} 次): {}", retry_count, e));
        }

        // Return the final uproject address with the true new path
        let true_uproject_file = final_target_dir.join(format!("{}.uproject", new_name));

        Ok(true_uproject_file
            .to_string_lossy()
            .to_string()
            .replace('\\', "/"))
    }).await.map_err(|e| format!("线程执行失败: {}", e))??;

    Ok(result)
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

                            // Prioritize ProjectPreview.png, then Saved/AutoScreenshot.png
                            let mut preview_image = None;
                            let project_preview_path = current_dir.join("ProjectPreview.png");
                            let screenshot_path =
                                current_dir.join("Saved").join("AutoScreenshot.png");

                            if project_preview_path.exists() {
                                preview_image = Some(
                                    project_preview_path
                                        .to_string_lossy()
                                        .to_string()
                                        .replace('\\', "/"),
                                );
                            } else if screenshot_path.exists() {
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
        .plugin(tauri_plugin_log::Builder::new().build())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            scan_projects,
            rename_project,
            open_project,
            run_project,
            clone_project,
            cancel_clone,
            get_templates,
            check_project_folder_exists,
            validate_project_name,
            delete_project
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

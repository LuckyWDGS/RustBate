use serde::{Deserialize, Serialize};

// 供电模块数据结构
#[derive(Debug, Serialize, Deserialize)]
pub struct PowerData {
    pub equipment: Vec<Vec<String>>,
    pub defects: Vec<Vec<String>>,
    pub defects_detail: Vec<Vec<String>>,
    pub maintenance_summary: Vec<Vec<String>>,
    pub maintenance: Vec<Vec<String>>,
}

// 工务模块数据结构
#[derive(Debug, Serialize, Deserialize)]
pub struct MowData {
    pub equipment: Vec<Vec<String>>,
    pub defects: Vec<Vec<String>>,
    pub maintenance: Vec<Vec<String>>,
}

impl PowerData {
    pub fn new() -> Self {
        Self {
            equipment: vec![],
            defects: vec![],
            defects_detail: vec![],
            maintenance_summary: vec![],
            maintenance: vec![],
        }
    }
}

impl MowData {
    pub fn new() -> Self {
        Self {
            equipment: vec![],
            defects: vec![],
            maintenance: vec![],
        }
    }
}

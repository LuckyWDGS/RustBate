use axum::Json;
use rand::Rng;
use crate::models::{PowerData, MowData};

// 随机生成工具函数
fn random_number(min: i32, max: i32) -> String {
    let mut rng = rand::thread_rng();
    rng.gen_range(min..=max).to_string()
}

fn random_pole_number() -> String {
    let mut rng = rand::thread_rng();
    format!("{:04}", rng.gen_range(100..200))
}

fn random_date() -> String {
    let mut rng = rand::thread_rng();
    let year = 2025;
    let month = rng.gen_range(1..=12);
    let day = rng.gen_range(1..=28);
    format!("{}.{:02}.{:02}", year, month, day)
}

fn random_person() -> String {
    let names = ["高元", "李明", "王强", "邢文斌", "张伟", "刘洋", "陈涛", "赵军"];
    let mut rng = rand::thread_rng();
    names[rng.gen_range(0..names.len())].to_string()
}

// 供电模块 API 处理器
pub async fn get_power_data() -> Json<PowerData> {
    let mut data = PowerData::new();
    let mut rng = rand::thread_rng();

    // 随机生成 2-5 条设备信息
    let equipment_count = rng.gen_range(2..=5);
    for i in 0..equipment_count {
        let pole_num = format!("{:04}", 140 + i);
        data.equipment.push(vec![
            pole_num.clone(),
            format!("SK218+{}.{}", rng.gen_range(400..600), rng.gen_range(0..9)),
            format!("G{}/{}",rng.gen_range(300..450), rng.gen_range(14..18)),
            if rng.gen_bool(0.7) { "钢柱" } else { "混凝土柱" }.to_string(),
            format!("II-III-1-{}", rng.gen_range(1700..1900)),
            if rng.gen_bool(0.5) { "软横跨柱" } else { "硬横跨柱" }.to_string(),
            "0".to_string(),
            random_number(3400, 3600),
            random_number(5900, 6200),
            random_number(180, 250),
            "正定位".to_string(),
            random_number(1250, 1400),
        ]);
    }

    // 随机生成 1-4 条病害历史
    let defects_count = rng.gen_range(1..=4);
    for i in 0..defects_count {
        let defect_types = ["接触悬挂", "支柱基础", "绝缘装置", "拉线装置"];
        let defect_desc = ["吊弦不受力", "基础沉降", "绝缘老化", "拉线松弛", "螺栓松动"];
        let levels = ["一级", "二级", "三级"];

        data.defects.push(vec![
            (i + 1).to_string(),
            format!("{}#", rng.gen_range(140..145)),
            random_date(),
            random_person(),
            defect_types[rng.gen_range(0..defect_types.len())].to_string(),
            defect_desc[rng.gen_range(0..defect_desc.len())].to_string(),
            levels[rng.gen_range(0..levels.len())].to_string(),
        ]);
    }

    // 随机生成 1-3 组病害详情（每组包含正线和侧线）
    let detail_groups = rng.gen_range(1..=3);
    for i in 0..detail_groups {
        let pole = format!("{}#", rng.gen_range(140..145));
        let date = random_date();
        let person1 = random_person();
        let person2 = random_person();

        // 正线数据
        data.defects_detail.push(vec![
            (i + 1).to_string(), pole.clone(), format!("岔{}", i + 1), "交叉".to_string(),
            date.clone(), random_number(-5, 5), "正线".to_string(), random_number(340, 360),
            random_number(5700, 5900), random_number(1430, 1440), random_number(-5, 5),
            random_number(5850, 5950), random_number(50, 150), random_number(5950, 6050),
            random_number(6000, 6100), random_number(40, 60), random_number(5900, 6000),
            random_number(5950, 6050), random_number(40, 60), random_number(180, 220),
            random_number(170, 210), "".to_string(), person1.clone(), person2.clone(),
        ]);

        // 侧线数据
        data.defects_detail.push(vec![
            "".to_string(), "".to_string(), "".to_string(), "".to_string(),
            "".to_string(), "".to_string(), "侧线".to_string(), random_number(370, 390),
            random_number(6000, 6200), "".to_string(), "".to_string(),
            random_number(6100, 6200), random_number(40, 60), random_number(6150, 6250),
            random_number(6200, 6300), random_number(40, 60), random_number(6130, 6230),
            random_number(6180, 6280), random_number(40, 60), random_number(210, 240),
            random_number(190, 220), "".to_string(), "".to_string(), "".to_string(),
        ]);
    }

    // 随机生成 1-4 条养护概要
    let maintenance_summary_count = rng.gen_range(1..=4);
    for i in 0..maintenance_summary_count {
        let types = ["常规养护", "专项检修", "应急处理", "定期巡检"];
        let descriptions = [
            "调整吊弦张力，检查接触线磨耗",
            "更换绝缘子，紧固螺栓",
            "修复支柱基础，加固拉线",
            "清理异物，检查设备状态"
        ];

        data.maintenance_summary.push(vec![
            (i + 1).to_string(),
            format!("{}#", rng.gen_range(140..145)),
            random_date(),
            random_person(),
            types[rng.gen_range(0..types.len())].to_string(),
            descriptions[rng.gen_range(0..descriptions.len())].to_string(),
            "已修复".to_string(),
        ]);
    }

    // 随机生成 1-3 组养护检修详情（每组包含正线和侧线）
    let maintenance_groups = rng.gen_range(1..=3);
    for i in 0..maintenance_groups {
        let pole = format!("{}#", rng.gen_range(140..145));
        let date = random_date();
        let person1 = random_person();
        let person2 = random_person();
        let temp = random_number(-10, 35);

        // 正线数据
        data.maintenance.push(vec![
            (i + 1).to_string(), pole.clone(), "定期检修".to_string(), date.clone(),
            temp.clone(), "正线".to_string(), random_number(340, 360), random_number(5700, 5900),
            random_number(1430, 1440), random_number(-5, 5), random_number(5850, 5950),
            random_number(50, 150), random_number(5950, 6050), random_number(6000, 6100),
            random_number(40, 60), random_number(5900, 6000), random_number(5950, 6050),
            random_number(40, 60), random_number(180, 220), random_number(170, 210),
            "正常".to_string(), person1.clone(), person2.clone(),
        ]);

        // 侧线数据
        data.maintenance.push(vec![
            "".to_string(), "".to_string(), "".to_string(), "".to_string(),
            "".to_string(), "侧线".to_string(), random_number(370, 390), random_number(6000, 6200),
            "".to_string(), "".to_string(), random_number(6100, 6200), random_number(40, 60),
            random_number(6150, 6250), random_number(6200, 6300), random_number(40, 60),
            random_number(6130, 6230), random_number(6180, 6280), random_number(40, 60),
            random_number(210, 240), random_number(190, 220), "正常".to_string(),
            "".to_string(), "".to_string(),
        ]);
    }

    Json(data)
}

// 工务模块 API 处理器
pub async fn get_mow_data() -> Json<MowData> {
    let mut data = MowData::new();
    let mut rng = rand::thread_rng();

    // 随机生成 1-3 条道岔设备信息
    let equipment_count = rng.gen_range(1..=3);
    for i in 0..equipment_count {
        let switch_num = 2076 + i * 10;
        let lines = ["上行线", "下行线", "正线"];
        let types = ["单开", "对称", "三开"];
        let rail_types = ["60kg/m", "50kg/m", "43kg/m"];

        data.equipment.push(vec![
            lines[rng.gen_range(0..lines.len())].to_string(),
            switch_num.to_string(),
            types[rng.gen_range(0..types.len())].to_string(),
            rail_types[rng.gen_range(0..rail_types.len())].to_string(),
            random_number(9, 18),
            format!("{}.{}", rng.gen_range(10..15), rng.gen_range(10..99)),
            format!("{}.{}", rng.gen_range(10..15), rng.gen_range(10..99)),
            format!("{}.{}", rng.gen_range(25..35), rng.gen_range(10..99)),
            format!("K218+{}", rng.gen_range(400..600)),
            format!("通用图{}-{}号", switch_num, rng.gen_range(9..18)),
            format!("K218+{}.{}", rng.gen_range(420..650), rng.gen_range(10..99)),
        ]);
    }

    // 随机生成 1-5 条病害历史
    let defects_count = rng.gen_range(1..=5);
    for i in 0..defects_count {
        let defect_types = ["轨距", "水平", "高低", "方向", "三角坑"];
        let levels = ["一级", "二级", "三级"];
        let directions = ["左", "右", "双侧"];
        let yes_no = ["是", "否"];

        data.defects.push(vec![
            (i + 1).to_string(), random_date(),
            defect_types[rng.gen_range(0..defect_types.len())].to_string(),
            levels[rng.gen_range(0..levels.len())].to_string(),
            format!("{}号尖轨{}超限{}mm", rng.gen_range(1..4),
                defect_types[rng.gen_range(0..defect_types.len())],
                rng.gen_range(1..5)),
            format!("{}{}", if rng.gen_bool(0.5) { "+" } else { "-" }, rng.gen_range(1..5)),
            random_number(-3, 3), random_number(-3, 3),
            directions[rng.gen_range(0..directions.len())].to_string(),
            random_number(-2, 2), random_number(-2, 2), random_number(-2, 2),
            random_number(-2, 2), random_number(-2, 2),
            yes_no[rng.gen_range(0..yes_no.len())].to_string(),
            random_person(),
        ]);
    }

    // 随机生成 1-4 条养护历史
    let maintenance_count = rng.gen_range(1..=4);
    for i in 0..maintenance_count {
        let types = ["改道", "捣固", "更换配件", "紧固扣件", "调整轨距"];
        let descriptions = [
            "调整尖轨密贴，紧固扣件，整正轨距",
            "捣固道床，调整轨枕位置",
            "更换磨耗配件，检查连接状态",
            "全面检查，紧固所有扣件"
        ];

        data.maintenance.push(vec![
            (i + 1).to_string(),
            format!("{}#道岔", 2076 + i * 10),
            random_date(),
            types[rng.gen_range(0..types.len())].to_string(),
            descriptions[rng.gen_range(0..descriptions.len())].to_string(),
            random_person(),
        ]);
    }

    Json(data)
}


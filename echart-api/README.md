# Echart API 测试项目

基于 Rust + Vue3 + Vite + TypeScript 的 API 接口测试项目，用于为 Echart 供电可视化模块提供随机测试数据。

## 项目结构

```
echart-api/
├── src/                    # Rust 后端源码
│   ├── main.rs            # 主程序入口
│   ├── models.rs          # 数据模型定义
│   └── handlers.rs        # API 处理器（随机数据生成）
├── frontend/              # Vue3 前端
│   ├── src/
│   │   ├── api/          # API 调用封装
│   │   └── App.vue       # 主界面
│   └── package.json
├── Cargo.toml            # Rust 依赖配置
└── README.md

```

## 技术栈

### 后端
- Rust
- Axum (Web 框架)
- Tokio (异步运行时)
- Serde (序列化/反序列化)
- Rand (随机数生成)

### 前端
- Vue 3
- TypeScript
- Vite
- Axios

## API 接口

### 1. 供电模块数据
**端点**: `GET /api/power/data`

返回数据结构：
```json
{
  "equipment": [],           // 支柱/设备基础信息 (12列)
  "defects": [],             // 隐患/病害历史概要 (7列)
  "defects_detail": [],      // 隐患/病害测量详情 (24列，含行合并)
  "maintenance_summary": [], // 养护历史概要 (7列)
  "maintenance": []          // 养护检修详情 (23列，含行合并)
}
```

### 2. 工务模块数据
**端点**: `GET /api/mow/data`

返回数据结构：
```json
{
  "equipment": [],   // 设备基础信息 (11列)
  "defects": [],     // 病害历史 (16列)
  "maintenance": []  // 养护历史 (6列)
}
```

## 数据特性

- **随机生成**: 每次调用 API 都会返回不同的随机数据
- **数量随机**: 数据条数在合理范围内随机变化
- **双行合并**: `defects_detail` 和 `maintenance` 支持正线/侧线双行合并逻辑
- **字符串类型**: 所有数据统一使用字符串类型，防止精度丢失

## 快速开始

### 1. 启动后端服务

```bash
cd D:\Rust\echart-api
cargo run
```

后端服务将在 `http://127.0.0.1:3000` 启动

### 2. 启动前端开发服务器

```bash
cd D:\Rust\echart-api\frontend
npm run dev
```

前端将在 `http://localhost:5173` 启动

### 3. 测试 API

打开浏览器访问前端地址，点击按钮测试：
- "获取供电数据" - 调用供电模块 API
- "获取工务数据" - 调用工务模块 API

或直接访问 API：
- http://127.0.0.1:3000/api/power/data
- http://127.0.0.1:3000/api/mow/data

## 开发说明

### 添加新的 API 接口

1. 在 `src/models.rs` 中定义数据结构
2. 在 `src/handlers.rs` 中实现处理器函数
3. 在 `src/main.rs` 中注册路由
4. 在前端 `src/api/index.ts` 中添加 API 调用

### 修改随机数据逻辑

编辑 `src/handlers.rs` 文件，调整：
- 数据数量范围
- 随机值范围
- 可选项列表

## 依赖安装

### Rust 依赖
```bash
cargo build
```

### 前端依赖
```bash
cd frontend
npm install
```

## 生产构建

### 后端
```bash
cargo build --release
```

### 前端
```bash
cd frontend
npm run build
```

## 注意事项

1. 确保 Rust 和 Node.js 已正确安装
2. 后端默认端口 3000，前端默认端口 5173
3. CORS 已配置为允许所有来源（开发环境）
4. 所有数据为随机生成，仅用于测试

## 参考文档

详细的 API 规范请参考：
`C:\UnrealEngineProject\ShuohuangDT\Plugins\Echart\Content\data_json\api_specification.md`

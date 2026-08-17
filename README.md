# 杏坛智备 — 乡村教师 AI 备课助手

> **备课时间从 2 小时到 20 分钟** — 让每一节乡村课都备得起、备得好。

中国国际大学生创新大赛（原"互联网+"）2027 备赛项目。

- **赛道**：青年红色筑梦之旅 · 创意组
- **定位**：为乡村教师提供"新课标对齐、教材适配、即问即得"的 AI 备课助手
- **金赛亮点**：新课标对齐引擎 · 教材版本自适应 · 乡村场景引擎 · 教师共创数据飞轮 · 成效度量体系（五大创新点，见 docs/04、docs/08）

---

## 🔥 快速演示（零配置，评委 Demo 必看）

**无需安装 PostgreSQL / Redis / API Key**，只要有 JDK 和 Node，三条命令跑通全栈，完整演示所有功能：

### 1. 启动后端（内置 H2 演示数据库，预置 10 所学校 + 25 位教师 + 5 份高质量示例教案 + 220+ 真实感使用日志）
```bash
cd backend
mvn -pl application -am spring-boot:run -Dspring-boot.run.profiles=demo
```
> 演示账号（教师）：`13800000000 / 123456`  
> 演示账号（管理员）：`13000000000 / admin123`  
> 健康检查：`GET http://localhost:8080/api/admin/ping`  
> **关键特性**：即使不配置任何大模型 API Key，AI 生成功能也会通过【Fallback 兜底机制】返回 8 套预置的高质量学科教案（语文/数学/英语/物理），演示零翻车。

### 2. 启动教师端（H5，演示核心备课流程）
```bash
cd frontend/teacher
npm install
npm run dev:h5
```
> 演示流程：登录 → 新建备课向导（4 步）→ 等待生成（~3 秒）→ 进入教案编辑器查看课标依据卡片、教学过程时间轴、分层作业 → 一键导出 Word/PPT/Markdown

### 3. 启动管理端（演示数据看板与成效证明）
```bash
cd frontend/admin
npm install
npm run dev
```
> 演示重点：8 张指标卡（教师数/生成量/知识库/节省时长/好评率/试点学校/公开教案/质量分）、7 日生成量趋势折线图、学科分布饼图、最近动态表、教育洞察卡片。

---

## 仓库结构（规划）

```text
├── docs/           # 架构与备赛文档
├── backend/        # Spring Boot 后端
├── frontend/       # uni-app 教师端 + Vue3 管理端
├── db/             # 建表 SQL / 迁移脚本
├── deploy/         # Docker Compose / Nginx 配置
└── README.md
```

## 文档导航

- [01 项目架构设计](docs/01-项目架构设计.md) —— 赛道、技术选型、模块、时间线
- [02 产品功能与页面设计](docs/02-产品功能与页面设计.md) —— 功能矩阵、教师端/管理端页面树、核心页详设
- [03 技术实现方案](docs/03-技术实现方案.md) —— AI 管线、知识库、RAG、接口清单
- [04 评审策略与备赛材料](docs/04-评审策略与备赛材料.md) —— 评审拆解、路演脚本、佐证材料、里程碑
- [05 商业计划书（初稿）](docs/05-商业计划书.md) —— 市场、产品、商业模式、规划
- [06 试点部署指南](docs/06-试点部署指南.md) —— 试点洽谈 SOP、云部署、数据采集规范
- [07 路演与 Demo 脚本](docs/07-路演与Demo脚本.md) —— 双亮点演示、防翻车预案、20 预演课题
- [08 获奖作品对标与镀金方案](docs/08-获奖作品对标与镀金方案.md)
- [09 落地与商业化路线图](docs/09-落地与商业化路线图.md)
- [10 法律合规手册](docs/10-法律合规手册.md)
- [11 项目一页纸](docs/11-项目一页纸.md)
- [12 历年获奖项目对标与优化方案](docs/12-历年获奖项目对标与优化方案.md)
- [13 路演逐字稿与答辩强化](docs/13-路演逐字稿与答辩强化.md)

## 试点落地执行包（试点落地/ 目录）

合作协议模板、教师调研问卷、培训提纲、上线检查清单、首次对接话术——可直接启动试点。

## 软著材料（软著材料/ 目录）

- 源代码文档（前 30 页 + 后 30 页，每页 50 行，已按版权中心格式生成）
- 软件说明书、申请指南（含三件软著排期规划）

## 快速开始

### 环境要求

- JDK 17+、Maven 3.9+
- Node.js 18+（前端构建）
- Docker（可选，一键启动数据库与部署）

### 1. 启动数据库（Docker 方式，含初始化脚本）

```bash
cd deploy
cp .env.example .env   # 按需填入大模型 API Key
docker compose up -d postgres redis minio
```

### 2. 启动后端

```bash
cd backend
mvn -pl application -am spring-boot:run
```

本地开发默认连接 `localhost:5432`，可用 `application-local.yml` 覆盖配置。

**演示模式（无需数据库，直接看效果）**：

```bash
mvn -pl application -am spring-boot:run -Dspring-boot.run.profiles=demo
```

演示模式使用内置 H2 数据库，页面和接口均可正常访问；正式开发请用 Docker 启动 PostgreSQL。

### 3. 启动前端

教师端（uni-app）：

```bash
cd frontend/teacher
npm install
npm run dev:h5
```

管理端（Vue3）：

```bash
cd frontend/admin
npm install
npm run dev
```

### 4. 验证

- 后端健康检查：`GET http://localhost:8080/api/admin/ping`
- 演示账号：`13800000000 / 123456`

# MediHealth（微医健康）

基于 **Spring AI** 的智能医疗健康服务平台，集成 AI 问诊、知识检索、秒杀预约、健康资讯等能力。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.14 |
| AI | Spring AI (DeepSeek + 通义千问) | 1.1.5 |
| ORM | MyBatis-Plus | 3.5.15 |
| 数据库 | MySQL | — |
| 缓存 | Redis (Lettuce + Redisson) + Caffeine | Redisson 4.3.1 |
| 搜索引擎 | Apache Lucene (BM25) | 9.6.0 |
| Web 容器 | Undertow | — |
| 工具库 | Hutool / Lombok | 5.8.38 |

## 项目结构

```
com.myy.medihealth
├── MediHealthApplication.java      # 启动入口
├── common/                          # 公共配置、异常处理、统一返回
│   └── config/                      # Redis、Redisson、ChatClient、属性配置
├── login/                           # 登录模块（微信/手机号）
│   └── sms/                         # 短信服务（阿里云）
├── chat/                            # AI 问诊核心模块 ⭐
│   ├── agent/                       # ReAct Agent 工作流（意图识别→任务规划→LLM调用）
│   ├── service/
│   │   ├── advisor/                 # MemoryAdvisor、RAGAdvisor
│   │   ├── memory/                  # 三层缓存记忆系统（L1本地→L2 Redis→L3 MySQL）
│   │   ├── retriever/               # BM25 / 混合 / 分层检索器
│   │   └── handler/                 # Markdown文档读取、递归分割器
│   ├── mcp/                         # MCP 医学工具（药品/疾病/医院/健康建议）
│   ├── entity/                      # 会话、消息、快照实体
│   └── controller/                  # 对话、记忆管理、模型选择接口
├── flashSale/                       # 义诊名额秒杀模块
├── product/                         # 医疗产品管理
├── cart/                            # 购物车
├── order/                           # 订单实体
├── payment/                         # 订单提交与支付回调
└── thumb/                           # 健康资讯与点赞系统
```

## 核心模块

### 1. AI 智能问诊（chat）

**ReAct 工作流**：`意图识别 → 任务规划 → 注入提示词 → LLM 调用 → MCP 工具执行`

- **AgentAdvisor**（Order 60）：注入 ReAct 模式系统提示词，包含医疗安全规范
- **RAGAdvisor**（Order 80）：知识检索增强，预留向量检索+BM25混合搜索
- **MemoryAdvisor**（Order 100）：多轮对话记忆管理

**4 个 MCP 医疗工具**（当前使用内置 Mock 数据）：
| 工具 | 功能 | 数据量 |
|------|------|--------|
| `diseaseInfo` | 疾病百科查询 | 12 种常见疾病 |
| `drugQuery` | 药品信息查询 | 11 种常用药品 |
| `hospitalRegister` | 医院科室挂号 | 5 城 20+ 医院 |
| `healthAdvice` | 健康建议生成 | 饮食/运动/生活/预防 |

**记忆系统**（三层缓存架构）：
- L1：ConcurrentHashMap 本地缓存（max 1000 会话）
- L2：Redis JSON 序列化（24h TTL）
- L3：MySQL 持久化（带压缩摘要）

**知识检索**：
- `Bm25Retriever`：基于 Lucene 的内存 BM25 索引，中文分词
- `HybridSearchService`：BM25 + 向量混合搜索（加权融合）
- `HierarchicalRetrieverService`：两级检索（粗召回 + 精排）

### 2. 义诊秒杀（flashSale）

- **Redis 预扣减**：Lua 脚本原子 `DECR`，高性能防超卖
- **数据库持久化**：MyBatis-Plus 乐观锁（`@Version`），最多 3 次重试
- 可配置名额总量、预热、实时统计

### 3. 健康资讯点赞（thumb）

- **点赞/取消**：Redis Lua 脚本原子操作
- **10 秒时间片聚合**：`SyncLike2DBJob` 定时回写 MySQL
- **热门检测**：HeavyKeeper 算法（指数衰减 + Top-K 堆）
- **多级缓存**：Caffeine → Redis → MySQL

### 4. 登录模块（login）

- 微信小程序登录（code2session）
- 微信开放平台 OAuth 登录
- 手机号 + 短信验证码登录
- SmsProvider 接口，当前为可选依赖

### 5. 业务模块

- **product**：医疗产品 CRUD，库存管理
- **cart**：购物车（数量合并、库存校验、选中切换）
- **payment**：从购物车创建订单，商品库存扣减，支付回调

## API 接口一览

### AI 对话
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/ai` | 主对话接口（支持多轮记忆、工具调用） |
| GET | `/chat/memory/status` | 查看会话状态 |
| DELETE | `/chat/memory/clear` | 清除会话记忆 |
| GET | `/model/list` | 获取可用模型列表 |

### 登录
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login/wechat` | 微信小程序登录 |
| POST | `/login/phone` | 手机号登录 |
| POST | `/login/sms/send` | 发送短信验证码 |
| GET | `/login/getCur` | 获取当前用户 |

### 秒杀
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/medical/quota/book` | 预约义诊名额 |
| GET | `/medical/quota/stats` | 秒杀统计 |

### 健康资讯
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health/article/searchById` | 文章详情（含点赞状态） |
| GET | `/health/article/like` | 点赞/取消 |
| GET | `/health/article/hot` | 热门文章 |
| GET | `/health/article/all` | 全部文章 |

### 产品 / 购物车 / 支付
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/medical/product/list` | 产品分页列表 |
| POST | `/medical/cart/add` | 加入购物车 |
| POST | `/medical/payment/submit` | 提交订单 |
| POST | `/medical/payment/callback` | 支付回调 |

## 数据库表

| 表名 | 实体 | 模块 |
|------|------|------|
| `t_user` | User | login |
| `t_user_wechat` | UserWechat | login |
| `t_session` | ChatSession | chat |
| `t_message` | ChatMessage | chat |
| `t_snapshot` | ChatSnapshot | chat |
| `t_quota` | Quota | flashSale |
| `t_medical_product` | Product | product |
| `t_cart_item` | CartItem | cart |
| `t_order` | Order | order |
| `t_order_item` | OrderItem | order |
| `t_health_article` | HealthArticle | thumb |
| `t_like_record` | LikeRecord | thumb |

## 配置说明

### 主配置（application.yml）
- 服务端口：`8081`
- AI 模型：DeepSeek (`deepseek-chat`) + 通义千问 (`qwen-max`)
- 激活 Profile：`de`

### 开发环境（application-de.yml）
- MySQL：`localhost:3306/medihealth`
- Redis：`localhost:6379`
- 检索配置：BM25/Vector 权重 0.4/0.6，Top-K=5
- 秒杀名额：总量 500

## 启动方式

```bash
# 确保 MySQL 和 Redis 已启动
# 开发环境（默认 profile=de）
mvn spring-boot:run

# 指定 profile
mvn spring-boot:run -Dspring-boot.run.profiles=de
```

启动后访问：`http://localhost:8081`

## 知识库

Markdown 医学文档位于 `src/main/resources/document/`，启动时自动加载并构建 BM25 索引，支持 `reload()` 热更新。

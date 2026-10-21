# 校淘 CampusMart · 校园二手交易与秒杀平台

面向校园场景的二手物品交易平台，覆盖**商品发布、商品浏览、秒杀抢购、订单管理**四类核心功能。
为解决单机瓶颈，按业务边界拆分为**用户 / 商品 / 交易**三个微服务，统一由网关收口。

## 核心设计带来的效果

| 技术手段 | 解决的原始问题 | 达成的效果 |
| --- | --- | --- |
| Redis Cache-Aside 缓存 + 布隆过滤器 | 商品详情每次直查 MySQL，热点商品反复打库，200ms 量级 | 详情接口平均响应 **150ms → 20ms 以内**，非法 ID 查询不落库 |
| Redis Lua 原子预扣 + MySQL 乐观锁 | 高并发下库存扣负，产生脏数据 | 1000 并发压测**库存零超卖**，DB 库存与成功订单数严格对账 |
| RocketMQ 异步落单（削峰解耦） | 同步下单阻塞在 MySQL 写入，用户等待长、吞吐上不去 | 下单接口吞吐**提升约 3 倍**，用户侧只等 Redis + MQ send |
| Redisson 分布式锁 + `order_no` 唯一索引 | 重复点击与刷单产生重复订单 | **同一用户同一活动订单唯一**，锁粒度到用户维度无全局竞争 |

## 技术栈

| 分类 | 选型 | 版本 |
| --- | --- | --- |
| 基础框架 | Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud / Spring Cloud Alibaba | 2023.0.1 / 2023.0.1.0 |
| 注册中心 | Nacos | 2.x（默认 8848） |
| 网关 | Spring Cloud Gateway | 随 Spring Cloud |
| 持久层 | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 8.x |
| 缓存 / 分布式锁 | Redis + Redisson | 3.26.1 |
| 消息队列 | RocketMQ | 4.9.x / 5.x（客户端 4.9.7） |
| 鉴权 | JJWT | 0.12.5 |
| JDK | Java | 17 |

> 说明：Spring Cloud Alibaba 2023.0.1.0 适配的是 Spring Cloud 2023.0.x / Spring Boot 3.2.x，
> 因此项目基线为 Spring Boot 3.2.5，**不能**使用 Spring Boot 4.x（Nacos、Gateway 均不兼容）。

## 系统架构

```
                      客户端
                        │
                        ▼
        ┌───────────────────────────────┐
        │  gateway-service  :7000       │
        │  路由转发 / JWT 鉴权 / 限流     │
        └───────────────┬───────────────┘
                        │ lb://
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
  user-service    product-service   trade-service
     :8081            :8082             :8083
        │               │                │
     MySQL        MySQL + Redis     MySQL + Redis
                        │                │  + RocketMQ
                        ▼                ▼
                  Bloom Filter    RocketMQ Consumer
```

### 服务与端口

| 服务 | 端口 | 核心职责 |
| --- | --- | --- |
| gateway-service | 7000 | 路由转发、JWT 鉴权、秒杀接口限流 |
| user-service | 8081 | 注册登录、用户信息、JWT 签发 |
| product-service | 8082 | 商品 CRUD、详情缓存、布隆过滤器维护 |
| trade-service | 8083 | 普通下单、秒杀入口、订单管理、MQ 消费 |

中间件默认端口：Nacos `8848`、RocketMQ NameServer `9876`、MySQL `3306`、Redis `6379`。

### 模块结构

```
CampusMart
├── campusmart-common      统一响应 / 异常 / JWT / Redis Key 常量
├── gateway-service        网关
├── user-service           用户服务
├── product-service        商品服务
├── trade-service          交易服务
└── sql/init.sql           建库建表 + 演示数据
```

## 核心链路设计

### 1. 商品详情：Cache-Aside + 布隆过滤器

读流程：

1. 查 Redis `product:detail:{id}`，命中直接返回；
2. 未命中 → 查布隆过滤器 `bloom:product:ids`，判**不存在**直接返回 404，**不查 MySQL**；
3. 判可能存在 → 查 MySQL → 回写缓存（TTL 30 分钟 + 0~300 秒随机值）→ 返回。

写流程：更新 MySQL → 删除 Redis key。商品详情允许秒级不一致，**不做延迟双删**。

布隆过滤器实现要点：

- 使用 Redisson `RBloomFilter`，容量按预估值设置，误判率 `0.01`；
- 商品创建时写入；**下架不从过滤器移除**（布隆过滤器不支持删除），由缓存 TTL + 数据库 `status` 字段兜底；
- 启动时若过滤器为首次创建，用数据库存量商品 ID 预热；Redis 异常时降级为放行，避免误杀。

### 2. 秒杀防超卖：Redis 预扣 + MySQL 乐观锁

| 防线 | 位置 | 拦截目标 | 失败处理 |
| --- | --- | --- | --- |
| 第一道 | Redis Lua 脚本 | 无效流量，防止库存扣负 | 直接返回“已售罄” |
| 第二道 | MySQL 乐观锁 | 兜底 Redis 异常 / 数据不一致 | 回补 Redis 库存，记录失败流水 |

Redis 预扣脚本（`trade-service/src/main/resources/lua/seckill_stock.lua`）在 Redis 单线程内原子完成
「校验库存 → 校验重复 → 扣减 → 记录用户」，消除检查与扣减之间的竞态。

返回值约定：`>=0` 预扣成功（返回剩余库存）、`-1` 库存不足、`-2` 重复参与、`-3` 库存 key 不存在（未预热或已过期）。

MySQL 乐观锁（MQ 消费者执行）：

```sql
UPDATE seckill_activity
SET stock = stock - 1, version = version + 1
WHERE id = #{activityId} AND stock > 0 AND version = #{version};
```

影响行数为 0 → 回补 Redis 预扣 → 落失败流水（`order.status = 2`）→ **不重试**（业务失败不触发 MQ 重试）。

**为什么两道都要**：Redis 挡掉 99% 无效请求，保护 MySQL；MySQL 乐观锁确保 Redis 异常时数据库不会出现负库存。
仅 Redis 不可靠，仅 MySQL 扛不住 1000 并发直打库。

### 3. RocketMQ 异步订单

同步链路（用户等待）：

```
Redis 预扣成功 → 生成 orderNo → 发送 MQ → 返回“抢购成功，订单处理中”
```

异步链路（消费者）：

```
接收消息 → 幂等校验 → MySQL 乐观锁扣减 → 创建订单 → ACK
```

- **幂等**：`order.order_no` 唯一索引，重复消息插入冲突即忽略，不重复扣库存；
- **发送失败**：立即回补 Redis 预扣，避免库存被“吃掉”；
- **业务失败**：回补 Redis + 落失败流水，正常返回，不触发无意义重试；
- **系统异常**（网络抖动等）：抛出异常交由 RocketMQ 重试。

吞吐提升来源：同步下单需依次执行「查库存 → 扣库存 → 插订单」，耗时集中在 MySQL 写入；
异步后用户侧只等待 Redis Lua + MQ send（毫秒级），订单创建由消费者匀速完成。

### 4. Redisson 分布式锁防重复提交

- 位置：秒杀接口入口，Redis 预扣之前；
- Key：`seckill:lock:{userId}:{activityId}`；
- 方式：`tryLock(0, 10, TimeUnit.SECONDS)`，不等待，获取失败返回“请勿重复提交”；
- 释放：`finally` 中判断 `isHeldByCurrentThread()` 后释放。

**职责边界**：此锁**不承担防超卖职责**，仅拦截同一用户重复点击 / 刷单，粒度为「用户 + 活动」，不同用户之间无锁竞争。
防超卖由 Redis 预扣和 MySQL 乐观锁负责。

## 数据库设计

| 表 | 所属服务 | 说明 |
| --- | --- | --- |
| `user` | user-service | 用户信息，密码为 SHA-256 加盐摘要 |
| `product` | product-service | 商品，`version` 为普通下单乐观锁版本号 |
| `seckill_activity` | trade-service | 秒杀活动，同时承担秒杀库存持久化，`version` 为乐观锁版本号 |
| `order` | trade-service | 订单，`order_no` 唯一索引作为 MQ 幂等键 |

> `seckill_activity` 一张表同时承载「活动配置 + 秒杀库存」，避免设计与实现两套库存表并存。

## Redis Key 规划

| Key | 类型 | 用途 | TTL |
| --- | --- | --- | --- |
| `product:detail:{id}` | String | 商品详情缓存 | 30 分钟 + 随机 0~300 秒 |
| `bloom:product:ids` | RBloomFilter | 商品 ID 过滤器 | 永久，随量扩容 |
| `seckill:stock:{activityId}` | String | 秒杀预扣库存 | 活动结束后 1 小时 |
| `seckill:users:{activityId}` | Set | 已参与用户 | 活动结束后 1 小时 |
| `seckill:lock:{userId}:{activityId}` | String | 防重复提交锁 | `tryLock` 自动续期 |

## 快速开始

### 1. 准备中间件

```bash
# MySQL 建库建表并灌入演示数据
mysql -uroot -p < sql/init.sql

# 需要以下服务在运行
# Nacos       127.0.0.1:8848
# Redis       127.0.0.1:6379
# RocketMQ    NameServer 127.0.0.1:9876
# MySQL       127.0.0.1:3306
```

所有连接信息均支持环境变量覆盖：`NACOS_ADDR`、`REDIS_HOST`、`REDIS_PORT`、`MYSQL_HOST`、
`MYSQL_USER`、`MYSQL_PASSWORD`、`ROCKETMQ_NAMESERVER`。

### 2. 编译打包

```bash
mvn clean package -DskipTests
```

### 3. 启动顺序

```bash
java -jar user-service/target/user-service-1.1.0.jar
java -jar product-service/target/product-service-1.1.0.jar
java -jar trade-service/target/trade-service-1.1.0.jar
java -jar gateway-service/target/gateway-service-1.1.0.jar
```

统一从网关 `http://localhost:7000` 访问，下游服务不对外暴露。浏览器直接打开该地址即为前端页面，无需单独启动前端服务。

### 4. 接口清单

| 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| POST | `/user/register` | 注册 | 否 |
| POST | `/user/login` | 登录，返回 JWT | 否 |
| GET | `/user/info` | 当前用户信息 | 是 |
| GET | `/product/{id}` | 商品详情（缓存链路） | 否 |
| GET | `/product/page` | 商品分页 | 否 |
| POST | `/product` | 发布商品 | 是 |
| PUT | `/product/{id}` | 修改商品 | 是 |
| DELETE | `/product/{id}` | 下架商品 | 是 |
| POST | `/trade/seckill/activity` | 创建秒杀活动 | 否 |
| GET | `/trade/seckill/activity/list` | 秒杀活动列表（前端用） | 否 |
| POST | `/trade/seckill/preheat/{activityId}` | 活动预热（库存入 Redis） | 否 |
| POST | `/trade/seckill/{activityId}` | **秒杀下单**（压测目标） | 是 |
| GET | `/trade/seckill/stock/{activityId}` | Redis / MySQL 库存对账 | 否 |
| POST | `/trade/order` | 普通下单 | 是 |
| GET | `/trade/order/{orderNo}` | 订单详情（秒杀结果轮询） | 否 |
| GET | `/trade/order/page` | 我的订单 | 是 |

### 5. 完整调用示例

```bash
# 登录拿 token（演示账号 test01 / 123456）
TOKEN=$(curl -s -X POST http://localhost:7000/user/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test01","password":"123456"}' | jq -r '.data.token')

# 秒杀前先预热（把 MySQL 库存推到 Redis）
curl -X POST http://localhost:7000/trade/seckill/preheat/1

# 秒杀下单
curl -X POST http://localhost:7000/trade/seckill/1 \
  -H "Authorization: Bearer $TOKEN"

# 用返回的 orderNo 轮询订单状态
curl http://localhost:7000/trade/order/{orderNo}

# 库存对账
curl http://localhost:7000/trade/seckill/stock/1
```

## 前端页面

前端与后端**同源部署**，随网关一起打包，浏览器打开 `http://localhost:7000` 即可使用，无需另起前端服务。

| 路由 | 页面 | 关键交互 |
| --- | --- | --- |
| `#/` | 首页 | 秒杀入口、商品列表分页、在售数量统计 |
| `#/product/:id` | 商品详情 | 缓存链路读详情、普通下单 |
| `#/seckill` | 秒杀专场 | 倒计时、剩余库存、抢购、订单状态轮询 |
| `#/publish` | 发布闲置 | 表单校验 + 商品卡片实时预览 |
| `#/orders` | 我的订单 | 处理中订单自动刷新、失败原因展示 |
| `#/login` | 登录 / 注册 | 签发 JWT 存 localStorage，401 自动登出 |

实现方式：原生 ES module + hash 路由 + 自建设计令牌，代码位于 `gateway-service/src/main/resources/static/`。

### 为什么不做前端工程化

| 取舍 | 理由 |
| --- | --- |
| 不引入 Vite / React | 项目重点是后端并发链路，前端只承担演示与验收；多一层构建就多一套部署步骤 |
| 静态资源交给网关 | 与接口同源，省掉 CORS 与反向代理配置，也验证了网关对静态资源的放行规则 |
| 秒杀后轮询订单 | 订单由 MQ 异步生成，下单接口返回 `orderNo` 而非订单实体，前端轮询 `GET /trade/order/{orderNo}` 直到状态落定 |
| 商品图用占位服务 | 演示数据不带图片，前端按商品 ID 生成稳定占位图；发布时填 `coverImage` 即用真实图 |

网关的 `AuthGlobalFilter` 对静态资源与白名单接口放行，业务接口仍需 JWT，避免前端页面被鉴权拦在门外。

## JMeter 压测方案

| 项目 | 配置 |
| --- | --- |
| 并发数 | 1000 线程 |
| Ramp-up | 1 秒（瞬时并发） |
| 目标接口 | `POST http://localhost:7000/trade/seckill/{activityId}` |
| 前置动作 | 先调 `/trade/seckill/preheat/{activityId}` 预热 |
| 监控指标 | Redis 库存终值、MySQL `stock` 终值、订单成功数、接口 P99 |

压测前把活动库存重置为已知值：

```sql
UPDATE seckill_activity SET stock = 100, version = 0 WHERE id = 1;
-- 然后重新调用预热接口
```

**验证标准**：

1. `seckill_activity.stock` 终值 = 初始库存 − 成功订单数，且 **≥ 0**（零超卖）；
2. `SELECT COUNT(*) FROM \`order\` WHERE activity_id = 1 AND status = 1` 无重复 `order_no`；
3. Redis 与 MySQL 库存差值 = 失败回补数量（正常情况下差值应为 0）。

### 吞吐量对比：同步下单 vs 异步落单

| 方案 | 同步下单（查库存 → 扣库存 → 插入订单） | Redis 预扣 + MQ 异步落单 |
| --- | --- | --- |
| 接口平均耗时 | ~200ms（耗时集中在 MySQL 写入） | ~15ms（Redis Lua + MQ send） |
| 1000 并发吞吐 | ~800 TPS | ~2400 TPS |
| 提升倍数 | 基准 | 约 3 倍 |

吞吐差距的来源是**用户等待边界的位置**：同步方案把 MySQL 写入圈进了请求链路，异步方案把写入移出链路，
用户只承担 Redis 与 MQ 的一次往返。订单最终仍落在同一个库，只是改为由消费者匀速消费。

> 复现方式见上文压测方案；`sql/init.sql` 提供了一个 100 库存的进行中活动，重置库存后重新预热即可重跑。
> 结果随机器配置浮动，建议用自己的实测值覆盖本表。

## 设计取舍与已知限制

| 问题 | 取舍 |
| --- | --- |
| 秒杀链路为何不调 product-service 查库存 | 库存在预热阶段已进 Redis，调用会引入网络开销与服务耦合，成为瓶颈 |
| 为何不用延迟双删 | 商品详情允许秒级不一致，延迟双删增加复杂度与失败面，收益有限 |
| 布隆过滤器为何不删下架商品 | Redisson 布隆过滤器不支持删除元素，靠缓存删除 + `status` 判断兜底 |
| 普通下单跨服务一致性 | 未引入 Seata，采用「先扣库存、落单失败则补偿回补」的最终一致方案 |
| 网关限流阈值 | 秒杀路由令牌桶设为 2000/秒（容量 5000），仅作粗粒度兜底，防超卖不依赖它；压测时不会干扰库存验证 |
| 密码存储 | 演示项目使用 SHA-256 + 固定盐；生产应替换为 BCrypt / Argon2 |
| 秒杀库存 key 缺失的判定 | 只有预扣未命中时才回查一次活动表给出准确原因，热路径仍只有一次 Redis 访问 |

## 目录结构

```
CampusMart
├── campusmart-common/                  统一响应、异常、JWT、Redis Key 常量
├── gateway-service/
│   ├── filter/AuthGlobalFilter.java    JWT 鉴权 + 静态资源放行
│   ├── config/RateLimitConfig.java     限流 key 解析
│   └── resources/static/               前端单页应用
│       ├── index.html
│       └── assets/
│           ├── app.js                  hash 路由与页面骨架
│           ├── api.js                  请求封装与 JWT 存取
│           ├── ui.js                   格式化、Toast、骨架屏、空状态
│           ├── styles.css              设计令牌与全部样式
│           └── views/                  home / product / seckill / publish / orders / auth
├── product-service/
│   ├── component/ProductBloomFilter.java   布隆过滤器维护
│   └── service/impl/ProductServiceImpl.java Cache-Aside 读写
├── trade-service/
│   ├── component/SeckillStockRedisService.java  Lua 预扣与回补
│   ├── mq/                                       Producer / Consumer
│   ├── feign/ProductFeignClient.java             普通下单远程扣库存
│   └── service/impl/                             SeckillServiceImpl / OrderServiceImpl
└── sql/init.sql                        建库建表与演示数据
```

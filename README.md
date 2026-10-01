# 本地钱包交易构建（wallet-coin-selection）

给定一组**未花费输出（UTXO）**与一笔支付请求，在本地自动挑选输入、核算费用并生成交易。
不依赖任何外部节点或链上数据：UTXO 集合、费率参数均通过本地接口维护并持久化到本地 JSON 文件，
全部行为可在本地复现与验证。

- 技术栈：Java 17、Spring Boot 3.4、Jackson（本地 JSON 持久化）、JUnit 5。
- 金额单位：聪（sat）；费率单位：sat/kB。

## 快速开始

```bash
mvn spring-boot:run        # 启动，默认监听 8080
mvn test                   # 运行全部测试（29 个用例）
```

## 费用模型

交易虚拟大小（`vsize`，单位 vbyte）按结构累加，**输入与输出的规模差异被计入**：

```
vsize = overheadSize + 输入数 * inputSize + 输出数 * outputSize
fee   = ceil(vsize * feeRatePerKb / 1000)      # 向上取整，保证实际费率不低于目标费率
```

默认为 Bitcoin 常见的简化 P2PKH 权重：`inputSize=148`、`outputSize=34`、`overheadSize=10`。
因此“多一个输入”比“多一个输出”更大，输入数量会真实影响费用，进而影响挑选结果。

每笔交易在组装后都会做**金额守恒校验**：

```
输入总额 = 收款金额 + 找零金额（若有） + 矿工费
```

不满足守恒直接失败，不会产生交易或占用。请求费率若超过 `wallet.max-fee-rate-per-kb`
会抛出可区分的“费率超限”错误，且不扣减、不占用任何 UTXO。

## 找零策略（粉尘下限语义）

`wallet.min-change` 配置找零下限（dust threshold，默认 546 sat）。对每个候选输入集合，
同时评估两种输出结构：

1. **带找零（2 个输出）**：若 `输入总额 - 收款金额 - 2输出费用 >= min-change`，
   生成收款输出 + 找零输出，输出顺序固定为 `[收款输出, 找零输出]`。
2. **无找零（1 个输出）**：若找零金额低于下限，则**不生成找零输出**，
   按 1 个输出重新核算规模与费用；输入总额超出收款金额的部分**全部作为矿工费支付**。
   资金并未丢失，交易仍满足目标费率且金额守恒（`输入总额 = 收款金额 + 实际矿工费`）。

这是确定性且可解释的处理：粉尘找零不产生不可花费的输出，而是以手续费形式留给矿工。

## 输入挑选策略

通过请求的 `strategy` 字段或 `wallet.default-strategy` 配置，三种策略可自由切换，
均保证金额守恒与确定性，只是排序/搜索目标不同：

| 策略 | 目标 | 行为 |
| --- | --- | --- |
| `MIN_INPUTS`（默认） | 优先减少输入数量 | 候选按金额降序（同额按 `txId`、`vout` 升序）逐个加入，最少输入覆盖目标 |
| `LONG_TERM` | 长期费用目标 | 请求未指定费率时使用更低的长期费率；优先纳入金额不超过目标的小额 UTXO（合并小额），不足再用大额补足 |
| `MIN_CHANGE` | 优先减少找零 | 在有界、确定的输入空间（≤12 个候选、评估解数有上限）内搜索最接近目标的组合，精确匹配零找零优先，无法命中时回退到降序贪心 |

**确定性保证**：所有排序都带稳定的 tie-breaker（金额 → `txId` → `vout`），
与 UTXO 集合的迭代顺序无关。相同的 UTXO 集合、目标金额与费率参数下，重复构建得到的
**选中输入、找零金额与输出顺序完全一致**，不含任何随机因素（`buildId` 使用 UUID，
仅作为构建标识，不影响任何业务结果）。

## 并发与租约占用

- `build` 成功后，选中的 UTXO 会被该构建**占用（租约锁）**，直到 `commit`、`cancel`
  或租约超时。并发构建绝不可能同时选中同一 UTXO：占用是原子的，任一冲突则整笔构建失败，
  不留中间状态。
- 当空闲 UTXO 不足以完成支付、但加入被占用 UTXO 即可完成时，返回**并发冲突**错误
  （HTTP 409），并在响应中给出冲突的 UTXO id；真正总额不足才返回“余额不足”（HTTP 422）。
- 占用带租约时长 `wallet.build-lease-millis`。构建取消、失败、持久化异常或超时后，
  占用都会被正确释放；过期提交会被拒绝且不扣减资金。

## 重启恢复

状态以单个 JSON 文件（`wallet.state-file`，默认 `data/wallet-state.json`）持久化，
写入采用“临时文件 + 原子 rename”。服务重启时：

- **已提交交易不丢失**：被花费 UTXO 保持移除状态，找零 UTXO 保持在池中；
- **未完成构建**：租约未过期的 PENDING 构建连同占用一并恢复，可继续提交或取消；
  租约已过期的占用自动释放，不会永久锁定资金。

## 错误类型

| 场景 | 异常 | HTTP 状态 |
| --- | --- | --- |
| 余额不足以覆盖金额+费用 | `InsufficientBalanceException` | 422 |
| 请求费率超过上限 | `FeeLimitExceededException` | 422 |
| 钱包没有任何 UTXO，或全部被占用 | `NoAvailableUtxoException` / `ConcurrentConflictException` | 422 / 409 |
| 最优输入被其他构建占用 | `ConcurrentConflictException`（含冲突 id） | 409 |
| 参数非法（空地址、非正金额等） | `IllegalArgumentException` | 400 |
| 构建不存在 | `NoSuchElementException` | 404 |
| 对已提交/已取消/已过期构建再操作 | `IllegalStateException` | 409 |

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/wallet/utxos` | 列出全部 UTXO（确定顺序） |
| POST | `/api/wallet/utxos` | 注入一个 UTXO：`{txId,vout,address,amount}` |
| POST | `/api/wallet/builds` | 构建未提交交易（PENDING），请求体见下 |
| GET | `/api/wallet/builds/{buildId}` | 查询构建 |
| POST | `/api/wallet/builds/{buildId}/commit` | 提交：花费输入、找零回池 |
| POST | `/api/wallet/builds/{buildId}/cancel` | 取消并释放占用 |
| GET | `/api/wallet/transactions` | 已提交交易列表 |
| GET / POST | `/api/wallet/fee-model` | 查看 / 更新费用模型 |

支付请求体：

```json
{
  "address": "recipient-address",
  "amount": 5000,
  "feeRatePerKb": 1000,
  "strategy": "MIN_INPUTS",
  "changeAddress": "own-change-address"
}
```

`feeRatePerKb`、`strategy`、`changeAddress` 均可省略；省略费率/策略时使用服务端默认值，
省略找零地址时找零（若产生）退回付款地址。

## 配置项（`application.properties` 的 `wallet.*`）

| 配置项 | 默认值 | 含义 |
| --- | --- | --- |
| `wallet.fee-rate-per-kb` | 1000 | 默认费率（sat/kB） |
| `wallet.long-term-fee-rate-per-kb` | 500 | `LONG_TERM` 策略使用的长期费率 |
| `wallet.max-fee-rate-per-kb` | 10000 | 费率上限，超过即报错 |
| `wallet.input-size` | 148 | 单个输入的规模（vbyte） |
| `wallet.output-size` | 34 | 单个输出的规模（vbyte） |
| `wallet.overhead-size` | 10 | 交易固定开销（vbyte） |
| `wallet.min-change` | 546 | 找零下限，低于则不生成找零输出 |
| `wallet.default-strategy` | MIN_INPUTS | 默认挑选策略 |
| `wallet.build-lease-millis` | 60000 | 构建占用租约时长（毫秒） |
| `wallet.state-file` | data/wallet-state.json | 本地状态文件路径 |

## 测试覆盖

- `FeeCalculatorTest`：规模差异、向上取整、输入数量影响费用。
- `CoinSelectorTest`：金额守恒、找零粉尘边界、确定性（打乱输入顺序结果不变）、
  余额不足、长期策略与减找零策略。
- `WalletServiceTest`：守恒、输出顺序、重复构建一致、粉尘找零、提交/取消、
  各类可区分错误、费率超限不产生副作用。
- `ConcurrencyTest`：20 线程并发构建，同一 UTXO 不被两笔同时选中。
- `LockLeaseTest`：租约超时自动释放、过期提交失败且不扣减。
- `RecoveryTest`：重启后已提交交易与 UTXO 状态恢复、未过期占用恢复、过期占用释放。
- `WalletControllerTest`：HTTP 全流程与 400/404/409/422 错误区分。

# Profiling

进展：

1. 基于基本块的分析 已完成
2. 按指令类型统计延迟 已完成
3. 指令流水可视化 70%
4. 计算访存总体情况可视化 40%
5. 指令延迟类型原因归纳 30%


## 1. 获取时间戳 [chisel代码](../../src/main/scala/)

### 1.1 指令流水级cycle

### 1.2 AXI访问cycle


## 2. 仿真生成row trace [C代码](../../ZirconSim/src/Emulator.cc)


### 2.1 base.log

base.log 在仿真时每次有指令提交（*cmtVlds[i] == true）时输出一行。输出字段包括：
1. 指令序号
2. 提交 PC
3. 汇编字符串
4. 上一条提交的时间戳
5. 各个流水级的发生时间
6. 当前总周期数
7. 是否为分支指令

该trace作为1个基本trace，可用于生成其他trace

### 2.2 timeline.log

`timeline.log` 记录所有外部访存（AXI）请求的起止事件。
当访存请求发出或完成时，各输出一行，标注事件类型（start/end）、事务 ID（INST DATA STREAM）、及事件发生周期。



## 3. 基于 base log 分析 [C代码](../../ZirconSim/src/Emulator.cc)


### 3.1 可视化计算和访存的总体情况 [相应脚本](../../ZirconSim/trace_timeline.py)

TODO：目前仅实现 AXI访存 可视化功能

### 3.2 指令流水可视化 [相应脚本](../../ZirconSim/trace-to-konata.py)


### 3.3 基本块 指令类型延迟 真实延迟统计[相应脚本](../../ZirconSim/trace.py)

#### Profiling Trace Analyzer 使用说明

本脚本用于对 **CPU 仿真生成的 trace（base.log）** 进行深入分析，提取指令级、基本块级与流水线级性能特征，并输出多种可视化和统计结果。

---

#### 功能概述

该脚本可以完成以下任务：

1. **解析 base.log 文件**，提取每条退休指令的执行时序。
2. **自动划分 Basic Block（基本块）**，统计迭代次数与性能特征。
3. **生成多种分析结果文件**：

   * `blkinfo`：基本块统计与优化分析
   * `blkview.json`：基本块级可视化 trace（可导入 Chrome Trace Viewer）
   * `pipeline_stage_stats.csv`：流水线各阶段耗时分析
   * `instrview.csv`：按 PC 分类的指令性能统计

**延迟：上一条指令退休了，但该指令仍未退休所耗cycles**

---

#### 使用方法

在终端运行：

```bash
python trace.py <程序名>
```

该命令会自动从：

```
profiling/<程序名>-riscv32/base.log
```

读取 trace 文件，并在对应目录下生成所有分析结果。

输出目录结构示例：

```
profiling/
 └── test-riscv32/
     ├── base.log
     ├── blkinfo
     ├── blkview.json
     ├── pipeline_stage_stats.csv
     └── instrview.csv
```

---

#### 🧠 主要功能模块

##### 1️⃣ Instruction 类

封装单条指令的信息：

* `seq`：指令序号
* `pc`：PC 值
* `asm`：汇编文本
* `start`、`latency`：执行开始与延迟周期
* `dispatch / ReadOp / Execute / writeBack / commit`：各流水级时间点
* `is_branch`：是否为分支指令
* `ipc`：每条指令的平均 IPC（动态计算）

##### 2️⃣ BasicBlock 类

表示一个基本块（Basic Block）：

* `iterations`：每次迭代包含的指令列表
* `total_cycles()`：计算总耗时
* `avg_ipc()`：计算平均 IPC
* `iteration_info()`：统计每次迭代的耗时与 IPC

##### 3️⃣ build_basic_blocks()

自动构造基本块。
算法依据：

* 第一条指令必为块起点；
* 若 `pc` 非顺序递增（非 +4），则认定为新块；
* 若前一条为分支指令，也视为新块。

---

#### 输出文件说明

##### `blkinfo`

包含程序整体统计信息与各基本块性能摘要：

示例：

```
程序的基本块数量: 25
总执行 cycles: 200000
总指令数: 60000
总体 IPC: 0.30

Block 3: 总cycles=8900, 占比=0.04, 迭代次数=12, 当前IPC=0.55
...
=== 基本块 3 ===
总耗时: 8900 cycles, 平均IPC: 0.55
迭代次数: 12
    迭代 1, 耗时=742 cycles
    迭代 2, 耗时=698 cycles
```

> 可用于定位热点基本块、分析迭代间性能差异。

---

##### `blkview.json`

以 **Chrome Trace Viewer 格式** 输出基本块执行时间段，可用于可视化。

打开方式：

1. 浏览器访问 `chrome://tracing`
2. 加载该 JSON 文件查看时间轴。

---

##### `pipeline_stage_stats.csv`

**分析指令未退休的原因：这条指令的前一条指令退休时，该指令处于哪一级**

TODO:后续要做真实延迟原因分析

输出示例：

```
Stage,PC,ASM,Count,Total_Cycles,Avg_Cycles
dispatch->readop,0x80001020,"lw x1,0(x2)",12,120.0,10.0
execute->writeback,0x80001024,"mul x3,x4,x5",8,240.0,30.0

# Stage Totals
lastCmt->dispatch_TOTAL,300.0
dispatch->readop_TOTAL,600.0
...
ALL_STAGES_TOTAL,2100.0
```

---

##### `instrview.csv`

按指令 PC 统计执行次数、平均耗时、类型分类结果：

```
pc,asm,count,total_cycles,avg_cycles
0x80001020,"lw x1,0(x2)",12,120.0,10.0
0x80001024,"mul x3,x4,x5",8,240.0,30.0

Type,count,total_cycles,avg_cycles,save_cycles
Load,12,120.0,10.0,-6.0
multiply,8,240.0,30.0,56.0
```

> 可帮助定位最耗时的指令与主要瓶颈类型。


